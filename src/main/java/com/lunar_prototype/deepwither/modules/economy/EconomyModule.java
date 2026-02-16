package com.lunar_prototype.deepwither.modules.economy;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.CreditManager;
import com.lunar_prototype.deepwither.TraderManager;
import com.lunar_prototype.deepwither.market.GlobalMarketManager;
import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.ItemFactory;

public class EconomyModule implements IModule {

    private final Deepwither plugin;

    public EconomyModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
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

            // TraderQuestManager
            com.lunar_prototype.deepwither.TraderQuestManager traderQuestManager = new com.lunar_prototype.deepwither.TraderQuestManager(
                    plugin, dbManager);
            container.registerInstance(com.lunar_prototype.deepwither.TraderQuestManager.class, traderQuestManager);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to configure EconomyModule components.");
            e.printStackTrace();
        }
    }

    @Override
    public void start() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            plugin.getLogger().info("Initializing Economy Managers...");
            container.get(GlobalMarketManager.class).init();
            container.get(CreditManager.class).init();
            container.get(TraderManager.class).init();
            container.get(com.lunar_prototype.deepwither.TraderQuestManager.class).init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            container.get(TraderManager.class).shutdown();
            container.get(CreditManager.class).shutdown();
            container.get(GlobalMarketManager.class).shutdown();
        } catch (Exception e) {
        }
    }
}
