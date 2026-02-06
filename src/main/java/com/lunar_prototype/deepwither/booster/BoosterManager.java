package com.lunar_prototype.deepwither.booster;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.entity.Player;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@DependsOn({DatabaseManager.class})
public class BoosterManager implements IManager {
    private final DatabaseManager db;
    private final ConcurrentHashMap<UUID, BoosterData> activeBoosters = new ConcurrentHashMap<>();

    public BoosterManager(DatabaseManager db) {
        this.db = db;
    }

    public static class BoosterData {
        public double multiplier;
        public long endTime;

        public BoosterData(double multiplier, long endTime, boolean isAbsolute) {
            this.multiplier = multiplier;
            this.endTime = isAbsolute ? endTime : System.currentTimeMillis() + endTime;
        }
    }

    @Override
    public void init() throws SQLException {
        // データベースから有効なブースターをすべてロード
        String query = "SELECT * FROM player_boosters";
        try (PreparedStatement ps = db.getConnection().prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            long now = System.currentTimeMillis();
            while (rs.next()) {
                long endTime = rs.getLong("end_time");
                if (endTime > now) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    double multiplier = rs.getDouble("multiplier");
                    activeBoosters.put(uuid, new BoosterData(multiplier, endTime, true));
                }
            }
        }
    }

    @Override
    public void shutdown() {
        // 停止時にすべてのブースターを保存
        activeBoosters.forEach((uuid, data) -> save(uuid, data));
    }

    private void save(UUID uuid, BoosterData data) {
        String query = "INSERT OR REPLACE INTO player_boosters (uuid, multiplier, end_time) VALUES (?, ?, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, data.multiplier);
            ps.setLong(3, data.endTime);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addBooster(Player player, double multiplier, int minutes) {
        UUID uuid = player.getUniqueId();
        BoosterData data = new BoosterData(multiplier, (long) minutes * 60 * 1000, false);
        activeBoosters.put(uuid, data);
        // 即座にDBにも反映（不慮のクラッシュ対策）
        save(uuid, data);
    }

    public double getMultiplier(Player player) {
        UUID uuid = player.getUniqueId();
        BoosterData data = activeBoosters.get(uuid);
        if (data == null) return 1.0;

        if (System.currentTimeMillis() > data.endTime) {
            activeBoosters.remove(uuid);
            // 期限切れなのでDBからも削除
            removeFromDb(uuid);
            return 1.0;
        }
        return data.multiplier;
    }

    private void removeFromDb(UUID uuid) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM player_boosters WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public long getRemainingSeconds(Player player) {
        BoosterData data = activeBoosters.get(player.getUniqueId());
        if (data == null) return 0;
        return Math.max(0, (data.endTime - System.currentTimeMillis()) / 1000);
    }
}