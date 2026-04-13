package com.lunar_prototype.deepwither.modules.outpost;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ModuleRegistrar;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;

public class OutpostModule implements IModule {

    private final Deepwither plugin;

    public OutpostModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("OutpostModule: configure()");

        OutpostConfig config = new OutpostConfig(plugin);
        container.registerInstance(OutpostConfig.class, config);

        OutpostManager manager = new OutpostManager(plugin, config);
        container.registerInstance(OutpostManager.class, manager);

        container.registerInstance(OutpostDamageListener.class, new OutpostDamageListener(manager));
        container.registerInstance(OutpostRegionListener.class, new OutpostRegionListener(manager));
    }

    @Override
    public void start() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        ModuleRegistrar registrar = container.get(ModuleRegistrar.class);

        try {
            plugin.getLogger().info("Starting Outpost Module (Listeners)...");

            // Register Listeners
            registrar.registerListener(container.get(OutpostDamageListener.class));
            registrar.registerListener(container.get(OutpostRegionListener.class));

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start Outpost Module.");
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        plugin.getLogger().info("Stopping Outpost Module...");
    }
}
