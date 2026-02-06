package com.lunar_prototype.deepwither.profession;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({DatabaseManager.class})
public class ProfessionDatabase implements IManager {

    private final JavaPlugin plugin;
    private final DatabaseManager db;

    public ProfessionDatabase(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public void init() {
        // テーブル作成は DatabaseManager で行うため、ここでは特になし
        // 必要ならキャッシュの構築などをここで行う
    }

    /**
     * プレイヤーのデータをロード
     */
    public PlayerProfessionData loadPlayer(UUID playerId) {
        PlayerProfessionData data = new PlayerProfessionData(playerId);
        String query = "SELECT profession_type, experience FROM player_professions WHERE player_id = ?";

        // connection ではなく db.getConnection() を使用
        try (PreparedStatement ps = db.getConnection().prepareStatement(query)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        ProfessionType type = ProfessionType.valueOf(rs.getString("profession_type"));
                        long exp = rs.getLong("experience");
                        data.addExp(type, exp);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load profession data for " + playerId, e);
        }
        return data;
    }

    /**
     * プレイヤーのデータを保存
     */
    public void savePlayer(PlayerProfessionData data) {
        String query = "INSERT OR REPLACE INTO player_professions (player_id, profession_type, experience) VALUES (?, ?, ?)";
        Connection conn = db.getConnection();

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            conn.setAutoCommit(false); // トランザクション開始

            for (Map.Entry<ProfessionType, Long> entry : data.getAllExperience().entrySet()) {
                ps.setString(1, data.getPlayerId().toString());
                ps.setString(2, entry.getKey().name());
                ps.setLong(3, entry.getValue());
                ps.addBatch();
            }

            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save profession data for " + data.getPlayerId(), e);
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
        }
    }
}