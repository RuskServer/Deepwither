package com.lunar_prototype.deepwither.core.engine;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 軽量なDependency Injection (DI) コンテナ。
 * 
 * <p>
 * 特徴:
 * </p>
 * <ul>
 * <li>コンストラクタインジェクションのみをサポート</li>
 * <li>シングルトンインスタンス管理</li>
 * <li>IManagerインターフェースによるライフサイクル管理の統合</li>
 * <li>@DependsOnアノテーションによる初期化順序の制御</li>
 * </ul>
 */
public class ServiceContainer {

    private final Map<Class<?>, Object> instances = new HashMap<>();
    private final Map<Class<?>, Object> registeredInstances = new HashMap<>();
    private final Set<Class<?>> currentlyCreating = new HashSet<>();
    private final Logger logger;

    // ライフサイクル管理
    private final List<IManager> orderedLifecycleManaged = new ArrayList<>();
    private boolean initialized = false;

    /**
     * 依存関係グラフのノード
     */
    private static class DependencyNode {
        final Class<?> clazz;
        final Set<Class<?>> dependencies = new LinkedHashSet<>();
        
        DependencyNode(Class<?> clazz) {
            this.clazz = clazz;
        }
    }

    public ServiceContainer(Logger logger) {
        this.logger = logger;
        registerInstance(ServiceContainer.class, this);
    }

    public boolean has(Class<?> clazz) {
        return instances.containsKey(clazz) || registeredInstances.containsKey(clazz);
    }

    public <T> void registerInstance(Class<T> clazz, T instance) {
        if (instances.containsKey(clazz) || registeredInstances.containsKey(clazz)) {
            logger.warning("Overwriting existing registration for: " + clazz.getName());
        }
        registeredInstances.put(clazz, instance);
        instances.put(clazz, instance);
    }

    public <T> T get(Class<T> clazz) {
        if (instances.containsKey(clazz)) {
            return clazz.cast(instances.get(clazz));
        }
        if (registeredInstances.containsKey(clazz)) {
            return clazz.cast(registeredInstances.get(clazz));
        }
        return createInstance(clazz);
    }

    private <T> T createInstance(Class<T> clazz) {
        if (currentlyCreating.contains(clazz)) {
            throw new IllegalStateException("Circular dependency detected: " + clazz.getName());
        }

        currentlyCreating.add(clazz);
        try {
            Constructor<?>[] constructors = clazz.getConstructors();
            if (constructors.length == 0) {
                throw new IllegalArgumentException("No public constructor found for: " + clazz.getName());
            }

            Constructor<?> targetConstructor = constructors[0];
            for (Constructor<?> c : constructors) {
                if (c.getParameterCount() > targetConstructor.getParameterCount()) {
                    targetConstructor = c;
                }
            }

            Class<?>[] parameterTypes = targetConstructor.getParameterTypes();
            Object[] parameters = new Object[parameterTypes.length];

            for (int i = 0; i < parameterTypes.length; i++) {
                parameters[i] = get(parameterTypes[i]);
            }

            T instance = clazz.cast(targetConstructor.newInstance(parameters));
            instances.put(clazz, instance);
            return instance;

        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate: " + clazz.getName(), e);
        } finally {
            currentlyCreating.remove(clazz);
        }
    }

    /**
     * 登録された全 IManager を依存関係順に初期化します。
     */
    public void initializeAll() throws Exception {
        if (initialized) return;
        
        logger.info("--- [DI] Building Dependency Graph ---");
        resolveLifecycleOrder();
        
        logger.info("--- [DI] Initialization Sequence ---");
        for (int i = 0; i < orderedLifecycleManaged.size(); i++) {
            IManager manager = orderedLifecycleManaged.get(i);
            logger.info(String.format("  [%d] %s", (i + 1), manager.getClass().getSimpleName()));
        }

        for (IManager manager : orderedLifecycleManaged) {
            manager.init();
        }
        initialized = true;
        logger.info("--- [DI] All Services Initialized ---");
    }

    /**
     * 初期化の逆順で全 IManager を停止します。
     */
    public void shutdownAll() {
        logger.info("--- [DI] Starting Shutdown Sequence ---");
        List<IManager> reverseOrder = new ArrayList<>(orderedLifecycleManaged);
        Collections.reverse(reverseOrder);

        for (IManager manager : reverseOrder) {
            try {
                logger.info("[DI] Shutting down: " + manager.getClass().getSimpleName());
                manager.shutdown();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during shutdown of: " + manager.getClass().getSimpleName(), e);
            }
        }
        orderedLifecycleManaged.clear();
        initialized = false;
        logger.info("--- [DI] All Services Stopped ---");
    }

    private void resolveLifecycleOrder() {
        orderedLifecycleManaged.clear();
        
        // 1. ノードの構築
        Map<Class<?>, DependencyNode> nodes = new HashMap<>();
        Map<Class<?>, IManager> managerInstances = new HashMap<>();

        for (Map.Entry<Class<?>, Object> entry : instances.entrySet()) {
            if (entry.getValue() instanceof IManager) {
                Class<?> clazz = entry.getKey();
                managerInstances.put(clazz, (IManager) entry.getValue());
                nodes.put(clazz, buildNode(clazz));
            }
        }

        // 2. グラフの解決 (DFSによるトポロジカルソート)
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> path = new LinkedHashSet<>(); // 順序を保持してサイクル表示に使う

        for (Class<?> clazz : nodes.keySet()) {
            visit(clazz, nodes, managerInstances, visited, path);
        }
    }

    private DependencyNode buildNode(Class<?> clazz) {
        DependencyNode node = new DependencyNode(clazz);

        // A. コンストラクタ引数からの自動抽出
        for (Constructor<?> c : clazz.getConstructors()) {
            for (Class<?> paramType : c.getParameterTypes()) {
                if (IManager.class.isAssignableFrom(paramType)) {
                    node.dependencies.add(paramType);
                }
            }
        }

        // B. @DependsOn による明示的抽出 (非コンストラクタ依存用)
        DependsOn dependsOn = clazz.getAnnotation(DependsOn.class);
        if (dependsOn != null) {
            for (Class<?> depClass : dependsOn.value()) {
                node.dependencies.add(depClass);
            }
        }

        return node;
    }

    private void visit(Class<?> clazz, Map<Class<?>, DependencyNode> nodes, 
                       Map<Class<?>, IManager> managerInstances, 
                       Set<Class<?>> visited, Set<Class<?>> path) {
        
        if (path.contains(clazz)) {
            List<String> cyclePath = path.stream().map(Class::getSimpleName).toList();
            throw new IllegalStateException("Circular dependency detected in lifecycle: " 
                + String.join(" -> ", cyclePath) + " -> " + clazz.getSimpleName());
        }
        
        if (visited.contains(clazz)) return;

        path.add(clazz);
        DependencyNode node = nodes.get(clazz);
        if (node != null) {
            for (Class<?> dep : node.dependencies) {
                // コンテナに存在する依存先のみを探索対象とする
                if (managerInstances.containsKey(dep)) {
                    visit(dep, nodes, managerInstances, visited, path);
                }
            }
        }

        path.remove(clazz);
        visited.add(clazz);
        
        IManager instance = managerInstances.get(clazz);
        if (instance != null) {
            orderedLifecycleManaged.add(instance);
        }
    }

    public void clear() {
        instances.clear();
        registeredInstances.clear();
        currentlyCreating.clear();
        orderedLifecycleManaged.clear();
        initialized = false;
    }
}
