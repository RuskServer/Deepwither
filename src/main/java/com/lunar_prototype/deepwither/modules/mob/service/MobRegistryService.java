package com.lunar_prototype.deepwither.modules.mob.service;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MobRegistryService implements IManager {

    private final Deepwither plugin;
    private final Map<UUID, Set<UUID>> spawnedMobsTracker = new ConcurrentHashMap<>();
    private final Map<UUID, String> outpostMobTracker = new ConcurrentHashMap<>();
    private final Set<String> spawnDisabledRegions = ConcurrentHashMap.newKeySet();

    public MobRegistryService(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        // cleanupDeadMobs is usually called from a scheduler in the SpawnerService,
        // but we can also provide a standalone cleanup task if needed.
    }

    @Override
    public void shutdown() {
        spawnedMobsTracker.clear();
        outpostMobTracker.clear();
        spawnDisabledRegions.clear();
    }

    public int getTrackedMobCount(UUID playerId) {
        return spawnedMobsTracker.getOrDefault(playerId, Collections.emptySet()).size();
    }

    public void trackSpawnedMob(UUID playerId, UUID mobUuid) {
        if (mobUuid == null) return;
        spawnedMobsTracker.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(mobUuid);
    }

    public void untrackMob(UUID mobUuid) {
        for (Set<UUID> mobUuids : spawnedMobsTracker.values()) {
            mobUuids.remove(mobUuid);
        }
        outpostMobTracker.remove(mobUuid);
    }

    public void cleanupDeadMobs() {
        for (Map.Entry<UUID, Set<UUID>> entry : spawnedMobsTracker.entrySet()) {
            entry.getValue().removeIf(uuid -> Bukkit.getEntity(uuid) == null);
        }
        spawnedMobsTracker.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void trackOutpostMob(UUID mobUuid, String regionId) {
        outpostMobTracker.put(mobUuid, regionId);
    }

    public String getMobOutpostId(UUID mobUuid) {
        return outpostMobTracker.get(mobUuid);
    }

    public void untrackOutpostMob(UUID mobUuid) {
        outpostMobTracker.remove(mobUuid);
    }

    public List<UUID> getOutpostMobsInRegion(String regionId) {
        List<UUID> mobs = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : outpostMobTracker.entrySet()) {
            if (regionId.equalsIgnoreCase(entry.getValue())) {
                mobs.add(entry.getKey());
            }
        }
        return mobs;
    }

    public void disableNormalSpawning(String regionId) {
        spawnDisabledRegions.add(regionId.toLowerCase());
    }

    public void enableNormalSpawning(String regionId) {
        spawnDisabledRegions.remove(regionId.toLowerCase());
    }

    public boolean isSpawnDisabledInRegion(String regionId) {
        return spawnDisabledRegions.contains(regionId.toLowerCase());
    }
}
