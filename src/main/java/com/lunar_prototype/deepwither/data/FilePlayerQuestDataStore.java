package com.lunar_prototype.deepwither.data;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.modules.aethelgard.PlayerQuestData;
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
            String uuidStr = data.getPlayerId().toString();
            String json = db.getGson().toJson(data);
            try (java.sql.Connection conn = db.getConnection()) {
                // 存在チェック
                boolean exists = false;
                try (PreparedStatement checkPs = conn.prepareStatement("SELECT 1 FROM player_quests WHERE uuid = ?")) {
                    checkPs.setString(1, uuidStr);
                    try (ResultSet rs = checkPs.executeQuery()) {
                        exists = rs.next();
                    }
                }

                if (exists) {
                    // UPDATE
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE player_quests SET data_json = ? WHERE uuid = ?")) {
                        ps.setString(1, json);
                        ps.setString(2, uuidStr);
                        ps.executeUpdate();
                    }
                } else {
                    // INSERT
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO player_quests (uuid, data_json) VALUES (?, ?)")) {
                        ps.setString(1, uuidStr);
                        ps.setString(2, json);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        if (e.getSQLState().startsWith("23")) {
                            try (PreparedStatement ps = conn.prepareStatement(
                                    "UPDATE player_quests SET data_json = ? WHERE uuid = ?")) {
                                ps.setString(1, json);
                                ps.setString(2, uuidStr);
                                ps.executeUpdate();
                            }
                        } else {
                            throw e;
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Quest save failed", e);
            }
        });
    }
    }