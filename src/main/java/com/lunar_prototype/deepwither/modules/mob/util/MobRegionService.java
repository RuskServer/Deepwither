package com.lunar_prototype.deepwither.modules.mob.util;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.modules.mob.service.MobRegistryService;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Random;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class MobRegionService implements IManager {

    private final Deepwither plugin;
    private final Map<UUID, Location> spawnLockLocations = new HashMap<>();
    private static final double MOVE_UNLOCK_DISTANCE_SQUARED = 30.0 * 30.0;

    public MobRegionService(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        spawnLockLocations.clear();
    }

    public boolean isSafeZone(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        for (ProtectedRegion region : set) {
            String regionId = region.getId().toLowerCase();
            if (regionId.contains("safezone") || regionId.contains("kbf")) return true;
        }
        return false;
    }

    public int getTierFromLocation(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        int maxTier = 0;
        for (ProtectedRegion region : set) {
            String id = region.getId().toLowerCase();
            if (id.contains("safezone")) return 0;
            int tierIndex = id.indexOf("t");
            if (tierIndex != -1 && tierIndex + 1 < id.length()) {
                char nextChar = id.charAt(tierIndex + 1);
                if (Character.isDigit(nextChar)) {
                    StringBuilder tierStr = new StringBuilder();
                    int i = tierIndex + 1;
                    while (i < id.length() && Character.isDigit(id.charAt(i))) {
                        tierStr.append(id.charAt(i));
                        i++;
                    }
                    try {
                        int tier = Integer.parseInt(tierStr.toString());
                        if (tier > maxTier) maxTier = tier;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return maxTier;
    }

    public Location getRandomLocationInRegion(World world, String regionId, double fixedY) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
        if (regionManager == null) return null;
        ProtectedRegion region = regionManager.getRegion(regionId);
        if (region == null) return null;
        
        Random random = plugin.getRandom();
        int minX = region.getMinimumPoint().x();
        int maxX = region.getMaximumPoint().x();
        int minZ = region.getMinimumPoint().z();
        int maxZ = region.getMaximumPoint().z();
        
        double x = minX + random.nextDouble() * (maxX - minX + 1);
        double z = minZ + random.nextDouble() * (maxZ - minZ + 1);
        return new Location(world, x, fixedY, z);
    }

    public boolean isSpawnDisabledInWGRegion(Location loc, MobRegistryService registry) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        for (ProtectedRegion region : set) {
            if (registry.isSpawnDisabledInRegion(region.getId().toLowerCase())) return true;
        }
        return false;
    }

    public boolean isSpawnLocked(UUID playerId, Location currentLoc) {
        Location lockLoc = spawnLockLocations.get(playerId);
        if (lockLoc != null) {
            if (currentLoc.distanceSquared(lockLoc) < MOVE_UNLOCK_DISTANCE_SQUARED) {
                return true;
            } else {
                spawnLockLocations.remove(playerId);
            }
        }
        return false;
    }

    public void setSpawnLock(UUID playerId, Location loc) {
        spawnLockLocations.put(playerId, loc);
    }
}
