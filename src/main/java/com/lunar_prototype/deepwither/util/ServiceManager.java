package com.lunar_prototype.deepwither.util;

import com.lunar_prototype.deepwither.Deepwither;

import java.util.*;
import java.util.logging.Level;

/**
 * サービスの登録、依存関係の解決、ライフサイクル管理を行うクラス。
 */
public class ServiceManager {

    private final Deepwither plugin;
    private final Map<Class<? extends IManager>, IManager> services = new HashMap<>();
    private final List<IManager> orderedServices = new ArrayList<>();
    private final com.lunar_prototype.deepwither.core.engine.ServiceContainer container; // [NEW] Bridge

    public ServiceManager(Deepwither plugin) {
        this(plugin, null);
    }

    // [NEW] Constructor with container
    public ServiceManager(Deepwither plugin, com.lunar_prototype.deepwither.core.engine.ServiceContainer container) {
        this.plugin = plugin;
        this.container = container;
    }

    /**
     * サービス（マネージャー）を登録します。
     * 自動的に実装しているインターフェースでも引けるようにインデックスされます。
     */
    @SuppressWarnings("unchecked")
    public <T extends IManager> void register(T service) {
        Class<? extends IManager> clazz = (Class<? extends IManager>) service.getClass();
        services.put(clazz, service);

        // 実装しているすべてのインターフェースでも引けるようにする
        for (Class<?> iface : clazz.getInterfaces()) {
            if (IManager.class.isAssignableFrom(iface) && iface != IManager.class) {
                services.putIfAbsent((Class<? extends IManager>) iface, service);
            }
        }

        // [Opt] Containerにも登録しておくと、Module側からLegacy Managerを参照できる
        if (container != null) {
            container.registerInstance((Class) clazz, service);
        }
    }

    /**
     * 登録されたサービスを取得します（インターフェース指定可）。
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz) {
        IManager service = services.get(clazz);

        // [NEW] Fallback to Container
        if (service == null && container != null) {
            try {
                return container.get(clazz);
            } catch (Exception e) {
                // Containerにもなければ例外へ
            }
        }

        if (service == null) {
            throw new IllegalArgumentException("Service not found: " + clazz.getName());
        }
        return (T) service;
    }

    /**
     * すべてのサービスの依存関係を解決し、トポロジカルソート順で初期化します。
     *
     * @throws Exception 循環参照や初期化エラーが発生した場合
     */
    public void startAll() throws Exception {
        resolveDependencies();

        for (IManager service : orderedServices) {
            try {
                plugin.getLogger().info("Initializing service: " + service.getClass().getSimpleName());
                service.init();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to initialize service: " + service.getClass().getSimpleName(), e);
                throw e;
            }
        }
    }

    /**
     * すべてのサービスを逆順で停止します。
     */
    public void stopAll() {
        // 初期化順の逆順で停止
        List<IManager> reverseOrder = new ArrayList<>(orderedServices);
        Collections.reverse(reverseOrder);

        for (IManager service : reverseOrder) {
            try {
                plugin.getLogger().info("Shutting down service: " + service.getClass().getSimpleName());
                service.shutdown();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Error during shutdown of service: " + service.getClass().getSimpleName(), e);
            }
        }
        services.clear();
        orderedServices.clear();
    }

    /**
     * トポロジカルソートを実行し、初期化順序を決定します。
     */
    private void resolveDependencies() {
        orderedServices.clear();
        Set<Class<? extends IManager>> visited = new HashSet<>();
        Set<Class<? extends IManager>> path = new HashSet<>();

        for (Class<? extends IManager> serviceClass : services.keySet()) {
            if (!visited.contains(serviceClass)) {
                visit(serviceClass, visited, path);
            }
        }
    }

    private void visit(Class<? extends IManager> serviceClass, Set<Class<? extends IManager>> visited,
            Set<Class<? extends IManager>> path) {
        if (path.contains(serviceClass)) {
            throw new IllegalStateException("Circular dependency detected involving: " + serviceClass.getName());
        }

        if (visited.contains(serviceClass)) {
            return;
        }

        path.add(serviceClass);

        DependsOn dependencyAnnotation = serviceClass.getAnnotation(DependsOn.class);
        if (dependencyAnnotation != null) {
            for (Class<? extends IManager> depClass : dependencyAnnotation.value()) {
                // 依存先が登録されているか確認（登録されていない依存先は無視するかエラーにするか。ここではエラーにする）
                if (!services.containsKey(depClass)) {
                    // Check if it exists in the Container
                    if (container != null) {
                        if (container.has(depClass)) {
                            continue; // Dependency is managed by Container (already init), skip.
                        }
                    }

                    throw new IllegalStateException("Service " + serviceClass.getName() + " depends on "
                            + depClass.getName() + " which is not registered.");
                }
                visit(depClass, visited, path);
            }
        }

        path.remove(serviceClass);
        visited.add(serviceClass);
        orderedServices.add(services.get(serviceClass));
    }
}
