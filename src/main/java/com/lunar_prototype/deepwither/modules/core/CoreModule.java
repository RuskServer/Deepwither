package com.lunar_prototype.deepwither.modules.core;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.CacheManager;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.core.playerdata.PlayerDataManager;

import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.PlayerSettingsManager;
import com.lunar_prototype.deepwither.ChargeManager;
import com.lunar_prototype.deepwither.CooldownManager;
import com.lunar_prototype.deepwither.util.IManager;

public class CoreModule implements IModule {

    private final Deepwither plugin;

    public CoreModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("CoreModule: configure()");

        // CacheManager
        CacheManager cacheManager = new CacheManager();
        container.registerInstance(CacheManager.class, cacheManager);

        // PlayerDataManager
        PlayerDataManager playerDataManager = new PlayerDataManager(plugin);
        container.registerInstance(PlayerDataManager.class, playerDataManager);

        // Dependencies for other modules
        ItemFactory itemFactory = new ItemFactory(plugin);
        container.registerInstance(ItemFactory.class, itemFactory);

        StatManager statManager = new StatManager();
        container.registerInstance(StatManager.class, statManager);

        PlayerSettingsManager settingsManager = new PlayerSettingsManager(plugin);
        container.registerInstance(PlayerSettingsManager.class, settingsManager);

        ChargeManager chargeManager = new ChargeManager(plugin);
        container.registerInstance(ChargeManager.class, chargeManager);

        CooldownManager cooldownManager = new CooldownManager();
        container.registerInstance(CooldownManager.class, cooldownManager);
    }

    @Override
    public void start() {
        ServiceContainer container = plugin.getBootstrap().getContainer();

        plugin.getLogger().info("Initializing Core Managers...");
        initSafely(container, CacheManager.class);
        initSafely(container, PlayerDataManager.class);
        initSafely(container, ItemFactory.class);
        initSafely(container, StatManager.class);
        initSafely(container, PlayerSettingsManager.class);
        initSafely(container, ChargeManager.class);
        initSafely(container, CooldownManager.class);
    }

    @Override
    public void stop() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            container.get(CooldownManager.class).shutdown();
            container.get(ChargeManager.class).shutdown();
            container.get(PlayerSettingsManager.class).shutdown();
            container.get(StatManager.class).shutdown();
            container.get(ItemFactory.class).shutdown();
            container.get(PlayerDataManager.class).shutdown();
            container.get(CacheManager.class).shutdown();
        } catch (Exception e) {
        }
    }

    private <T extends IManager> void initSafely(ServiceContainer container, Class<T> clazz) {
        try {
            container.get(clazz).init();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize " + clazz.getSimpleName());
            e.printStackTrace();
        }
    }
}
