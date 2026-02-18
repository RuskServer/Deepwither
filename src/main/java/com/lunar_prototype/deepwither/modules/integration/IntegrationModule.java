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

    public IntegrationModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            container.registerInstance(IMobService.class, new MythicMobService());
        } else {
            container.registerInstance(IMobService.class, new VanillaMobService());
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
