package com.lunar_prototype.deepwither.dungeon.instance;

import org.bukkit.World;
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
    private List<com.lunar_prototype.deepwither.dungeon.DungeonGenerator.PendingSpawner> spawners = new ArrayList<>();
    private org.bukkit.scheduler.BukkitTask respawnTask;
    private org.bukkit.scheduler.BukkitTask limitTask;

    public void setSpawners(List<com.lunar_prototype.deepwither.dungeon.DungeonGenerator.PendingSpawner> spawners) {
        this.spawners = spawners;
    }

    public void startLifeCycle() {
        // 15分制限 (15 * 60 * 20 = 18000 ticks)
        limitTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                handleTimeLimit();
            }
        }.runTaskLater(com.lunar_prototype.deepwither.Deepwither.getInstance(), 18000L);
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
    }

    public void cleanup() {
        if (respawnTask != null && !respawnTask.isCancelled()) {
            respawnTask.cancel();
        }
        if (limitTask != null && !limitTask.isCancelled()) {
            limitTask.cancel();
        }
    }
}
