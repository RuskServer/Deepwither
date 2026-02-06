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

            // 座標設定
            ConfigurationSection locSec = bossSec.getConfigurationSection("location");
            String worldName = locSec != null ? locSec.getString("world", "aether") : "aether"; // デフォルトaether
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

    // 召喚処理
    public boolean spawnBoss(Player player, String bossId) {
        RaidBossData data = bossDataMap.get(bossId);
        if (data == null) {
            player.sendMessage("§cエラー: このボスの設定が存在しません。");
            return false;
        }

        // 1. リージョンチェック
        if (!isInRegion(player, data.regionId)) {
            player.sendMessage("§cこのアイテムは決戦のバトルフィールド (§e" + data.regionId + "§c) でのみ使用可能です。");
            return false;
        }

        // 2. ワールド取得
        World world = Bukkit.getWorld(data.worldName);
        if (world == null) {
            player.sendMessage("§cエラー: 指定されたワールド (" + data.worldName + ") が見つかりません。");
            return false;
        }

        Location spawnLoc = new Location(world, data.x, data.y, data.z);

        // 3. MythicMobsスポーン
        try {
            MythicBukkit.inst().getAPIHelper().spawnMythicMob(data.mythicMobId, spawnLoc);

            // メッセージや音の演出
            player.sendMessage("§5§l[RAID] §dレイドボス §f" + data.mythicMobId + " §dが出現しました！");
            world.strikeLightningEffect(spawnLoc); // 雷エフェクト
            return true;
        } catch (Exception e) {
            player.sendMessage("§cMythicMobsのスポーンに失敗しました。IDを確認してください: " + data.mythicMobId);
            e.printStackTrace();
            return false;
        }
    }

    // WorldGuardリージョン判定
    private boolean isInRegion(Player player, String targetRegionId) {
        if (targetRegionId == null || targetRegionId.isEmpty()) return true; // 設定なければどこでも可とする場合

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

    // データクラス
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