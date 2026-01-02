package com.lunar_prototype.deepwither;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lunar_prototype.deepwither.util.LocalDateAdapter;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.time.LocalDate;

public class DatabaseManager {
    private final Connection connection;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter()) // これを追加
            .create();

    public DatabaseManager(JavaPlugin plugin) throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "database.db");
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

        // 全てのテーブルをここで一括初期化
        setupTables();
    }

    private void setupTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // AttributeManager用
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_attributes (
                    uuid TEXT PRIMARY KEY, total_points INTEGER,
                    str INTEGER, vit INTEGER, mnd INTEGER, int INTEGER, agi INTEGER
                )""");

            // LevelManager用
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_levels (
                    uuid TEXT PRIMARY KEY, level INTEGER, exp REAL
                )""");

            // SkilltreeManager用
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_skilltree (
                    uuid TEXT PRIMARY KEY, skill_point INTEGER, skills TEXT
                )""");

            // 汎用データ（YAML代替用）
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS generic_configs (
                    config_key TEXT PRIMARY KEY, config_value TEXT
                )""");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_daily_tasks (
                   uuid TEXT PRIMARY KEY,
                data_json TEXT
                )""");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_quests (
                    uuid TEXT PRIMARY KEY,
                    data_json TEXT
                )""");
        }
    }

    public Connection getConnection() { return connection; }

    // YAMLの代わりにオブジェクトを保存できる汎用メソッド
    public void saveConfig(String key, Object data) {
        String json = gson.toJson(data);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO generic_configs (config_key, config_value) VALUES (?, ?) ON CONFLICT(config_key) DO UPDATE SET config_value = excluded.config_value")) {
            ps.setString(1, key);
            ps.setString(2, json);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public <T> T loadConfig(String key, Type typeOfT) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT config_value FROM generic_configs WHERE config_key = ?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return gson.fromJson(rs.getString("config_value"), typeOfT);
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public Gson getGson() {
        return gson;
    }

    public void close() {
        try { if (connection != null) connection.close(); } catch (SQLException e) { e.printStackTrace(); }
    }
}