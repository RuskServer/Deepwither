package com.lunar_prototype.deepwither.modules.integration;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.modules.integration.service.IMobService;
import com.lunar_prototype.deepwither.modules.integration.service.MythicMobService;
import com.lunar_prototype.deepwither.modules.integration.service.VanillaMobService;
import org.bukkit.Bukkit;

public class IntegrationModule implements IModule {

    private final Deepwither plugin;

    /**
     * Creates a new IntegrationModule tied to the given Deepwither plugin instance.
     *
     * @param plugin the main Deepwither plugin instance used by this module
     */
    public IntegrationModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers an IMobService implementation into the provided service container based on whether the MythicMobs plugin is enabled.
     *
     * If MythicMobs is present, a MythicMobService instance is registered; otherwise a VanillaMobService instance is registered.
     *
     * @param container the ServiceContainer used to register the selected IMobService implementation
     */
    @Override
    public void configure(ServiceContainer container) {
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            container.registerInstance(IMobService.class, new MythicMobService());
        } else {
            container.registerInstance(IMobService.class, new VanillaMobService());
        }
    }

    /**
     * Performs startup actions for this module.
     *
     * <p>Invoked when the module is started; implementations should perform any initialization
     * required (for example, allocating resources or registering listeners).</p>
     */
    @Override
    public void start() {
    }

    /**
     * Stop the module and perform any shutdown or cleanup work for integration resources.
     *
     * <p>Currently this implementation performs no actions.
     */
    @Override
    public void stop() {
    }
}