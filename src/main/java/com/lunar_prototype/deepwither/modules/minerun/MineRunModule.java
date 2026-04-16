package com.lunar_prototype.deepwither.modules.minerun;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ModuleRegistrar;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;

public class MineRunModule implements IModule {

    private final Deepwither plugin;
    private MineRunManager manager;
    private ModuleRegistrar registrar;

    public MineRunModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("Configuring MineRunModule...");

        this.manager = new MineRunManager(plugin);
        this.registrar = container.get(ModuleRegistrar.class);

        container.registerInstance(MineRunManager.class, this.manager);
    }

    @Override
    public void start() {
        if (manager != null) {
            manager.init();
            
            if (registrar != null) {
                registrar.registerListener(this, new MineRunListener(plugin, this.manager));
            } else {
                plugin.getLogger().warning("ModuleRegistrar not found, skipping listener registration for MineRun.");
            }
        }
    }

    @Override
    public void stop() {
        if (manager != null) {
            manager.shutdown();
        }
    }
}
