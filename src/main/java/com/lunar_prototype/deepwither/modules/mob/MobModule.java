package com.lunar_prototype.deepwither.modules.mob;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.modules.mob.service.*;
import com.lunar_prototype.deepwither.modules.mob.util.MobRegionService;

public class MobModule implements IModule {

    private final Deepwither plugin;

    public MobModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("MobModule: configure()");

        try {
            // Register services
            MobConfigService configService = new MobConfigService(plugin);
            container.registerInstance(MobConfigService.class, configService);

            MobRegistryService registryService = new MobRegistryService(plugin);
            container.registerInstance(MobRegistryService.class, registryService);

            MobRegionService regionService = new MobRegionService(plugin);
            container.registerInstance(MobRegionService.class, regionService);

            MobTraitService traitService = new MobTraitService(plugin);
            container.registerInstance(MobTraitService.class, traitService);

            MobLevelService levelService = new MobLevelService(plugin);
            container.registerInstance(MobLevelService.class, levelService);

            // SpawnerService depends on others
            PlayerQuestManager playerQuestManager = container.get(PlayerQuestManager.class);
            if (playerQuestManager == null) {
                plugin.getLogger().warning("PlayerQuestManager not available, quest-based spawning will be disabled.");
            }
            MobSpawnerService spawnerService = new MobSpawnerService(
                    plugin, configService, registryService, regionService, traitService, levelService, playerQuestManager
            );
            container.registerInstance(MobSpawnerService.class, spawnerService);

            // Legacy support
            container.registerInstance(com.lunar_prototype.deepwither.MobSpawnManager.class, 
                    new com.lunar_prototype.deepwither.MobSpawnManager(plugin, playerQuestManager));

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to configure MobModule components.");
            throw new IllegalStateException("MobModule configure failed", e);
        }
    }

    @Override
    public void start() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            plugin.getLogger().info("Initializing Mob Services...");

            container.get(MobConfigService.class).init();
            container.get(MobRegistryService.class).init();
            container.get(MobRegionService.class).init();
            container.get(MobTraitService.class).init();
            container.get(MobLevelService.class).init();
            container.get(MobSpawnerService.class).init();

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start MobModule components.");
            throw new IllegalStateException("MobModule start failed", e);
        }
    }

    @Override
    public void stop() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            container.get(MobSpawnerService.class).shutdown();
            container.get(MobLevelService.class).shutdown();
            container.get(MobTraitService.class).shutdown();
            container.get(MobRegionService.class).shutdown();
            container.get(MobRegistryService.class).shutdown();
            container.get(MobConfigService.class).shutdown();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to stop MobModule components.");
            throw new IllegalStateException("MobModule stop failed", e);
        }
    }
}
