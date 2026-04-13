package com.lunar_prototype.deepwither.modules.mob;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.modules.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.modules.mob.service.*;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMobManager;
import com.lunar_prototype.deepwither.modules.mob.implementation.FireDemon;
import com.lunar_prototype.deepwither.modules.mob.implementation.IcePilgrim;
import com.lunar_prototype.deepwither.modules.mob.implementation.VanguardSkeleton;
import com.lunar_prototype.deepwither.modules.mob.implementation.CrimsonLancer;
import com.lunar_prototype.deepwither.modules.mob.util.MobRegionService;
import org.bukkit.entity.EntityType;

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
            CustomMobManager customMobManager = new CustomMobManager(plugin);
            container.registerInstance(CustomMobManager.class, customMobManager);

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
            plugin.getLogger().info("Starting MobModule (Listeners & Framework Initialization)...");

            // Initialize CustomMob framework
            CustomMobManager customMobManager = container.get(CustomMobManager.class);
            // CustomMobManager is an IManager, its init() is handled by DI container.
            // We just register the mobs here.
            customMobManager.registerMob("FireDemon", FireDemon.class, EntityType.ZOMBIE);
            customMobManager.registerMob("IcePilgrim", IcePilgrim.class, EntityType.WITHER_SKELETON);
            customMobManager.registerMob("SilentWatcher", com.lunar_prototype.deepwither.modules.mob.implementation.SilentWatcher.class, EntityType.HUSK);
            customMobManager.registerMob("EngravedExecutor", com.lunar_prototype.deepwither.modules.mob.implementation.EngravedExecutor.class, EntityType.WITHER_SKELETON);
            customMobManager.registerMob("melee_skeleton", VanguardSkeleton.class, EntityType.SKELETON);
            customMobManager.registerMob("melee_zombie2", CrimsonLancer.class, EntityType.ZOMBIE);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start MobModule components.");
            throw new IllegalStateException("MobModule start failed", e);
        }
    }

    @Override
    public void stop() {
        plugin.getLogger().info("Stopping MobModule...");
    }
}
