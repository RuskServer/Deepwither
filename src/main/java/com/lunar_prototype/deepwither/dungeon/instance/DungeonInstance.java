package com.lunar_prototype.deepwither.dungeon.instance;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DungeonInstance {
    private final String instanceId;
    private final World world;
    private final Set<UUID> currentPlayers;
    private long lastEmptyTime;

    public String getType() {
        return type;
    }

    public String getDifficulty() {
        return difficulty;
    }

    private final String type;
    private final String difficulty;

    public DungeonInstance(String instanceId, World world, String type, String difficulty) {
        this.instanceId = instanceId;
        this.world = world;
        this.currentPlayers = new HashSet<>();
        // 初期状態ではプレイヤーがいないため、作成時刻をセットしておく
        // (生成直後に誰も入らず放置された場合も削除対象にするため)
        this.lastEmptyTime = System.currentTimeMillis();
        this.type = type;
        this.difficulty = difficulty;

        // BossBarの初期化
        this.timerBar = Bukkit.createBossBar(
                "§e§lダンジョン残り時間",
                BarColor.GREEN,
                BarStyle.SOLID
        );
    }

    public String getInstanceId() {
        return instanceId;
    }

    public World getWorld() {
        return world;
    }

    public Set<UUID> getPlayers() {
        return currentPlayers;
    }

    public void addPlayer(UUID uuid) {
        currentPlayers.add(uuid);
        lastEmptyTime = -1; // プレイヤーがいる状態
    }

    public void removePlayer(UUID uuid) {
        currentPlayers.remove(uuid);
        if (currentPlayers.isEmpty()) {
            lastEmptyTime = System.currentTimeMillis();
        }
    }

    public boolean isEmpty() {
        return currentPlayers.isEmpty();
    }

    public long getLastEmptyTime() {
        return lastEmptyTime;
    }

    // --- PvPvE Lifecycle ---
    private BossBar timerBar;
    private final int MAX_TIME_SECONDS = 15 * 60; // 15分
    private int remainingSeconds = MAX_TIME_SECONDS;

    private BukkitTask timerTask;
    public void startLifeCycle() {
        // 1秒ごとに更新するタスク
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                remainingSeconds--;

                if (remainingSeconds <= 0) {
                    handleTimeLimit();
                    this.cancel();
                    return;
                }

                // ゲージと色の更新
                double progress = (double) remainingSeconds / MAX_TIME_SECONDS;
                timerBar.setProgress(progress);

                // 残り時間に応じて色を変える
                if (progress < 0.2) {
                    timerBar.setColor(BarColor.RED);
                    timerBar.setTitle("§c§l警告: ダンジョン崩壊まで残り " + remainingSeconds + "秒");
                } else if (progress < 0.5) {
                    timerBar.setColor(BarColor.YELLOW);
                }

                // 分:秒 表記にタイトルを更新
                int mins = remainingSeconds / 60;
                int secs = remainingSeconds % 60;
                timerBar.setTitle(String.format("§e§l制限時間: %02d:%02d", mins, secs));
            }
        }.runTaskTimer(com.lunar_prototype.deepwither.Deepwither.getInstance(), 0L, 20L);
    }

    private void handleTimeLimit() {
        if (currentPlayers.isEmpty())
            return;

        for (UUID uuid : currentPlayers) {
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage("§c§l[Dungeon] §r§4制限時間が経過しました。ダンジョンは崩壊します...");
                p.setHealth(0); // 死亡
            }
        }
        cleanup();
    }

    public void cleanup() {
        if (timerTask != null) timerTask.cancel();
        if (timerBar != null) {
            timerBar.removeAll(); // 全プレイヤーからバーを消す
        }
    }
}
