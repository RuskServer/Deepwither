package com.lunar_prototype.deepwither.modules.economy;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.CreditManager;
import com.lunar_prototype.deepwither.modules.economy.trader.TraderManager;
import com.lunar_prototype.deepwither.market.GlobalMarketManager;
import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.ItemFactory;

public class EconomyModule implements IModule {

    private final Deepwither plugin;
    private ServiceContainer container;

    /**
     * Create a new EconomyModule bound to the given plugin instance.
     *
     * @param plugin the main Deepwither plugin instance used for registering managers and accessing plugin services
     */
    public EconomyModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates and registers economy managers (GlobalMarketManager, CreditManager, and TraderManager)
     * in the provided service container so they can be retrieved and initialized elsewhere.
     *
     * On failure to instantiate or register any manager, a severe log message is written and the
     * exception stack trace is printed.
     *
     * @param container the service container used to obtain dependencies and register manager instances
     */
    @Override
    public void configure(ServiceContainer container) {
        this.container = container;
        plugin.getLogger().info("EconomyModule: configure()");

        try {
            DatabaseManager dbManager = container.get(DatabaseManager.class);
            ItemFactory itemFactory = container.get(ItemFactory.class);

            // GlobalMarketManager
            GlobalMarketManager globalMarketManager = new GlobalMarketManager(plugin, dbManager);
            container.registerInstance(GlobalMarketManager.class, globalMarketManager);

            // CreditManager
            CreditManager creditManager = new CreditManager(plugin);
            container.registerInstance(CreditManager.class, creditManager);

            // TraderManager
            TraderManager traderManager = new TraderManager(plugin, itemFactory);
            container.registerInstance(TraderManager.class, traderManager);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to configure EconomyModule components.");
            e.printStackTrace();
        }
    }

    /**
     * Initializes the economy managers and starts their runtime behavior.
     *
     * Logs an informational message and calls `init()` on the GlobalMarketManager, CreditManager,
     * and TraderManager instances retrieved from the service container.
     * Any exception thrown during initialization is caught and its stack trace is printed.
     */
    @Override
    public void start() {
        try {
            plugin.getLogger().info("Initializing Economy Managers...");
            container.get(GlobalMarketManager.class).init();
            container.get(CreditManager.class).init();
            container.get(TraderManager.class).init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Shuts down economy-related managers obtained from the service container.
     *
     * Invokes `shutdown()` on TraderManager, CreditManager, and GlobalMarketManager. Any exceptions thrown during shutdown are caught and their stack traces are printed.
     */
    @Override
    public void stop() {
        try {
            container.get(TraderManager.class).shutdown();
            container.get(CreditManager.class).shutdown();
            container.get(GlobalMarketManager.class).shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}