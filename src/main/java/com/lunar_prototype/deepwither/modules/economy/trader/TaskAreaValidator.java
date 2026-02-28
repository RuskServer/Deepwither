package com.lunar_prototype.deepwither.modules.economy.trader;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * プレイヤーの位置情報やエリア設定に基づいた判定を行うバリデーター
 */
public class TaskAreaValidator {

    /**
     * Determine the highest numeric tier encoded in WorldGuard region IDs that contain the given location.
     *
     * The method scans each applicable region's ID for a sequence of digits immediately following the character 't'
     * (for example, "area_t3" yields tier 3) and returns the largest parsed tier. Regions without a valid "t<digits>"
     * pattern are ignored.
     *
     * @param loc the location to inspect for overlapping WorldGuard regions
     * @return the highest tier number found in region IDs, or 0 if none are present
     */
    public int getTierFromLocation(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        int maxTier = 0;
        for (ProtectedRegion region : set) {
            String id = region.getId().toLowerCase();
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

    /**
     * Determines whether a player is located inside the task area defined by the configuration.
     *
     * <p>The configuration should contain "x", "y", and "z" for the target location. It may include
     * "world" (defaults to "world") and "radius" (defaults to 3.0).
     *
     * @param player the player to check
     * @param taskConfig configuration specifying the task area
     * @return `true` if the player is in the same world and within the configured radius of the target location, `false` otherwise
     */
    public boolean isInTaskArea(Player player, ConfigurationSection taskConfig) {
        if (taskConfig == null) return false;
        String worldName = taskConfig.getString("world", "world");
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return false;

        Location targetLoc = new Location(
                world,
                taskConfig.getDouble("x"),
                taskConfig.getDouble("y"),
                taskConfig.getDouble("z")
        );
        double radius = taskConfig.getDouble("radius", 3.0);

        return player.getWorld().equals(world) &&
                player.getLocation().distanceSquared(targetLoc) <= (radius * radius);
    }
}
