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
        String json = db.getGson().toJson(data);
        // plugin.isEnabled() チェックを含めた非同期/同期の振り分け
        Runnable saveTask = () -> {
            try (java.sql.Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO player_daily_tasks (uuid, data_json) VALUES (?, ?) " +
                                 "ON CONFLICT(uuid) DO UPDATE SET data_json = excluded.data_json")) {
                ps.setString(1, data.getPlayerId().toString());
                ps.setString(2, json);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
    }
}