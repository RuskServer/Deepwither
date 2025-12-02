package com.lunar_prototype.deepwither.util;

import com.lunar_prototype.deepwither.Deepwither;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MythicMobSafeZoneManager {

    private final Deepwither plugin;
    private BukkitTask checkTask;

    // Key: Mob UUID, Value: セーフゾーンに滞在し始めたTick (System.currentTimeMillis()に近い値)
    private final Map<UUID, Long> safeZoneEntryTime = new ConcurrentHashMap<>();

    // 削除までの時間 (ティック) - 5秒 = 100ティック
    private static final long REMOVAL_TIME_TICKS = 100L;
    // チェック周期 (ティック) - 1秒ごと (20ティック)
    private static final long CHECK_INTERVAL_TICKS = 20L;

    public MythicMobSafeZoneManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    /**
     * セーフゾーン滞在チェックの定期タスクを開始します。
     */
    public void startCheckTask() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs") ||
                !Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            plugin.getLogger().warning("MythicMobs or WorldGuard is not enabled. SafeZone Mob Removal disabled.");
            return;
        }

        if (checkTask != null) {
            checkTask.cancel();
        }

        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkMobsInSafeZone, 0L, CHECK_INTERVAL_TICKS);
        plugin.getLogger().info("MythicMob SafeZone Removal task started.");
    }

    /**
     * 全Mobに対してセーフゾーン滞在チェックを実行します。
     */
    private void checkMobsInSafeZone() {
        // サーバーが実行されている現在のティック数を取得
        long currentTick = plugin.getServer().getCurrentTick();

        // ワールドをまたいで処理を行う
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof LivingEntity)) continue;

                // 1. MythicMobの判定
                ActiveMob mob = MythicBukkit.inst().getMobManager().getMythicMobInstance(entity);
                if (mob == null) {
                    // MythicMobではない場合、追跡から削除 (念のため)
                    safeZoneEntryTime.remove(entity.getUniqueId());
                    continue;
                }

                Location loc = entity.getLocation();
                UUID mobId = entity.getUniqueId();

                // 2. セーフゾーンの判定
                boolean isInSafeZone = isSafeZone(loc);

                if (isInSafeZone) {
                    // セーフゾーンにいる場合
                    safeZoneEntryTime.putIfAbsent(mobId, currentTick);

                    long entryTick = safeZoneEntryTime.get(mobId);

                    // 3. 5秒(100ティック)以上の滞在チェック
                    if (currentTick - entryTick >= REMOVAL_TIME_TICKS) {
                        // 削除実行
                        mob.remove();
                        safeZoneEntryTime.remove(mobId);
                        plugin.getLogger().info("Removed MythicMob '" + mob.getName() + "' due to extended stay in safezone.");
                    }
                } else {
                    // セーフゾーンから出た場合、タイマーをリセット
                    safeZoneEntryTime.remove(mobId);
                }
            }
        }
    }

    /**
     * 指定されたLocationが、名前に「safezone」を含むリージョン内にあるかを判定します。
     * (SafeZoneListenerからコピーして再利用)
     */
    private boolean isSafeZone(Location loc) {
        // WorldGuardのAPI経由でリージョンコンテナを取得
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        // WorldGuardがMobのいるワールドに対応しているかを確認
        if (loc.getWorld() == null) return false;

        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        for (ProtectedRegion region : set) {
            if (region.getId().toLowerCase().contains("safezone")) {
                return true;
            }
        }
        return false;
    }

    // サーバー停止時のクリーンアップ
    public void stopCheckTask() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        safeZoneEntryTime.clear();
    }
}