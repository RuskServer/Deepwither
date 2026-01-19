package com.lunar_prototype.deepwither.dungeon.instance;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.dungeon.instance.DungeonInstance;
import com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class DungeonExtractionManager {

    private final Deepwither plugin;
    // インスタンスごとの脱出候補地点（まだ出ていない場所）
    private final Map<String, List<Location>> pendingExtractions = new HashMap<>();
    // インスタンスごとのアクティブな脱出地点
    private final Map<String, List<Location>> activeExtractions = new HashMap<>();

    // インスタンスIDごとの「3分ごと生成タスク」を保持
    private final Map<String, BukkitTask> extractionTasks = new HashMap<>();

    private final Map<String, List<BukkitTask>> effectTasks = new HashMap<>();

    public DungeonExtractionManager(Deepwither plugin) {
        this.plugin = plugin;
        startDetectionTask();
    }

    /**
     * ダンジョン生成完了時に呼び出す。3分おきに脱出地点を増やすタイマーを開始
     */
    public void registerExtractionTask(String instanceId, List<Location> spawnPoints) {
        plugin.getLogger().info("[DungeonExtractionManager] Registering task for instance: " + instanceId + " with "
                + spawnPoints.size() + " spawns.");

        List<Location> shuffled = new ArrayList<>(spawnPoints);
        Collections.shuffle(shuffled);

        pendingExtractions.put(instanceId, shuffled);
        activeExtractions.put(instanceId, new ArrayList<>());

        // 3分(3600L)おきに脱出地点を開放
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // インスタンスが消滅していたらタスク終了
                if (DungeonInstanceManager.getInstance().getActiveInstances().get(instanceId) == null) {
                    plugin.getLogger().info("[DungeonExtractionManager] Instance " + instanceId
                            + " not found or inactive. Cancelling task.");
                    pendingExtractions.remove(instanceId);
                    activeExtractions.remove(instanceId);
                    this.cancel();
                    return;
                }

                plugin.getLogger().info("[DungeonExtractionManager] Running extraction task for " + instanceId);
                activateNextPoint(instanceId);
            }
        }.runTaskTimer(plugin, 3600L, 3600L);

        extractionTasks.put(instanceId, task);
    }

    /**
     * 次の脱出地点を有効化し、演出を行う
     */
    private void activateNextPoint(String instanceId) {
        List<Location> pending = pendingExtractions.get(instanceId);
        if (pending == null || pending.isEmpty()) {
            plugin.getLogger().warning("[DungeonExtractionManager] No pending extractions for " + instanceId);
            return;
        }

        Location loc = pending.remove(0);
        activeExtractions.get(instanceId).add(loc);

        World world = loc.getWorld();
        // 視認性のためのブロック設置（エンドポータルフレーム等）
        loc.getBlock().setType(Material.END_PORTAL_FRAME);

        // 演出：雷（ダメージなし）と音
        world.strikeLightningEffect(loc);
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);

        // メッセージ通知
        broadcastToInstance(instanceId, "§b§l[!] 脱出地点が活性化しました！座標: "
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());

        // パーティクル演出タスク（常時発光させて見つけやすくする）
        startParticleEffect(instanceId, loc);
    }

    /**
     * 脱出地点のパーティクル演出
     */
    private void startParticleEffect(String instanceId, Location loc) {
        BukkitTask effectTask = new BukkitRunnable() {
            double angle = 0;

            @Override
            public void run() {
                if (loc.getBlock().getType() != Material.END_PORTAL_FRAME) {
                    this.cancel();
                    return;
                }
                // 円状にパーティクルを出す
                double x = Math.cos(angle) * 1.5;
                double z = Math.sin(angle) * 1.5;
                loc.getWorld().spawnParticle(Particle.WITCH, loc.clone().add(x, 1, z), 1, 0, 0, 0, 0);

                // メモ：Particle.FLASHにColorデータが必要な場合の考慮（もしREDSTONEなどを使う場合）
                // ここでは標準のWITCHやEND_RODを使用
                loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 1, 0.1, 0.5, 0.1, 0.05);

                angle += 0.2;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        effectTasks.computeIfAbsent(instanceId, k -> new ArrayList<>()).add(effectTask);
    }

    /**
     * 特定のインスタンスに関するすべてのタイマーとエフェクトを完全に停止する
     */
    public void stopExtractionSystem(String instanceId) {
        // 1. メインの生成タイマーを止める
        if (extractionTasks.containsKey(instanceId)) {
            extractionTasks.get(instanceId).cancel();
            extractionTasks.remove(instanceId);
            plugin.getLogger().info("Extraction timer stopped for: " + instanceId);
        }

        // 2. 実行中のパーティクル演出をすべて止める
        if (effectTasks.containsKey(instanceId)) {
            for (BukkitTask effectTask : effectTasks.get(instanceId)) {
                effectTask.cancel();
            }
            effectTasks.remove(instanceId);
        }

        // 3. データのクリーンアップ
        pendingExtractions.remove(instanceId);
        activeExtractions.remove(instanceId);
    }

    /**
     * プレイヤーの脱出判定タスク
     */
    private void startDetectionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String instanceId = player.getWorld().getName(); // ワールド名がIDの場合
                    List<Location> extractions = activeExtractions.get(instanceId);

                    if (extractions == null)
                        continue;

                    for (Location loc : extractions) {
                        if (player.getLocation().distance(loc) < 2.0) {
                            performExtraction(player);
                            break;
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    /**
     * 脱出成功時の処理
     */
    private void performExtraction(Player player) {
        player.sendTitle("§a§lSUCCESS", "§fダンジョンから無事脱出した！", 10, 70, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // ロビー等へ転送（DungeonInstanceManagerのleaveを使用）
        DungeonInstanceManager.getInstance().leaveDungeon(player);
        Deepwither.getInstance().getRoguelikeBuffManager().clearBuffs(player);

        Bukkit.broadcastMessage("§7[Deepwither] §b" + player.getName() + " §fがダンジョンから生還しました！");
    }

    private void broadcastToInstance(String instanceId, String message) {
        DungeonInstance inst = DungeonInstanceManager.getInstance().getActiveInstances().get(instanceId);
        if (inst != null) {
            inst.getPlayers().forEach(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null)
                    p.sendMessage(message);
            });
        }
    }
}
