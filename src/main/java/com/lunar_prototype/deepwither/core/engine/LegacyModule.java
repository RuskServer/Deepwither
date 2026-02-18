package com.lunar_prototype.deepwither.core.engine;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.ServiceManager;

/**
 * 既存のServiceManagerを新アーキテクチャに適合させるためのアダプターモジュール。
 * これにより、既存のコードを変更せずに段階的な移行が可能になる。
 */
public class LegacyModule implements IModule {

    private final Deepwither plugin;
    private ServiceManager serviceManager;

    public LegacyModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("LegacyModule: configure() called.");
        try {
            // ServiceManagerを生成し、Deepwitherにセット
            this.serviceManager = new ServiceManager(plugin, container);
            container.registerInstance(ServiceManager.class, this.serviceManager);
            plugin.setServiceManager(this.serviceManager);
            // Managerの登録 (Deepwither側のメソッドを呼び出し)
            plugin.getLogger().info("LegacyModule: calling setupManagers()...");
            plugin.setupManagers();
            plugin.getLogger().info("LegacyModule: ServiceManager set to Deepwither.");
        } catch (Exception e) {
            plugin.getLogger().severe("LegacyModule: configure() failed! ServiceManager will be reset.");
            this.serviceManager = null;
            throw e;
        }
    }

    @Override
    public void start() {
        plugin.getLogger().info("Starting Legacy Module (ServiceManager)...");
        try {
            if (serviceManager == null) {
                plugin.getLogger()
                        .severe("LegacyModule: serviceManager is null in start()! configure() might have failed.");
            }

            // 一括初期化
            plugin.getLogger().info("LegacyModule: calling serviceManager.startAll()...");
            serviceManager.startAll();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start Legacy Module!");
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        plugin.getLogger().info("Stopping Legacy Module (ServiceManager)...");
        if (serviceManager != null) {
            serviceManager.stopAll();
        }
    }
}
