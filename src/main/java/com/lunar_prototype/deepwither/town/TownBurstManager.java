package com.lunar_prototype.deepwither.town;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 街（SafeZoneリージョン）に一時的なMob（NPC）をスポーンさせ、
 * 一定時間後に自動的に消滅させることで賑わいを創出するマネージャー。
 */
@DependsOn({})
public class TownBurstManager implements IManager {

    private final Deepwither plugin;
    private final Random random = new Random();

    // 設定値
    private final List<String> burstRegions;
    private final long burstIntervalTicks;
    private final int burstSpawnCount;
    private final long entityLifetimeTicks;

    // スポーンエンティティの抽選リスト
    private final List<WeightedEntity> weightedEntities = new ArrayList<>();
    private double totalWeight = 0;

    private BukkitTask burstTask;

    public TownBurstManager(Deepwither plugin) {
        this.plugin = plugin;

        // 設定のロード
        ConfigurationSection settings = plugin.getConfig().getConfigurationSection("town_burst_settings");
        if (settings == null) {
            plugin.getLogger().severe("Town burst settings not found in config.yml!");
            this.burstRegions = List.of();
            this.burstIntervalTicks = 2400L;
            this.burstSpawnCount = 0;
            this.entityLifetimeTicks = 6000L;
        } else {
            this.burstRegions = settings.getStringList("burst_regions");
            this.burstIntervalTicks = settings.getLong("burst_interval_ticks", 3600L);
            this.burstSpawnCount = settings.getInt("burst_spawn_count", 5);
            this.entityLifetimeTicks = settings.getLong("entity_lifetime_ticks", 6000L);

            loadEntityWeights(settings.getList("burst_entities"));
        }
    }

    @Override
    public void init() {
        startBurstTask();
    }

    @Override
    public void shutdown() {
        stopBurstTask();
    }

