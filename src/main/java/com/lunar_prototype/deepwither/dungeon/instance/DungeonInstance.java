package com.lunar_prototype.deepwither.dungeon.instance;

import org.bukkit.World;
import java.util.HashSet;
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

    public DungeonInstance(String instanceId, World world,String type,String difficulty) {
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
}
