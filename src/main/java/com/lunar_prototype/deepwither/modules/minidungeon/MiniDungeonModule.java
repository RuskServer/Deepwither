package com.lunar_prototype.deepwither.modules.minidungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import com.lunar_prototype.deepwither.core.engine.ModuleRegistrar;

public class MiniDungeonModule implements IModule {

    private final Deepwither plugin;
    private MiniDungeonManager manager;
    private ModuleRegistrar registrar;

    public MiniDungeonModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("Configuring MiniDungeonModule...");

        this.manager = new MiniDungeonManager(plugin);
        this.registrar = container.get(ModuleRegistrar.class);
        
        // Register container
        container.registerInstance(MiniDungeonManager.class, manager);
    }

    @Override
    public void start() {
        if (manager != null) {
            manager.init();

            if (registrar != null) {
                registrar.registerListener(this, new MiniDungeonListener(plugin, manager));
                MiniDungeonCommand cmdExecutor = new MiniDungeonCommand(plugin, manager);
                registrar.registerCommand(this, "minidungeon", cmdExecutor, cmdExecutor);
            } else {
                plugin.getLogger().warning("ModuleRegistrar not found, skipping listener/command registration.");
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
