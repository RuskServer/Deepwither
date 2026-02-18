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
        // Module固有のstart処理
        // DatabaseManager.init() は従来 ServiceManager.startAll() で呼ばれていた。
        // 新体制ではここで呼ぶべきか？

        // 移行期:
        // ServiceManager は "IManager" を実装しているものを startAll で init() する。
        // しかし、ServiceManager.services に入っていないと startAll 対象にならない。

        // 案1: InfrastructureModule.start() で dbManager.init() を呼ぶ。
        // 案2: ServiceManager が Container 内の IManager も探して init() する。(複雑)

        // 案1を採用。
        ServiceContainer container = plugin.getBootstrap().getContainer();
        DatabaseManager dbManager = container.get(DatabaseManager.class);
        try {
            plugin.getLogger().info("Initializing DatabaseManager from InfrastructureModule...");
            dbManager.init();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize DatabaseManager");
            e.printStackTrace();
        }
    }

    /**
     * Shuts down the DatabaseManager obtained from the module's service container, if present.
     *
     * <p>Retrieves the ServiceContainer from the plugin bootstrap, fetches the DatabaseManager
     * instance and invokes its {@code shutdown()} method. Any exceptions thrown during retrieval
     * or shutdown are caught and suppressed (e.g., if the manager is already disposed).</p>
     */
    @Override
    public void stop() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        if (container != null) {
            try {
                DatabaseManager dbManager = container.get(DatabaseManager.class);
                if (dbManager != null) {
                    dbManager.shutdown();
                }
            } catch (Exception e) {
                // 無視 (既に破棄されている場合など)
            }
        }
    }
}