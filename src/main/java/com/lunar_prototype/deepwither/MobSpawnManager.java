package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.modules.mob.service.*;
import com.lunar_prototype.deepwither.modules.mob.util.MobRegionService;
import com.lunar_prototype.deepwither.outpost.OutpostEvent;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * @deprecated This class is now a legacy wrapper for the new MobModule services.
 * Use individual services from the MobModule instead.
 */
@Deprecated
public class MobSpawnManager implements IManager {

    private final Deepwither plugin;

    public MobSpawnManager(Deepwither plugin, PlayerQuestManager playerQuestManager) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        // Initialization is now handled by MobModule
    }

    @Override
    public void shutdown() {
        // Shutdown is now handled by MobModule
    }

    private MobSpawnerService getSpawner() { return DW.get(MobSpawnerService.class); }
    private MobRegistryService getRegistry() { return DW.get(MobRegistryService.class); }
    private MobRegionService getRegion() { return DW.get(MobRegionService.class); }
    private MobConfigService getConfig() { return DW.get(MobConfigService.class); }
    private MobLevelService getLevel() { return DW.get(MobLevelService.class); }
    private MobTraitService getTrait() { return DW.get(MobTraitService.class); }

    public UUID spawnDungeonMob(Location loc, String mobId, int level) {
        MobSpawnerService spawner = getSpawner();
        MobRegionService region = getRegion();
        MobLevelService levelService = getLevel();
        if (spawner == null || region == null || loc == null) return null;

        UUID mobUuid = spawner.spawnMythicMob(mobId, loc, region.getTierFromLocation(loc));
        if (mobUuid == null) return null;

        if (levelService != null) {
            Entity entity = plugin.getServer().getEntity(mobUuid);
            if (entity instanceof LivingEntity livingEntity) {
                levelService.applyLevel(livingEntity, mobId, Math.max(1, level));
            }
        }
        return mobUuid;
    }

    public void disableNormalSpawning(String regionId) {
        if (getRegistry() != null) getRegistry().disableNormalSpawning(regionId);
    }

    public void enableNormalSpawning(String regionId) {
        if (getRegistry() != null) getRegistry().enableNormalSpawning(regionId);
    }

    public String getMobOutpostId(Entity mob) {
        return getRegistry() != null ? getRegistry().getMobOutpostId(mob.getUniqueId()) : null;
    }

    public void untrackOutpostMob(UUID mobUuid) {
        if (getRegistry() != null) getRegistry().untrackOutpostMob(mobUuid);
    }

    public void untrackMob(UUID mobUuid) {
        if (getRegistry() != null) getRegistry().untrackMob(mobUuid);
    }

    public int spawnOutpostMobs(String mobId, int count, String regionId, double fixedY, OutpostEvent event) {
        return getSpawner() != null ? getSpawner().spawnOutpostMobs(mobId, count, regionId, fixedY, event) : 0;
    }

    public void removeAllOutpostMobs(String regionId) {
        MobRegistryService registry = getRegistry();
        if (registry == null) return;
        List<UUID> mobsToRemove = registry.getOutpostMobsInRegion(regionId);
        for (UUID uuid : mobsToRemove) {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null) entity.remove();
            registry.untrackMob(uuid);
        }
    }

    public List<String> getMobTraits(LivingEntity entity) {
        return getTrait() != null ? getTrait().getMobTraits(entity) : Collections.emptyList();
    }

    public List<String> getQuestCandidateMobIdsByTier(int tier) {
        MobConfigService config = getConfig();
        if (config == null) return Collections.emptyList();
        MobConfigService.MobTierConfig tierConfig = config.getTierConfig(tier);
        if (tierConfig == null) return Collections.emptyList();
        
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        for (String mobId : tierConfig.getRegularMobs()) {
            if (mobId == null || mobId.isEmpty()) continue;
            if (mobId.equalsIgnoreCase("bandit")) candidates.addAll(tierConfig.getBanditMobs());
            else candidates.add(mobId);
        }
        return new java.util.ArrayList<>(candidates);
    }

    public int getTierFromLocation(Location loc) {
        return getRegion() != null ? getRegion().getTierFromLocation(loc) : 0;
    }
}
