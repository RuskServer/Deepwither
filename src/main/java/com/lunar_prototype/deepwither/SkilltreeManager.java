package com.lunar_prototype.deepwither;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({DatabaseManager.class})
public class SkilltreeManager implements IManager {
    
    private final Gson gson = new Gson();
    private File treeFile;
    private final JavaPlugin plugin;
    private YamlConfiguration treeConfig;
    private final DatabaseManager db;
    private final Map<UUID, SkillData> cache = new HashMap<>();

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
        cache.remove(uuid);
    }

    public SkillData load(UUID uuid) {
        if (cache.containsKey(uuid)) {
            return cache.get(uuid);
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT skill_point, skills FROM player_skilltree WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int skillPoint = rs.getInt("skill_point");
                    String skillsJson = rs.getString("skills");
                    Map<String, Integer> skillsMap = new HashMap<>();

                    if (skillsJson != null && !skillsJson.isEmpty()) {
                        skillsMap = gson.fromJson(skillsJson, new TypeToken<Map<String, Integer>>(){}.getType());
                    }

                    SkillData data = new SkillData(skillPoint, skillsMap);
                    data.recalculatePassiveStats(treeConfig);
                    cache.put(uuid, data);
                    return data;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        SkillData newData = new SkillData(1, new HashMap<>());
        newData.recalculatePassiveStats(treeConfig);
        cache.put(uuid, newData);
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

    public static class SkillData {
        private int skillPoint;
        private Map<String, Integer> skills;
        private StatMap passiveStats = new StatMap();
        private Map<String, Double> specialEffects = new HashMap<>();

        public SkillData(int skillPoint, Map<String, Integer> skills) {
            this.skillPoint = skillPoint;
            this.skills = skills;
        }

        public int getSkillPoint() {
            return skillPoint;
        }

        public void setSkillPoint(int skillPoint) {
            this.skillPoint = skillPoint;
        }

        public Map<String, Integer> getSkills() {
            return skills;
        }

        public void setSkills(Map<String, Integer> skills) {
            this.skills = skills;
        }

        public boolean hasSkill(String id) {
            return skills.containsKey(id);
        }

        public int getSkillLevel(String id) {
            return skills.getOrDefault(id, 0);
        }

        public boolean canLevelUp(String id, int maxLevel) {
            return getSkillLevel(id) < maxLevel;
        }

        public void unlock(String id) {
            skills.put(id, getSkillLevel(id) + 1);
        }

        public StatMap getPassiveStats() {
            return passiveStats;
        }

        public double getSpecialEffectValue(String effectId) {
            return specialEffects.getOrDefault(effectId, 0.0);
        }

        public boolean hasSpecialEffect(String effectId) {
            return specialEffects.containsKey(effectId);
        }

        /**
         * スキルから得られるバフ（passiveStats）と特殊効果（specialEffects）を再計算
         */
        public void recalculatePassiveStats(YamlConfiguration treeConfig) {
            passiveStats = new StatMap();
            specialEffects = new HashMap<>();

            List<Map<?, ?>> trees = treeConfig.getMapList("trees");
            for (Map<?, ?> tree : trees) {
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) tree.get("nodes");
                if (nodes == null) continue;

                for (Map<String, Object> node : nodes) {
                    String nodeId = (String) node.get("id");
                    if (nodeId == null || !hasSkill(nodeId)) continue;

                    int level = getSkillLevel(nodeId);

                    // バフノードの処理
                    if ("buff".equals(node.get("type"))) {
                        List<Map<?, ?>> buffStats = (List<Map<?, ?>>) node.get("stats");
                        if (buffStats != null) {
                            for (Map<?, ?> statEntry : buffStats) {
                                String statKey = (String) statEntry.get("stat");
                                if (statKey == null) continue;
                                double baseValue = ((Number) statEntry.get("value")).doubleValue();
                                double totalValue = baseValue * level;

                                try {
                                    StatType statType = StatType.valueOf(statKey.toUpperCase());
                                    passiveStats.addFlat(statType, totalValue);
                                } catch (IllegalArgumentException e) {
                                    Deepwither.getInstance().getLogger().warning("Invalid StatType '" + statKey + "' in skill node: " + nodeId);
                                }
                            }
                        }
                    }

                    // 特殊パッシブノードの処理
                    if ("special_passive".equals(node.get("type"))) {
                        Map<?, ?> effect = (Map<?, ?>) node.get("effect");
                        if (effect != null) {
                            String effectId = (String) effect.get("id");
                            if (effectId != null) {
                                double baseValue = ((Number) effect.get("value")).doubleValue();
                                specialEffects.put(effectId, baseValue * level);
                            }
                        }
                    }
                }
            }
        }
    }
}
