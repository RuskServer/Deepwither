package com.lunar_prototype.deepwither.modules.minidungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

public class MiniDungeonModule implements IModule {

    private final Deepwither plugin;
    private MiniDungeonManager manager;

    public MiniDungeonModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("Configuring MiniDungeonModule...");

        this.manager = new MiniDungeonManager(plugin);
        
        // Register container
        container.registerInstance(MiniDungeonManager.class, manager);
    }

    @Override
    public void start() {
        if (manager != null) {
            manager.init();

            // Register Listener
            Bukkit.getPluginManager().registerEvents(new MiniDungeonListener(plugin, manager), plugin);

            // Register Command
            PluginCommand command = plugin.getCommand("minidungeon");
            if (command != null) {
                MiniDungeonCommand cmdExecutor = new MiniDungeonCommand(plugin, manager);
                command.setExecutor(cmdExecutor);
                command.setTabCompleter(cmdExecutor);
            } else {
                plugin.getLogger().warning("Failed to register '/minidungeon' command. Please check plugin.yml.");
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
