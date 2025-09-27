package com.lunar_prototype.deepwither;

import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LevelManager {
    private static final int MAX_LEVEL = 100;

    private final Map<UUID, PlayerLevelData> dataMap = new HashMap<>();
    private final Connection connection;

    public LevelManager(File dbFile) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_levels (
                    uuid TEXT PRIMARY KEY,
                    level INTEGER,
                    exp REAL
                )
            """);
        }
    }

    public void load(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT level, exp FROM player_levels WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int level = Math.min(rs.getInt("level"), MAX_LEVEL);
                double exp = rs.getDouble("exp");
                dataMap.put(uuid, new PlayerLevelData(level, exp));
            } else {
                dataMap.put(uuid, new PlayerLevelData(1, 0));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void save(UUID uuid) {
        PlayerLevelData data = dataMap.get(uuid);
        if (data == null) return;

        int level = Math.min(data.getLevel(), MAX_LEVEL);
        double exp = (level >= MAX_LEVEL) ? 0 : data.getExp(); // 上限ならEXPを0に

        try (PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO player_levels (uuid, level, exp) VALUES (?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET level = excluded.level, exp = excluded.exp
        """)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, level);
            ps.setDouble(3, exp);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addExp(Player player, double amount) {
        PlayerLevelData data = dataMap.get(player.getUniqueId());
        if (data == null || data.getLevel() >= MAX_LEVEL) return;

        int before = data.getLevel();
        data.addExp(amount);
        int after = data.getLevel();

        player.sendMessage("§a+ " + amount + " EXP");

        if (after > before) {
            player.sendMessage("§6Level Up! §e" + before + " → " + after);
        }

        if (after >= MAX_LEVEL) {
            player.sendMessage("§b最大レベルに到達しました！");
        }
    }

    public PlayerLevelData get(Player player) {
        return dataMap.get(player.getUniqueId());
    }

    public void unload(UUID uuid) {
        save(uuid);
        dataMap.remove(uuid);
    }
}