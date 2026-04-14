package com.lunar_prototype.deepwither.profession;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

@DependsOn({DatabaseManager.class})
public class ProfessionDatabase implements IManager {

    private final Deepwither plugin;
    private final DatabaseManager db;

    public ProfessionDatabase(Deepwither plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public void init() throws Exception {}

    @Override
    public void shutdown() {}

    /**
     * プレイヤーの職業データをロード
     */
    public PlayerProfessionData loadPlayer(UUID playerId) {
        PlayerProfessionData data = new PlayerProfessionData(playerId);
        String query = "SELECT profession_type, experience FROM player_professions WHERE player_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
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
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false); // トランザクション開始
            try {
                String checkQuery = "SELECT 1 FROM player_professions WHERE player_id = ? AND profession_type = ?";
                String updateQuery = "UPDATE player_professions SET experience = ? WHERE player_id = ? AND profession_type = ?";
                String insertQuery = "INSERT INTO player_professions (player_id, profession_type, experience) VALUES (?, ?, ?)";

                try (PreparedStatement checkPs = conn.prepareStatement(checkQuery);
                     PreparedStatement updatePs = conn.prepareStatement(updateQuery);
                     PreparedStatement insertPs = conn.prepareStatement(insertQuery)) {

                    for (Map.Entry<ProfessionType, Long> entry : data.getAllExperience().entrySet()) {
                        String uuidStr = data.getPlayerId().toString();
                        String profType = entry.getKey().name();
                        long exp = entry.getValue();

                        // 存在チェック
                        checkPs.setString(1, uuidStr);
                        checkPs.setString(2, profType);
                        boolean exists = false;
                        try (ResultSet rs = checkPs.executeQuery()) {
                            exists = rs.next();
                        }

                        if (exists) {
                            // UPDATE
                            updatePs.setLong(1, exp);
                            updatePs.setString(2, uuidStr);
                            updatePs.setString(3, profType);
                            updatePs.executeUpdate();
                        } else {
                            // INSERT
                            insertPs.setString(1, uuidStr);
                            insertPs.setString(2, profType);
                            insertPs.setLong(3, exp);
                            insertPs.executeUpdate();
                        }
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save profession data for " + data.getPlayerId(), e);
        }
    }
}
