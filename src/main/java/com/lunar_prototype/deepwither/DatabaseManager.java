package com.lunar_prototype.deepwither;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunar_prototype.deepwither.api.database.IDatabaseManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.lunar_prototype.deepwither.util.LocalDateAdapter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

@DependsOn({})
public class DatabaseManager implements IManager, IDatabaseManager {
    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws Exception {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        String type = config.getString("database.type", "sqlite").toLowerCase();

        HikariConfig hikariConfig = new HikariConfig();
        
        if (type.equals("mysql")) {
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String dbName = config.getString("database.mysql.database", "deepwither");
            String user = config.getString("database.mysql.username", "root");
            String pass = config.getString("database.mysql.password", "password");
            boolean useSSL = config.getBoolean("database.mysql.use-ssl", false);

            hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=%b", host, port, dbName, useSSL));
            hikariConfig.setUsername(user);
            hikariConfig.setPassword(pass);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            
            // MySQL向けの最適化設定
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        } else {
            // デフォルトは SQLite
            File dbFile = new File(plugin.getDataFolder(), "database.db");
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
        }

        hikariConfig.setPoolName("Deepwither-Pool");

        // プール設定の読み込み
        if (type.equals("sqlite")) {
            // SQLiteは同時書き込み制限のため、最大1に固定するのが最も安全
            hikariConfig.setMaximumPoolSize(1);
        } else {
            hikariConfig.setMaximumPoolSize(config.getInt("database.pool.maximum-pool-size", 10));
        }
        
        hikariConfig.setMinimumIdle(config.getInt("database.pool.minimum-idle", 2));
        hikariConfig.setConnectionTimeout(config.getLong("database.pool.connection-timeout", 30000));
        hikariConfig.setIdleTimeout(config.getLong("database.pool.idle-timeout", 600000));
        hikariConfig.setMaxLifetime(config.getLong("database.pool.max-lifetime", 1800000));
        
        hikariConfig.setConnectionTestQuery("SELECT 1");

        this.dataSource = new HikariDataSource(hikariConfig);

        // SQLiteの場合はWALモードを有効化
        if (type.equals("sqlite")) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
            }
        }

        // 全てのテーブルをここで一括初期化
        setupTables();
    }

    private void setupTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
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

            // DatabaseManager.java の setupTables 内に追加
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_professions (
                    player_id VARCHAR(36) NOT NULL,
                    profession_type VARCHAR(32) NOT NULL,
                    experience BIGINT DEFAULT 0,
                    PRIMARY KEY (player_id, profession_type)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_boosters (
                    uuid TEXT PRIMARY KEY,
                    multiplier REAL,
                    end_time INTEGER
                )""");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS market_listings (
                    id TEXT PRIMARY KEY,
                    seller_uuid TEXT,
                    item_stack TEXT,
                    price REAL,
                    listed_date INTEGER
                )""");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS market_earnings (
                    uuid TEXT PRIMARY KEY,
                    amount REAL
                )""");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clans (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    tag TEXT,
                    owner TEXT
                )""");

                        stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clan_members (
                    player_uuid TEXT PRIMARY KEY,
                    clan_id TEXT,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )""");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public CompletableFuture<Void> runAsync(Consumer<Connection> task) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection()) {
                task.accept(conn);
            } catch (SQLException e) {
                plugin.getLogger().severe("Database async error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(Function<Connection, T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                return task.apply(conn);
            } catch (SQLException e) {
                plugin.getLogger().severe("Database async error: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    // YAMLの代わりにオブジェクトを保存できる汎用メソッド
    public void saveConfig(String key, Object data) {
        String json = gson.toJson(data);
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO generic_configs (config_key, config_value) VALUES (?, ?) ON CONFLICT(config_key) DO UPDATE SET config_value = excluded.config_value")) {
            ps.setString(1, key);
            ps.setString(2, json);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public <T> T loadConfig(String key, Type typeOfT) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT config_value FROM generic_configs WHERE config_key = ?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return gson.fromJson(rs.getString("config_value"), typeOfT);
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public Gson getGson() {
        return gson;
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}