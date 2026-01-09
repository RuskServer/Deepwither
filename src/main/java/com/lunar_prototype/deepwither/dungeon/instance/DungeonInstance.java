package com.lunar_prototype.deepwither.dungeon.instance;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DungeonInstance {
    private final String instanceId; // World Name
    private final World world;
    private final Set<UUID> players = new HashSet<>();
    private long lastEmptyTime = -1;

    // Lazy Spawn Data
    private final List<Location> pendingMobSpawns = new ArrayList<>();
    private final List<String> mobIds = new ArrayList<>();

    public DungeonInstance(String instanceId, World world) {
        this.instanceId = instanceId;
        this.world = world;
        // 初期状態ではプレイヤーがいないため、作成時刻をセットしておく
        // (生成直後に誰も入らず放置された場合も削除対象にするため)
        this.lastEmptyTime = System.currentTimeMillis();
    }

    public String getInstanceId() {
        return instanceId;
    }

    public World getWorld() {
        return world;
    }

    public Set<UUID> getPlayers() {
        return players;
    }

    public void addPlayer(UUID uuid) {
        players.add(uuid);
        lastEmptyTime = -1; // プレイヤーがいる状態
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
        if (players.isEmpty()) {
            lastEmptyTime = System.currentTimeMillis();
        }
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public long getLastEmptyTime() {
        return lastEmptyTime;
    }

    public List<Location> getPendingMobSpawns() {
        return pendingMobSpawns;
    }

    public List<String> getMobIds() {
        return mobIds;
    }

    public void setPendingSpawnData(List<Location> spawns, List<String> ids) {
        this.pendingMobSpawns.clear();
        this.pendingMobSpawns.addAll(spawns);
        this.mobIds.clear();
        this.mobIds.addAll(ids);
    }
}
