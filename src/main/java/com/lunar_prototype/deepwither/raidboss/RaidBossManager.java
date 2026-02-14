package com.lunar_prototype.deepwither.raidboss;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

@DependsOn({})
public class RaidBossManager implements IManager {

    private final JavaPlugin plugin;
    private final Map<String, RaidBossData> bossDataMap = new HashMap<>();

    public RaidBossManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        loadConfig();
    }

    @Override
    public void shutdown() {}

    public void loadConfig() {
        bossDataMap.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("raid_bosses");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection bossSec = section.getConfigurationSection(key);
            if (bossSec == null) continue;

            String mythicMobId = bossSec.getString("mythic_mob");
            String regionId = bossSec.getString("region");

            ConfigurationSection locSec = bossSec.getConfigurationSection("location");
            String worldName = locSec != null ? locSec.getString("world", "aether") : "aether";
            double x = locSec != null ? locSec.getDouble("x") : 0;
            double y = locSec != null ? locSec.getDouble("y") : 0;
            double z = locSec != null ? locSec.getDouble("z") : 0;

            RaidBossData data = new RaidBossData(key, mythicMobId, regionId, worldName, x, y, z);
            bossDataMap.put(key, data);
        }
        plugin.getLogger().info("レイドボス設定を " + bossDataMap.size() + " 件ロードしました。");
    }

    public RaidBossData getBossData(String id) {
        return bossDataMap.get(id);
    }

    public boolean spawnBoss(Player player, String bossId) {
        RaidBossData data = bossDataMap.get(bossId);
        if (data == null) {
            player.sendMessage(Component.text("エラー: このボスの設定が存在しません。", NamedTextColor.RED));
            return false;
        }

        if (!isInRegion(player, data.regionId)) {
            player.sendMessage(Component.text("このアイテムは決戦のバトルフィールド (", NamedTextColor.RED)
                    .append(Component.text(data.regionId, NamedTextColor.YELLOW))
                    .append(Component.text(") でのみ使用可能です。", NamedTextColor.RED)));
            return false;
        }

        World world = Bukkit.getWorld(data.worldName);
        if (world == null) {
            player.sendMessage(Component.text("エラー: 指定されたワールド (" + data.worldName + ") が見つかりません。", NamedTextColor.RED));
            return false;
        }

        Location spawnLoc = new Location(world, data.x, data.y, data.z);

        try {
            MythicBukkit.inst().getAPIHelper().spawnMythicMob(data.mythicMobId, spawnLoc);

            player.sendMessage(Component.text("[RAID] ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                    .append(Component.text("レイドボス ", NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(data.mythicMobId, NamedTextColor.WHITE))
                    .append(Component.text(" が出現しました！", NamedTextColor.LIGHT_PURPLE)));
            world.strikeLightningEffect(spawnLoc);
            return true;
        } catch (Exception e) {
            player.sendMessage(Component.text("MythicMobsのスポーンに失敗しました。IDを確認してください: " + data.mythicMobId, NamedTextColor.RED));
            e.printStackTrace();
            return false;
        }
    }

    private boolean isInRegion(Player player, String targetRegionId) {
        if (targetRegionId == null || targetRegionId.isEmpty()) return true;

        Location loc = player.getLocation();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        for (ProtectedRegion region : set) {
            if (region.getId().equalsIgnoreCase(targetRegionId)) {
                return true;
            }
        }
        return false;
    }

    public static class RaidBossData {
        private final String id;
        private final String mythicMobId;
        private final String regionId;
        private final String worldName;
        private final double x, y, z;

        public RaidBossData(String id, String mythicMobId, String regionId, String worldName, double x, double y, double z) {
            this.id = id;
            this.mythicMobId = mythicMobId;
            this.regionId = regionId;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
