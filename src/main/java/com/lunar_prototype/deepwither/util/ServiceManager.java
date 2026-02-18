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
    private final com.lunar_prototype.deepwither.core.engine.ServiceContainer container; /**
     * Creates a ServiceManager that does not use an external ServiceContainer.
     *
     * @param plugin the Deepwither plugin instance used by the manager
     */

    public ServiceManager(Deepwither plugin) {
        this(plugin, null);
    }

    /**
     * Create a ServiceManager backed by the given plugin and an optional service container bridge.
     *
     * @param plugin    the Deepwither plugin instance used by this manager
     * @param container an optional ServiceContainer to register and resolve services from; may be {@code null}
     */
    public ServiceManager(Deepwither plugin, com.lunar_prototype.deepwither.core.engine.ServiceContainer container) {
        this.plugin = plugin;
        this.container = container;
    }

    /**
     * Register a manager instance and index it by its concrete class and any implemented IManager interfaces.
     *
     * Registers the given service under its concrete class key and, for each implemented interface that
     * extends IManager (excluding IManager itself), registers the same instance under that interface if not
     * already present. If a ServiceContainer bridge was provided to this ServiceManager, the instance is also
     * registered in the container under its concrete class.
     *
     * @param service the manager instance to register
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
     * Retrieve a registered service by its concrete class or an implemented interface.
     *
     * @param <T>   the expected service type
     * @param clazz the service class or interface to look up
     * @return the registered service instance cast to the requested type
     * @throws IllegalArgumentException if no service is registered for the given class and no external container provides it
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
     * Resolves dependencies for all registered services and initializes them in topological order.
     *
     * @throws Exception if dependency resolution detects a circular dependency or if any service fails during initialization
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
     * Stops every registered service in the reverse order of initialization.
     *
     * Each service's shutdown method is invoked; failures during a service shutdown
     * are caught and logged, and shutdown proceeds for remaining services. After
     * shutdown, all registered service references and the initialization order are cleared.
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
     * Compute and populate the service initialization order by performing a topological sort
     * over the currently registered manager classes.
     *
     * This fills {@code orderedServices} with service instances in dependency-resolved
     * initialization order.
     *
     * @throws IllegalStateException if a circular dependency or a missing dependency is detected
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

    /**
     * Visits a service class and its declared dependencies to determine initialization order.
     *
     * @param serviceClass the service class to visit
     * @param visited a set of service classes that have already been processed
     * @param path the current recursion path used to detect circular dependencies
     * @throws IllegalStateException if a circular dependency is detected or if a declared dependency
     *         is not registered locally and is not available from the container
     */
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