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

    public CombatModule(Deepwither plugin) {
        this.plugin = plugin;
    }

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
