package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.core.CacheManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({DatabaseManager.class, CacheManager.class})
public class AttributeManager implements IManager {

    private static final int MAX_PER_STAT = 50;

    private final DatabaseManager db;

    public AttributeManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void init() {
    }

    public void load(UUID uuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM player_attributes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                PlayerAttributeData data;
                if (rs.next()) {
                    int total = rs.getInt("total_points");
                    EnumMap<StatType, Integer> map = new EnumMap<>(StatType.class);
                    map.put(StatType.STR, rs.getInt("str"));
                    map.put(StatType.VIT, rs.getInt("vit"));
                    map.put(StatType.MND, rs.getInt("mnd"));
                    map.put(StatType.INT, rs.getInt("int"));
                    map.put(StatType.AGI, rs.getInt("agi"));

                    data = new PlayerAttributeData(total, map);
                } else {
                    data = new PlayerAttributeData(0); // 初期値
                }
                DW.cache().getCache(uuid).set(PlayerAttributeData.class, data);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void save(UUID uuid) {
        PlayerAttributeData data = get(uuid);
        if (data == null) return;

        try (Connection conn = db.getConnection()) {
            // 存在チェック
            boolean exists = false;
            try (PreparedStatement checkPs = conn.prepareStatement("SELECT 1 FROM player_attributes WHERE uuid = ?")) {
                checkPs.setString(1, uuid.toString());
                try (ResultSet rs = checkPs.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                // UPDATE
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE player_attributes SET total_points = ?, str = ?, vit = ?, mnd = ?, \"int\" = ?, agi = ? WHERE uuid = ?")) {
                    ps.setInt(1, data.getRemainingPoints());
                    ps.setInt(2, data.getAllocated(StatType.STR));
                    ps.setInt(3, data.getAllocated(StatType.VIT));
                    ps.setInt(4, data.getAllocated(StatType.MND));
                    ps.setInt(5, data.getAllocated(StatType.INT));
                    ps.setInt(6, data.getAllocated(StatType.AGI));
                    ps.setString(7, uuid.toString());
                    ps.executeUpdate();
                }
            } else {
                // INSERT
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO player_attributes (uuid, total_points, str, vit, mnd, \"int\", agi) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, data.getRemainingPoints());
                    ps.setInt(3, data.getAllocated(StatType.STR));
                    ps.setInt(4, data.getAllocated(StatType.VIT));
                    ps.setInt(5, data.getAllocated(StatType.MND));
                    ps.setInt(6, data.getAllocated(StatType.INT));
                    ps.setInt(7, data.getAllocated(StatType.AGI));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    if (e.getSQLState().startsWith("23")) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE player_attributes SET total_points = ?, str = ?, vit = ?, mnd = ?, \"int\" = ?, agi = ? WHERE uuid = ?")) {
                            ps.setInt(1, data.getRemainingPoints());
                            ps.setInt(2, data.getAllocated(StatType.STR));
                            ps.setInt(3, data.getAllocated(StatType.VIT));
                            ps.setInt(4, data.getAllocated(StatType.MND));
                            ps.setInt(5, data.getAllocated(StatType.INT));
                            ps.setInt(6, data.getAllocated(StatType.AGI));
                            ps.setString(7, uuid.toString());
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

    public PlayerAttributeData get(UUID uuid) {
        return DW.cache().getCache(uuid).get(PlayerAttributeData.class);
    }

    public void unload(UUID uuid) {
        save(uuid);
        DW.cache().getCache(uuid).remove(PlayerAttributeData.class);
    }

    public void addPoint(UUID uuid, StatType type) {
        PlayerAttributeData data = get(uuid);
        if (data == null || data.getRemainingPoints() <= 0) return;

        int current = data.getAllocated(type);
        // 修正: 明示的に現在のデータを渡して上限を計算
        if (current >= getMaxAllocatable(data, type)) return;

        data.addPoint(type);
        
        // 追加後に他ステータスの上限超過をチェックし、還元する
        validateAndRefundExcessPoints(uuid, data);

        // プレイヤーのステータスを即座に同期
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            DW.get(StatManager.class).updatePlayerStats(player);
        }
    }

    /**
     * 上限を超えたステータスをチェックし、超過分をポイントとして還元する
     */
    private void validateAndRefundExcessPoints(UUID uuid, PlayerAttributeData data) {
        boolean changed = false;
        for (StatType type : new StatType[]{StatType.STR, StatType.VIT, StatType.MND, StatType.INT, StatType.AGI}) {
            int current = data.getAllocated(type);
            // 修正: 明示的に現在のデータを渡して上限を計算
            int max = getMaxAllocatable(data, type);
            
            if (current > max) {
                int excess = current - max;
                data.setAllocated(type, max);
                data.addPoints(excess);
                changed = true;
            }
        }
        
        if (changed) {
            save(uuid);
            // プレイヤーがオンラインならステータスを即座に再計算して同期する
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                DW.get(StatManager.class).updatePlayerStats(player);
            }
        }
    }

    public void givePoints(UUID uuid, int amount) {
        PlayerAttributeData data = get(uuid);
        if (data != null) {
            data.addPoints(amount);
        }
    }

    public int getMaxAllocatable(UUID uuid, StatType target) {
        return getMaxAllocatable(get(uuid), target);
    }

    /**
     * トレードオフを含めた最大割り振り可能値を取得
     */
    public int getMaxAllocatable(PlayerAttributeData data, StatType target) {
        if (data == null) return MAX_PER_STAT;

        int penalty = 0;

        switch (target) {
            case AGI -> {
                int vit = data.getAllocated(StatType.VIT);
                penalty = (int) Math.floor(vit * 0.5);
            }
            case VIT -> {
                int agi = data.getAllocated(StatType.AGI);
                penalty = (int) Math.floor(agi * 0.5);
            }
            case STR -> {
                int agi = data.getAllocated(StatType.AGI);
                penalty = (int) Math.floor(agi * 0.5);
            }
            case MND -> {
                int intelligence = data.getAllocated(StatType.INT);
                penalty = (int) Math.floor(intelligence * 0.5);
            }
            case INT -> {
                int mnd = data.getAllocated(StatType.MND);
                penalty = (int) Math.floor(mnd * 0.5);
            }
            default -> penalty = 0;
        }

        int effectiveMax = MAX_PER_STAT - penalty;
        return Math.max(0, effectiveMax);
    }
}