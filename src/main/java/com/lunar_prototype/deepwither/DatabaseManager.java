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
    private boolean failFast = false;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void checkMainThread() {
        if (org.bukkit.Bukkit.isPrimaryThread()) {
            String message = "Blocking database call on main thread! This causes server lag. Please use async methods.";
            if (failFast) {
                throw new RuntimeException(message);
            } else {
                plugin.getLogger().warning(message);
                // スタックトレースを出力して呼び出し元を特定しやすくする
                new Throwable().printStackTrace();
            }
        }
    }

    /**
     * データベース接続プールを構成・作成し、必要なテーブルおよびSQLite固有の設定を初期化する。
     *
     * <p>プラグイン設定からデータベース種別（MySQLまたはSQLite）と接続設定を読み取り、
     * HikariCP の設定を作成してデータソースを生成します。SQLite の場合はデータベースファイルを
     * プラグインフォルダに作成し、WALモードと `synchronous=NORMAL` を有効化します。最後にテーブル
     * 作成処理を呼び出してスキーマを初期化します。</p>
     *
     * @throws Exception 初期化処理中に接続確立、JDBC設定適用、PRAGMA実行、またはテーブル初期化でエラーが発生した場合
     */
    @Override
    public void init() throws Exception {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        this.failFast = config.getBoolean("database.fail-fast", false);
        // デフォルトを H2 に変更
        String type = config.getString("database.type", "h2").toLowerCase();

        HikariConfig hikariConfig = new HikariConfig();
        File oldSqliteFile = new File(plugin.getDataFolder(), "database.db");
        boolean doMigration = false;

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
        } else if (type.equals("sqlite")) {
            // 後方互換性: Configで明示的に sqlite が指定されている場合
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + oldSqliteFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
        } else {
            // H2 Database (PostgreSQL互換モード)
            File dbFile = new File(plugin.getDataFolder(), "database");
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

            File h2File = new File(plugin.getDataFolder(), "database.mv.db");
            if (oldSqliteFile.exists() && !h2File.exists()) {
                doMigration = true; // 初回起動時のみマイグレーションを実行
            }

            hikariConfig.setJdbcUrl("jdbc:h2:" + dbFile.getAbsolutePath() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
            hikariConfig.setDriverClassName("org.h2.Driver");
        }

        hikariConfig.setPoolName("Deepwither-Pool");
        hikariConfig.setMaximumPoolSize(config.getInt("database.pool.maximum-pool-size", 10));
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
                stmt.execute("PRAGMA synchronous=NORMAL;");
            }
        }

        // 全てのテーブルをここで一括初期化
        setupTables();

        if (doMigration) {
            migrateFromSQLite(oldSqliteFile);
        }
    }

    /**
     * SQLiteデータベースからH2データベースへデータを自動移行します。
     */
    private void migrateFromSQLite(File sqliteFile) {
        plugin.getLogger().info("Migrating data from SQLite to H2 database...");
        String sqliteUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
        try (Connection h2Conn = dataSource.getConnection();
             Connection sqliteConn = DriverManager.getConnection(sqliteUrl)) {

            String[] tables = {
                "player_attributes", "player_levels", "player_skilltree", "generic_configs",
                "player_daily_tasks", "player_quests", "player_mailbox", "player_professions",
                "player_boosters", "market_listings", "market_earnings", "clans", "clan_members",
                "player_fast_travel"
            };

            for (String table : tables) {
                try (Statement stmt = sqliteConn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM " + table)) {

                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    StringBuilder insertSql = new StringBuilder("INSERT INTO ").append(table).append(" VALUES (");
                    for (int i = 0; i < colCount; i++) {
                        insertSql.append("?");
                        if (i < colCount - 1) insertSql.append(", ");
                    }
                    insertSql.append(")"); // 新規構築直後なので競合は発生しない前提

                    try (PreparedStatement ps = h2Conn.prepareStatement(insertSql.toString())) {
                        int count = 0;
                        while (rs.next()) {
                            for (int i = 1; i <= colCount; i++) {
                                ps.setObject(i, rs.getObject(i));
                            }
                            ps.addBatch();
                            count++;

                            if (count % 500 == 0) { // メモリ節約のため定期的にバッチ実行
                                ps.executeBatch();
                            }
                        }
                        ps.executeBatch();
                        plugin.getLogger().info("  -> Migrated " + count + " records for table '" + table + "'");
                    }
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to migrate table " + table + " (it may be empty or missing): " + e.getMessage());
                }
            }
            plugin.getLogger().info("Migration completed successfully!");

            // 二重移行を防ぐためリネーム
            File backupFile = new File(sqliteFile.getParentFile(), sqliteFile.getName() + ".backup");
            if (sqliteFile.renameTo(backupFile)) {
                plugin.getLogger().info("Backed up old SQLite database to " + backupFile.getName());
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to SQLite for migration: " + e.getMessage());
        }
    }

    private void setupTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            // AttributeManager用
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_attributes (
                    uuid TEXT PRIMARY KEY, total_points INTEGER,
                    str INTEGER, vit INTEGER, mnd INTEGER, "int" INTEGER, agi INTEGER
                )""");

            // LevelManager用
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_levels (
                    uuid TEXT PRIMARY KEY, "level" INTEGER, exp REAL
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

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_mailbox (
                    mail_id TEXT PRIMARY KEY,
                    recipient_uuid TEXT NOT NULL,
                    mail_json TEXT NOT NULL
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

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_fast_travel (
                    uuid TEXT NOT NULL,
                    point_id TEXT NOT NULL,
                    world TEXT,
                    x REAL,
                    y REAL,
                    z REAL,
                    yaw REAL,
                    pitch REAL,
                    PRIMARY KEY (uuid, point_id)
                )""");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // --- 高レベル API 実装 ---

    @Override
    public int execute(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL Execution Error: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public <T> java.util.Optional<T> querySingle(String sql, com.lunar_prototype.deepwither.api.database.RowMapper<T> mapper, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return java.util.Optional.ofNullable(mapper.map(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL Query Error: " + e.getMessage());
        }
        return java.util.Optional.empty();
    }

    @Override
    public <T> java.util.List<T> queryList(String sql, com.lunar_prototype.deepwither.api.database.RowMapper<T> mapper, Object... params) {
        java.util.List<T> result = new java.util.ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    T obj = mapper.map(rs);
                    if (obj != null) result.add(obj);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL List Query Error: " + e.getMessage());
        }
        return result;
    }

    private void setParameters(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
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
        try (Connection connection = getConnection()) {
            // 存在チェック
            boolean exists = false;
            try (PreparedStatement checkPs = connection.prepareStatement("SELECT 1 FROM generic_configs WHERE config_key = ?")) {
                checkPs.setString(1, key);
                try (ResultSet rs = checkPs.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                // UPDATE
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE generic_configs SET config_value = ? WHERE config_key = ?")) {
                    ps.setString(1, json);
                    ps.setString(2, key);
                    ps.executeUpdate();
                }
            } else {
                // INSERT
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO generic_configs (config_key, config_value) VALUES (?, ?)")) {
                    ps.setString(1, key);
                    ps.setString(2, json);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    if (e.getSQLState().startsWith("23")) {
                        try (PreparedStatement ps = connection.prepareStatement(
                                "UPDATE generic_configs SET config_value = ? WHERE config_key = ?")) {
                            ps.setString(1, json);
                            ps.setString(2, key);
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
