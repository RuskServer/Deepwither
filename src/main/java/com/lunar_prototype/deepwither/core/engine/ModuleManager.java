package com.lunar_prototype.deepwither.core.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * モジュールの登録とライフサイクルを管理するクラス。
 */
public class ModuleManager {

    private final ServiceContainer container;
    private final Logger logger;
    private final List<IModule> modules = new ArrayList<>();
    private final Set<IModule> failedModules = new HashSet<>();

    /**
     * Creates a ModuleManager, stores the provided ServiceContainer and Logger, and registers this
     * ModuleManager instance in the given container.
     *
     * @param container the ServiceContainer used for dependency registration and access; this
     *                  ModuleManager will be registered into it
     * @param logger    the Logger used for recording lifecycle events and errors
     */
    public ModuleManager(ServiceContainer container, Logger logger) {
        this.container = container;
        this.logger = logger;
        // 自身もコンテナに登録
        container.registerInstance(ModuleManager.class, this);
    }

    /**
     * Registers a module for lifecycle management.
     *
     * Registration order determines the order modules are configured and started;
     * actual dependency resolution is performed by the ServiceContainer.
     *
     * @param module the module to register; it will be configured, started, and stopped following registration order
     */
    public void registerModule(IModule module) {
        modules.add(module);
        logger.info("Registered module: " + module.getClass().getSimpleName());
    }

    /**
     * Calls each registered module's configure method so modules can register their services in the container.
     *
     * Modules that throw an exception during configuration are recorded as failed; a severe log entry is made and the exception's stack trace is printed.
     */
    public void configureModules() {
        logger.info("Configuring modules...");
        for (IModule module : modules) {
            try {
                module.configure(container);
            } catch (Exception e) {
                logger.severe("Failed to configure module: " + module.getClass().getSimpleName());
                e.printStackTrace();
                failedModules.add(module);
            }
        }
    }

    /**
     * Starts all registered modules in registration order.
     *
     * Invokes each module's {@code start()} method; modules present in the manager's failedModules set are skipped.
     * Exceptions thrown by an individual module are caught so remaining modules continue to be started; such failures are recorded via error logging and the module's stack trace is printed.
     */
    public void startModules() {
        logger.info("Starting modules...");
        for (IModule module : modules) {
            if (failedModules.contains(module)) {
                logger.warning("Skipping failed module: " + module.getClass().getSimpleName());
                continue;
            }
            try {
                logger.info("Starting module: " + module.getClass().getSimpleName());
                module.start();
            } catch (Exception e) {
                logger.severe("Failed to start module: " + module.getClass().getSimpleName());
                e.printStackTrace();
            }
        }
    }

    /**
     * Stops all registered modules in reverse registration order.
     *
     * Each module's {@code stop()} is invoked; exceptions thrown by a module are caught and do not prevent
     * remaining modules from being stopped. After all modules have been processed, the internal modules list
     * is cleared and the service container is cleared.
     */
    public void stopModules() {
        logger.info("Stopping modules...");
        // 逆順で停止
        for (int i = modules.size() - 1; i >= 0; i--) {
            IModule module = modules.get(i);
            try {
                logger.info("Stopping module: " + module.getClass().getSimpleName());
                module.stop();
            } catch (Exception e) {
                logger.severe("Failed to stop module: " + module.getClass().getSimpleName());
                e.printStackTrace();
            }
        }
        modules.clear();
        container.clear();
    }
}