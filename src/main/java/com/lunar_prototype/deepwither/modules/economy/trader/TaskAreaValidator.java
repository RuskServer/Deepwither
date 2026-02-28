package com.lunar_prototype.deepwither.modules.economy.trader;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * プレイヤーの位置情報やエリア設定に基づいた判定を行うバリデーター
 */
public class TaskAreaValidator {

    /**
     * 現在地の WorldGuard リージョンからティア（難易度レベル）を判定します。
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
     * プレイヤーが指定されたタスクの対象エリア内にいるかを確認します。
     */
    public boolean isInTaskArea(Player player, ConfigurationSection taskConfig) {
        if (taskConfig == null) return false;

        Location targetLoc = new Location(
                Bukkit.getWorld(taskConfig.getString("world", "world")),
                taskConfig.getDouble("x"),
                taskConfig.getDouble("y"),
                taskConfig.getDouble("z")
        );
        double radius = taskConfig.getDouble("radius", 3.0);

        return player.getWorld().equals(targetLoc.getWorld()) &&
                player.getLocation().distanceSquared(targetLoc) <= (radius * radius);
    }
}
