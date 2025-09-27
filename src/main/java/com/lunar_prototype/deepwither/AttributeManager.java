package com.lunar_prototype.deepwither;

import java.io.File;
import java.sql.*;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AttributeManager {

    private static final int MAX_PER_STAT = 50;

    private final Map<UUID, PlayerAttributeData> dataMap = new HashMap<>();
    private final Connection connection;

    public AttributeManager(File dbFile) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_attributes (
                    uuid TEXT PRIMARY KEY,
                    total_points INTEGER,
                    str INTEGER,
                    vit INTEGER,
                    mnd INTEGER,
                    int INTEGER,
                    agi INTEGER
                )
            """);
        }
    }

    public void load(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_attributes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int total = rs.getInt("total_points");
                EnumMap<StatType, Integer> map = new EnumMap<>(StatType.class);
                map.put(StatType.STR, rs.getInt("str"));
                map.put(StatType.VIT, rs.getInt("vit"));
                map.put(StatType.MND, rs.getInt("mnd"));
                map.put(StatType.INT, rs.getInt("int"));
                map.put(StatType.AGI, rs.getInt("agi"));

                dataMap.put(uuid, new PlayerAttributeData(total, map));
            } else {
                dataMap.put(uuid, new PlayerAttributeData(0)); // 初期値
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void save(UUID uuid) {
        PlayerAttributeData data = dataMap.get(uuid);
        if (data == null) return;

        try (PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO player_attributes (
                uuid, total_points, str, vit, mnd, int, agi
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                total_points = excluded.total_points,
                str = excluded.str,
                vit = excluded.vit,
                mnd = excluded.mnd,
                int = excluded.int,
                agi = excluded.agi
        """)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, data.getRemainingPoints());
            ps.setInt(3, data.getAllocated(StatType.STR));
            ps.setInt(4, data.getAllocated(StatType.VIT));
            ps.setInt(5, data.getAllocated(StatType.MND));
            ps.setInt(6, data.getAllocated(StatType.INT));
            ps.setInt(7, data.getAllocated(StatType.AGI));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public PlayerAttributeData get(UUID uuid) {
        return dataMap.get(uuid);
    }

    public void unload(UUID uuid) {
        save(uuid);
        dataMap.remove(uuid);
    }

    public void addPoint(UUID uuid, StatType type) {
        PlayerAttributeData data = dataMap.get(uuid);
        if (data == null || data.getRemainingPoints() <= 0) return;

        int current = data.getAllocated(type);
        if (current >= getMaxAllocatable(uuid, type)) return;

        data.addPoint(type);
    }

    public void givePoints(UUID uuid, int amount) {
        PlayerAttributeData data = dataMap.get(uuid);
        if (data != null) {
            data.addPoints(amount);
        }
    }

    /**
     * トレードオフを含めた最大割り振り可能値を取得
     */
    public int getMaxAllocatable(UUID uuid, StatType target) {
        PlayerAttributeData data = dataMap.get(uuid);
        if (data == null) return MAX_PER_STAT;

        int penalty = 0;

        switch (target) {
            case AGI -> {
                int str = data.getAllocated(StatType.STR);
                penalty = (int) Math.floor(str * 0.5);
            }
            case MND -> {
                int intelligence = data.getAllocated(StatType.INT);
                penalty = (int) Math.floor(intelligence * 0.5);
            }
            case STR -> {
                int agi = data.getAllocated(StatType.AGI);
                penalty = (int) Math.floor(agi * 0.5);
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