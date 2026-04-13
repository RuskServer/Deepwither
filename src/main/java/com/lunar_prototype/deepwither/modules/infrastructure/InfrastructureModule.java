package com.lunar_prototype.deepwither.modules.infrastructure;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;

public class InfrastructureModule implements IModule {

    private final Deepwither plugin;

    /**
     * Creates a new InfrastructureModule tied to the given plugin instance.
     *
     * @param plugin the Deepwither plugin used to access logging and bootstrap/service container facilities
     */
    public InfrastructureModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    /**
     * Register infrastructure services required by this module into the provided service container.
     *
     * Specifically, creates and registers a DatabaseManager instance for later retrieval.
     *
     * @param container the service container where module services will be registered
     * @throws RuntimeException if the DatabaseManager cannot be created or registered
     */
    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("InfrastructureModule: configure()");

        // DatabaseManagerの登録
        // new DatabaseManager(plugin) は依存関係が単純なのでここで生成
        // init() は IManager のライフサイクルだが、Moduleのstart()で呼ぶか、
        // ServiceManager(Legacy)との兼ね合いをどうするか。
        // -> DatabaseManagerはIManagerを実装している。
        // -> 新アーキテクチャでは IManager.init() は Module.start() 内で呼ぶか、
        // LegacyModule/ServiceManager に任せるか。

        // 今回は "登録の移動" なので、インスタンスを生成してコンテナに登録する。
        // 初期化(init)は、ServiceManagerのFallback機能を使って呼び出されるか、
        // ここで明示的に呼ぶ必要がある。

        try {
            DatabaseManager dbManager = new DatabaseManager(plugin);
            container.registerInstance(DatabaseManager.class, dbManager);

            // 重要: DatabaseManagerはDeepwitherフィールドにも保持されている
            // (Deepwither.getDatabaseManager()のため)
            // リフレクション等でセットするか、Deepwitherにセッターを作るか。
            // -> Deepwither.java の setupManagers から削除するので、
            // Deepwither.databaseManager フィールドは null になる可能性がある。

            // Deepwither.java 側で getDatabaseManager() が serviceManager.get() を使うなら問題ない。
            // 既存コード: return (IDatabaseManager) serviceManager.get(DatabaseManager.class);
            // なので、ServiceManagerがコンテナから引ければOK。

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create DatabaseManager");
            throw new RuntimeException("Failed to create DatabaseManager", e);
        }
    }

    /**
     * Starts the infrastructure module by initializing the DatabaseManager retrieved from the service container.
     *
     * If the DatabaseManager cannot be initialized, the method logs a severe error and suppresses the exception.
     */
    @Override
    public void start() {
        plugin.getLogger().info("Starting InfrastructureModule (Managers are initialized by DI container)...");
        plugin.getServer().getPluginManager().registerEvents(new LegacyNPCCleanupListener(plugin), plugin);
    }

    @Override
    public void stop() {
        plugin.getLogger().info("Stopping InfrastructureModule...");
    }
}