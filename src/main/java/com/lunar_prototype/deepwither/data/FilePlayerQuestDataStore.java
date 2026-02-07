package com.lunar_prototype.deepwither.data;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@DependsOn({DatabaseManager.class})
public class FilePlayerQuestDataStore implements PlayerQuestDataStore, IManager {

    private final DatabaseManager db;

    public FilePlayerQuestDataStore(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void init() {}

    @Override
    public CompletableFuture<PlayerQuestData> loadQuestData(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (java.sql.Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                    "SELECT data_json FROM player_quests WHERE uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return db.getGson().fromJson(rs.getString("data_json"), PlayerQuestData.class);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new PlayerQuestData(playerId); // 新規データ
        });
    }

    @Override
    public CompletableFuture<Void> saveQuestData(PlayerQuestData data) {
        return CompletableFuture.runAsync(() -> {
            String json = db.getGson().toJson(data);
            try (java.sql.Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO player_quests (uuid, data_json) VALUES (?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET data_json = excluded.data_json")) {
                ps.setString(1, data.getPlayerId().toString());
                ps.setString(2, json);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Quest save failed", e);
            }
        });
    }
}