    /**
     * エンティティの重み設定をconfigからロードし、重みの合計を計算します。
     */
    private void loadEntityWeights(List<?> entityConfigs) {
        if (entityConfigs != null) {
            for (Object obj : entityConfigs) {
                if (obj instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) obj;
                    try {
                        String typeName = (String) map.get("type");
                        double weight = 0.0;

                        if (map.get("weight") != null) {
                            weight = ((Number) map.get("weight")).doubleValue();
                        }
                        // EntityTypeのチェック
                        EntityType type = EntityType.valueOf(typeName.toUpperCase());

                        WeightedEntity we = new WeightedEntity(type, weight);
                        weightedEntities.add(we);
                        totalWeight += weight;

                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid entity type or format in burst_entities config: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 人口管理の定期タスクを開始します。
     */
    public void startBurstTask() {
        if (burstTask != null) {
            burstTask.cancel();
        }

        if (burstRegions.isEmpty() || totalWeight <= 0 || burstSpawnCount <= 0) {
            plugin.getLogger().warning("TownBurstManager is disabled due to missing region config or zero spawn weight/count.");
            return;
        }

        // 定期タスクを開始
        burstTask = Bukkit.getScheduler().runTaskTimer(plugin, this::triggerTownBursts, 0L, burstIntervalTicks);
        plugin.getLogger().info("Town Burst Manager task started. Interval: " + (burstIntervalTicks / 20) + " seconds.");
    }

    /**
     * プラグイン無効化時にタスクを停止します。
     */
    public void stopBurstTask() {
        // 定期実行タスクの停止
        if (burstTask != null) {
            burstTask.cancel();
            burstTask = null;
        }

        // ★重要: runTaskLater でスケジュールされた個別の寿命タスクも全てキャンセル
        // これにより、シャットダウン時にサーバーログにエラーが出るのを防ぎ、残存Mobも削除される。
        Bukkit.getScheduler().cancelTasks(plugin);

        plugin.getLogger().info("Town Burst Manager tasks stopped and all scheduled mob cleanup tasks cancelled.");
    }

    /**
     * 全ての監視対象タウンにエンティティの賑わいバーストを発生させます。
     */
    private void triggerTownBursts() {
        for (String regionId : burstRegions) {
            spawnEntitiesInRegion(regionId, burstSpawnCount);
        }
    }

    /**
     * 特定のリージョン内に指定された数のエンティティをスポーンさせ、寿命を設定します。
     */
    private void spawnEntitiesInRegion(String regionId, int count) {
        for (int i = 0; i < count; i++) {

            // TODO: WorldGuard APIを使用して、リージョン内のランダムな安全な位置を取得
            Location spawnLoc = getRandomSafeLocationInRegion(regionId);

            if (spawnLoc != null) {
                EntityType typeToSpawn = selectEntityType();

                // メインスレッドでスポーン
                Entity entity = spawnLoc.getWorld().spawnEntity(spawnLoc, typeToSpawn);

                // 設定
                entity.setInvulnerable(true); // 無敵
                entity.setCustomNameVisible(false);
                entity.setSilent(true); // 無音
                entity.setPersistent(false); // ワールド保存時に永続化しない

                // 寿命を設定
                setEntityLifetime(entity);
            }
        }
    }

    /**
     * 設定された重みに基づいて、スポーンさせるEntityTypeを選択します。
     */
    private EntityType selectEntityType() {
        if (weightedEntities.isEmpty()) return EntityType.VILLAGER;

        double roll = random.nextDouble() * totalWeight;

        for (WeightedEntity we : weightedEntities) {
            roll -= we.weight();
            if (roll <= 0) {
                return we.type();
            }
        }
        return EntityType.VILLAGER; // フォールバック
    }

    /**
     * エンティティに寿命を設定し、指定時間後に削除するタスクをスケジュールします。
     */
    private void setEntityLifetime(Entity entity) {
        if (entityLifetimeTicks <= 0) return;

        // 指定した時間後に、エンティティを削除するタスクをスケジュール
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // エンティティがまだロードされており、削除されていないことを確認してから実行
            if (!entity.isDead() && entity.isValid()) {
                entity.remove();
            }
        }, entityLifetimeTicks);
    }
    // -------------------------------------------------------------
    // WorldGuard 関連のヘルパーメソッド
    // -------------------------------------------------------------

    /**
     * 指定されたWorldGuardリージョン内のランダムで安全な位置を取得します。
     * @param regionId 対象のリージョンID
     * @return 安全なスポーン位置 (Location) または見つからなかった場合は null
     */
    private Location getRandomSafeLocationInRegion(String regionId) {
        // WorldGuardのコンテナとインスタンスを取得
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        // 監視対象のワールドを特定する（リージョンIDからワールド名を取得するか、全ワールドを探索する必要がある）
        // ここでは、とりあえず全ての有効なワールドをチェックする実装とします。

        for (World bukkitWorld : Bukkit.getWorlds()) {
            RegionManager manager = container.get(BukkitAdapter.adapt(bukkitWorld));
            if (manager == null) continue;

            ProtectedRegion region = manager.getRegion(regionId);
            if (region == null) continue;

            // リージョンが見つかった
            return findSafeSpotInRegion(bukkitWorld, region);
        }

        plugin.getLogger().warning("WorldGuard region '" + regionId + "' not found in any loaded world.");
        return null; // リージョンが見つからなかった
    }

    /**
     * 指定されたリージョン内でランダムに位置を試行し、安全なスポーン位置を見つけます。
     */
    private Location findSafeSpotInRegion(World bukkitWorld, ProtectedRegion region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        // 試行回数
        final int MAX_ATTEMPTS = 50;

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            // リージョンの境界内でランダムな座標を生成
            int x = random.nextInt(max.x() - min.x() + 1) + min.x();
            int z = random.nextInt(max.z() - min.z() + 1) + min.z();

            // Y座標は、リージョンの最大Yから下に探索する
            int startY = max.y();

            for (int y = startY; y >= min.y(); y--) {
                Location testLoc = new Location(bukkitWorld, x + 0.5, y, z + 0.5); // 中央に寄せる

                // ブロックを取得
                Material current = testLoc.getBlock().getType();
                Material below = testLoc.clone().subtract(0, 1, 0).getBlock().getType();

                // 1. 現在の位置が空気または水である (スポーン可能)
                // 2. 1ブロック上が空気である (Mobが窒息しない)
                // 3. 1ブロック下が固形ブロックである (Mobが落下しない)
                if ((current.isAir() || current == Material.WATER) &&
                        testLoc.clone().add(0, 1, 0).getBlock().getType().isAir() &&
                        below.isSolid())
                {
                    // 安全なスポーン位置が見つかった
                    return testLoc;
                }

                // 固形ブロックが見つかったら、その上がスポーン位置ではないため、次のX/Zのペアに移る
                if (current.isSolid()) {
                    break;
                }
            }
        }

        plugin.getLogger().warning("Could not find a safe spawn location in region: " + region.getId() + " after " + MAX_ATTEMPTS + " attempts.");
        return null;
    }

    // -------------------------------------------------------------
    // 内部レコード: エンティティの重み付けを保持
    // -------------------------------------------------------------
    private record WeightedEntity(EntityType type, double weight) {}
}