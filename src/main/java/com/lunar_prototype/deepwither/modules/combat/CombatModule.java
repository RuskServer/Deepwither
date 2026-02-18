package com.lunar_prototype.deepwither.modules.combat;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.DamageManager;
import com.lunar_prototype.deepwither.core.damage.DamageProcessor;
import com.lunar_prototype.deepwither.WeaponMechanicManager;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.PlayerSettingsManager;
import com.lunar_prototype.deepwither.ChargeManager;

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

            // DamageProcessor
            DamageProcessor damageProcessor = new DamageProcessor(plugin, statManager, settingsManager);
            container.registerInstance(DamageProcessor.class, damageProcessor);

            // WeaponMechanicManager
            WeaponMechanicManager weaponMechanicManager = new WeaponMechanicManager(plugin, statManager, chargeManager,
                    settingsManager);
            container.registerInstance(WeaponMechanicManager.class, weaponMechanicManager);

            // DamageManager
            DamageManager damageManager = new DamageManager(plugin, statManager, settingsManager);
            container.registerInstance(DamageManager.class, damageManager);

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
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            plugin.getLogger().info("Initializing Combat Managers...");
            container.get(DamageProcessor.class).init();
            container.get(WeaponMechanicManager.class).init();
            container.get(DamageManager.class).init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Shuts down combat-related managers in reverse initialization order.
     *
     * Retrieves the service container and calls `shutdown()` on DamageManager, then
     * WeaponMechanicManager, and finally DamageProcessor. Any exception thrown
     * during shutdown is caught and suppressed.
     */
    @Override
    public void stop() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            container.get(DamageManager.class).shutdown();
            container.get(WeaponMechanicManager.class).shutdown();
            container.get(DamageProcessor.class).shutdown();
        } catch (Exception e) {
        }
    }
}