package com.lunar_prototype.deepwither.modules.combat;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DeepwitherPartyAPI;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.DamageManager;
import com.lunar_prototype.deepwither.core.damage.DamageProcessor;
import com.lunar_prototype.deepwither.WeaponMechanicManager;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.PlayerSettingsManager;
import com.lunar_prototype.deepwither.ChargeManager;
import com.lunar_prototype.deepwither.SpecialItemEffectManager;

public class CombatModule implements IModule {

    private final Deepwither plugin;

    /**
     * Create a CombatModule tied to the main plugin instance.
     *
     * Stores the provided Deepwither instance for use when configuring, starting,
     * and stopping the module (accessing bootstrap, container, and logging).
     *
     * @param plugin the main plugin instance used to access the bootstrap, service container, and logging
     */
    public CombatModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    /**
     * Instantiates and registers combat-related managers and processors in the provided service container.
     *
     * Retrieves required dependencies (StatManager, PlayerSettingsManager, ChargeManager) from the container,
     * constructs DamageProcessor, WeaponMechanicManager, and DamageManager using those dependencies and the plugin,
     * and registers each instance back into the container.
     *
     * If dependency retrieval or instantiation fails, a severe error is logged and the exception stack trace is printed.
     *
     * @param container the ServiceContainer used to retrieve dependencies and register the created components
     */
    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("CombatModule: configure()");

        try {
            // Retrieve dependencies from container (registered in CoreModule)
            // Note: registerInstance doesn't support dependency injection automatically
            // unless we use a factory or provider.
            // But here we need to instantiate manually using other instances from the
            // container.
            // Since CoreModule runs before CombatModule, these should be available.

            StatManager statManager = container.get(StatManager.class);
            PlayerSettingsManager settingsManager = container.get(PlayerSettingsManager.class);
            ChargeManager chargeManager = container.get(ChargeManager.class);
            com.lunar_prototype.deepwither.core.UIManager uiManager = container.get(com.lunar_prototype.deepwither.core.UIManager.class);
            DeepwitherPartyAPI partyAPI = container.get(DeepwitherPartyAPI.class);

            // DamageProcessor
            DamageProcessor damageProcessor = new DamageProcessor(plugin, statManager, uiManager, partyAPI);
            container.registerInstance(DamageProcessor.class, damageProcessor);

            // WeaponMechanicManager
            WeaponMechanicManager weaponMechanicManager = new WeaponMechanicManager(plugin, statManager, chargeManager,
                    settingsManager, uiManager);
            container.registerInstance(WeaponMechanicManager.class, weaponMechanicManager);

            // DamageManager
            DamageManager damageManager = new DamageManager(plugin, statManager, settingsManager, uiManager);
            container.registerInstance(DamageManager.class, damageManager);

            // SpecialItemEffectManager
            SpecialItemEffectManager specialItemEffectManager = new SpecialItemEffectManager(plugin);
            container.registerInstance(SpecialItemEffectManager.class, specialItemEffectManager);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to configure CombatModule components. Dependencies might be missing.");
            e.printStackTrace();
        }
    }

    /**
     * Initializes combat-related managers retrieved from the service container in the order:
     * DamageProcessor, WeaponMechanicManager, then DamageManager.
     *
     * Any exceptions thrown during initialization are caught and printed to standard error.
     */
    @Override
    public void start() {
        plugin.getLogger().info("Starting CombatModule (Managers are initialized by DI container)...");
    }

    @Override
    public void stop() {
        plugin.getLogger().info("Stopping CombatModule...");
    }
}