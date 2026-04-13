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

    /**
     * Creates a CoreModule tied to the specified plugin.
     *
     * @param plugin the Deepwither plugin used to obtain runtime services and configuration
     */
    public CoreModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers and wires the core manager instances required by the plugin into the given service container.
     *
     * @param container the ServiceContainer used to register manager instances (CacheManager, PlayerDataManager,
     *                  ItemFactory, StatManager, PlayerSettingsManager, ChargeManager, and CooldownManager)
     */
    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("CoreModule: configure()");

        // CacheManager
        CacheManager cacheManager = new CacheManager();
        container.registerInstance(CacheManager.class, cacheManager);

        // PlayerDataManager
        PlayerDataManager playerDataManager = new PlayerDataManager(plugin, cacheManager);
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

        com.lunar_prototype.deepwither.core.UIManager uiManager = new com.lunar_prototype.deepwither.core.UIManager(settingsManager);
        container.registerInstance(com.lunar_prototype.deepwither.core.UIManager.class, uiManager);

        CooldownManager cooldownManager = new CooldownManager();
        container.registerInstance(CooldownManager.class, cooldownManager);
    }

    /**
     * Initializes the core manager components from the plugin's service container.
     *
     * <p>Each registered core manager is located in the ServiceContainer and has its `init()` invoked:
     * CacheManager, PlayerDataManager, ItemFactory, StatManager, PlayerSettingsManager, ChargeManager,
     * and CooldownManager.</p>
     */
    @Override
    public void start() {
        plugin.getLogger().info("CoreModule started (Managers are initialized by DI container).");
    }

    @Override
    public void stop() {
        plugin.getLogger().info("CoreModule stopped (Managers are shutdown by DI container).");
    }
}