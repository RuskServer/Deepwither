package com.lunar_prototype.deepwither;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.core.CacheManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@DependsOn({DatabaseManager.class, CacheManager.class})
public class SkilltreeManager implements IManager {
    
    private final Gson gson = new Gson();
    private File treeFile;
    private final JavaPlugin plugin;
    private YamlConfiguration treeConfig;
    private final DatabaseManager db;

    public SkilltreeManager(DatabaseManager db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
    }

    @Override
    public void init() {
        treeFile = new File(plugin.getDataFolder(), "tree.yaml");
        if (!treeFile.exists()) {
            treeFile.getParentFile().mkdirs();
            try {
                treeFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        treeConfig = YamlConfiguration.loadConfiguration(treeFile);
    }

    public void unload(UUID uuid) {
        DW.cache().getCache(uuid).remove(SkillData.class);
    }

    public SkillData load(UUID uuid) {
        SkillData cached = DW.cache().getCache(uuid).get(SkillData.class);
        if (cached != null) {
            return cached;
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT skill_point, skills FROM player_skilltree WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                SkillData data;
                if (rs.next()) {
                    int skillPoint = rs.getInt("skill_point");
                    String skillsJson = rs.getString("skills");
                    Map<String, Integer> skillsMap = new HashMap<>();

                    if (skillsJson != null && !skillsJson.isEmpty()) {
                        skillsMap = gson.fromJson(skillsJson, new TypeToken<Map<String, Integer>>(){}.getType());
                    }

                    data = new SkillData(skillPoint, skillsMap);
                } else {
                    data = new SkillData(1, new HashMap<>());
                }
                data.recalculatePassiveStats(treeConfig);
                DW.cache().getCache(uuid).set(SkillData.class, data);
                return data;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        SkillData newData = new SkillData(1, new HashMap<>());
        newData.recalculatePassiveStats(treeConfig);
        DW.cache().getCache(uuid).set(SkillData.class, newData);
        return newData;
    }

    public void save(UUID uuid, SkillData data) {
        data.recalculatePassiveStats(treeConfig);
        String skillsJson = gson.toJson(data.getSkills());
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO player_skilltree (uuid, skill_point, skills)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                skill_point = excluded.skill_point,
                skills = excluded.skills
            """)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, data.getSkillPoint());
            ps.setString(3, skillsJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * プレイヤーのスキルツリーをリセットし、消費した全スキルポイントを返却します。
     * @param uuid プレイヤーのUUID
     * @return プレイヤーに返却された合計スキルポイント
     */
    public int resetSkillTree(UUID uuid) {
        SkillData data = load(uuid);
        if (data == null) {
            return 0;
        }

        int totalSpentPoints = 0;
        Map<String, Integer> learnedSkills = data.getSkills();

        // 1. 消費した合計ポイントの計算
        for (Map.Entry<String, Integer> entry : learnedSkills.entrySet()) {
            String nodeId = entry.getKey();
            int skillLevel = entry.getValue();

            int baseCost = findNodeCost(nodeId, treeConfig);
            // ノードのコスト (常に 1) * 現在のスキルレベルを合計ポイントに加算
            totalSpentPoints += baseCost * skillLevel;
        }

        // 2. スキルポイントの返却
        int currentPoints = data.getSkillPoint();
        data.setSkillPoint(currentPoints + totalSpentPoints);

        // 3. 習得スキルのリセット
        learnedSkills.clear();

        // 4. パッシブステータスの再計算とデータの保存
        data.recalculatePassiveStats(treeConfig);
        save(uuid, data);

        // 5. 返却されたポイントを返す
        return totalSpentPoints;
    }

    /**
     * 指定されたノードIDの基本コストをスキルツリー設定から検索します。
     * ノードにコストフィールドが存在しないという要件に基づき、
     * 常に 1 レベルあたり 1 ポイントのコストを返します。
     */
    private int findNodeCost(String nodeId, YamlConfiguration treeConfig) {
        // ★修正点: YAML設定を無視し、常に1ポイントを返す
        return 1;
    }

    public Map<String, Object> getNodeById(String treeId, String nodeId) {
        List<Map<?, ?>> trees = treeConfig.getMapList("trees");
        System.out.println("[DEBUG] Searching for tree ID: " + treeId + ", node ID: " + nodeId);

        for (Map<?, ?> tree : trees) {
            System.out.println("[DEBUG] Checking tree ID: " + tree.get("id"));

            if (treeId.equals(tree.get("id"))) {
                System.out.println("[DEBUG] Tree matched!");

                Map<String, Object> starter = (Map<String, Object>) tree.get("starter");
                if (starter != null) {
                    System.out.println("[DEBUG] Starter ID: " + starter.get("id"));
                    if (nodeId.equals(starter.get("id"))) {
                        System.out.println("[DEBUG] Matched starter node");
                        return starter;
                    }
                }

                List<Map<String, Object>> nodes = (List<Map<String, Object>>) tree.get("nodes");
                if (nodes != null) {
                    for (Map<String, Object> node : nodes) {
                        System.out.println("[DEBUG] Checking node ID: " + node.get("id"));
                        if (nodeId.equals(node.get("id"))) {
                            System.out.println("[DEBUG] Matched regular node");
                            return node;
                        }
                    }
                }

                System.out.println("[DEBUG] Node not found in this tree.");
            }
        }

        System.out.println("[DEBUG] Tree not found.");
        return null;
    }

}
