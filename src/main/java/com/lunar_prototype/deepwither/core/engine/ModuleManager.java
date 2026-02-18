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

    public ModuleManager(ServiceContainer container, Logger logger) {
        this.container = container;
        this.logger = logger;
        // 自身もコンテナに登録
        container.registerInstance(ModuleManager.class, this);
    }

    /**
     * モジュールを登録します。
     * 登録順序が初期化順序（configure順）になりますが、
     * 実際の依存解決はServiceContainerが行います。
     * 
     * @param module モジュールインスタンス
     */
    public void registerModule(IModule module) {
        modules.add(module);
        logger.info("Registered module: " + module.getClass().getSimpleName());
    }

    /**
     * 全モジュールの configure() を呼び出します。
     * このフェーズで各モジュールは自身のクラスをコンテナに登録します。
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
     * 全モジュールの start() を呼び出します。
     * インスタンス化とstart処理が行われます。
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
     * 全モジュールの stop() を逆順で呼び出します。
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
