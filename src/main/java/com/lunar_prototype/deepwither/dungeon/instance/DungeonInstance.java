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
        // 3分ごとにモブをリセット＆リスポーン
        // 最初の実行は3分後 (既に生成時に湧いているため)
        respawnTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                respawnMobs();
            }
        }.runTaskTimer(com.lunar_prototype.deepwither.Deepwither.getInstance(), 3600L, 3600L); // 3 mins

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

    private void respawnMobs() {
        if (spawners == null || spawners.isEmpty())
            return;
        if (world == null)
            return;

        // 既存モブの扱い: "リセット" なので、エリア内の特定モブを消すか？
        // 簡易実装として、単純に追加で湧かせるが、重複しすぎないようにするなら
        // PendingSpawnerの位置周辺のモブをチェックするなどのロジックが必要。
        // ここではユーザー要望の「リセットされ復活する」を「湧き直し」と解釈し、
        // 既存のモブが残っていても湧かせる (PvPvEなら倒されていることが多い想定)
        // 必要であれば world.getEntities() で一掃する処理を追加

        for (com.lunar_prototype.deepwither.dungeon.DungeonGenerator.PendingSpawner spawner : spawners) {
            // チャンクがロードされている場合のみ
            if (spawner.getLocation().getChunk().isLoaded()) {
                com.lunar_prototype.deepwither.Deepwither.getInstance().getMobSpawnManager()
                        .spawnDungeonMob(spawner.getLocation(), spawner.getMobId(), spawner.getLevel());
                world.spawnParticle(org.bukkit.Particle.CLOUD, spawner.getLocation(), 20, 0.5, 1, 0.5, 0.1);
            }
        }

        // プレイヤーに通知
        for (UUID uuid : currentPlayers) {
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage("§c§l[Dungeon] §r§7ダンジョンのモンスターたちが再活性化した...");
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_AMBIENT, 0.5f, 0.5f);
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
