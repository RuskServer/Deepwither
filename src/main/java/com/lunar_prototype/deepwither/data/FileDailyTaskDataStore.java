package com.lunar_prototype.deepwither.data;


import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@DependsOn({DatabaseManager.class})
public class FileDailyTaskDataStore implements DailyTaskDataStore, IManager {

    private final Deepwither plugin;
    private final DatabaseManager db;

    public FileDailyTaskDataStore(Deepwither plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public void init() {
        // 必要に応じて初期化（テーブル作成はDatabaseManagerで実施済み）
    }

    @Override
    public CompletableFuture<DailyTaskData> loadTaskData(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (java.sql.Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                    "SELECT data_json FROM player_daily_tasks WHERE uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return db.getGson().fromJson(rs.getString("data_json"), DailyTaskData.class);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    @Override
    public void saveTaskData(DailyTaskData data) {
        String uuidStr = data.getPlayerId().toString();
        String json = db.getGson().toJson(data);
        try (java.sql.Connection conn = db.getConnection()) {
            // 存在チェック
            boolean exists = false;
            try (PreparedStatement checkPs = conn.prepareStatement("SELECT 1 FROM player_daily_tasks WHERE uuid = ?")) {
                checkPs.setString(1, uuidStr);
                try (ResultSet rs = checkPs.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                // UPDATE
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE player_daily_tasks SET data_json = ? WHERE uuid = ?")) {
                    ps.setString(1, json);
                    ps.setString(2, uuidStr);
                    ps.executeUpdate();
                }
            } else {
                // INSERT
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO player_daily_tasks (uuid, data_json) VALUES (?, ?)")) {
                    ps.setString(1, uuidStr);
                    ps.setString(2, json);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    if (e.getSQLState().startsWith("23")) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE player_daily_tasks SET data_json = ? WHERE uuid = ?")) {
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
            e.printStackTrace();
        }
    }
    }