package com.lunar_prototype.deepwither;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class SkilltreeManager {

    private final Connection connection;
    private final Gson gson = new Gson();
    private final File treeFile;
    private final JavaPlugin plugin;
    private final YamlConfiguration treeConfig;

    public SkilltreeManager(File dbFile, JavaPlugin plugin) throws SQLException {
        this.plugin = plugin;
        this.treeFile = new File(plugin.getDataFolder(), "tree.yaml");
        if (!treeFile.exists()) {
            treeFile.getParentFile().mkdirs();
            try {
                treeFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        this.treeConfig = YamlConfiguration.loadConfiguration(treeFile);

        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_skilltree (
                    uuid TEXT PRIMARY KEY,
                    skill_point INTEGER,
                    skills TEXT
                )
            """);
        }
    }

    public SkillData load(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT skill_point, skills FROM player_skilltree WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int skillPoint = rs.getInt("skill_point");
                String skillsJson = rs.getString("skills");
                Map<String, Integer> skillsMap = new HashMap<>();

                if (skillsJson != null && !skillsJson.isEmpty()) {
                    // JSONをMap<String,Integer>に変換
                    skillsMap = gson.fromJson(skillsJson, new TypeToken<Map<String, Integer>>(){}.getType());
                }

                SkillData data = new SkillData(skillPoint,skillsMap);

                // 【✅ 追加】ロード直後に再計算
                data.recalculatePassiveStats(treeConfig);
                return data;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // データがない場合は空のSkillDataを返す
        // 新規データの場合
        SkillData newData = new SkillData(1, new HashMap<>());
        // 【✅ 追加】新規作成時にも再計算（StatMapを初期化するため）
        newData.recalculatePassiveStats(treeConfig);
        return newData;
    }

    public void save(UUID uuid, SkillData data) {
        data.recalculatePassiveStats(treeConfig);
        String skillsJson = gson.toJson(data.getSkills());
        try (PreparedStatement ps = connection.prepareStatement("""
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

    // SkillData クラスの例
    public static class SkillData {
        private int skillPoint;
        private Map<String, Integer> skills;
        private StatMap passiveStats = new StatMap(); // ← 追加: バフ合計保持

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

        /**
         * スキルから得られるバフ（passiveStats）を再計算
         *
         * @param treeConfig スキルツリーのYAML設定（YamlConfiguration）
         */
        public void recalculatePassiveStats(YamlConfiguration treeConfig) {
            // StatMapの初期化
            passiveStats = new StatMap();

            // 全ツリーを走査
            List<Map<?, ?>> trees = treeConfig.getMapList("trees");
            for (Map<?, ?> tree : trees) {
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) tree.get("nodes");
                if (nodes == null) continue;

                // 全ノードを走査
                for (Map<String, Object> node : nodes) {
                    String nodeId = (String) node.get("id");
                    if (nodeId == null || !hasSkill(nodeId)) continue; // 習得していないノードはスキップ

                    // バフノードのみを処理
                    if ("buff".equals(node.get("type"))) {
                        int level = getSkillLevel(nodeId);

                        // 【✅ 変更点】: 新しい "stats" フィールドを取得
                        List<Map<?, ?>> buffStats = (List<Map<?, ?>>) node.get("stats");
                        if (buffStats == null) continue;

                        // ノードが持つ複数のステータスをループ処理
                        for (Map<?, ?> statEntry : buffStats) {
                            String statKey = (String) statEntry.get("stat");
                            if (statKey == null) continue;

                            // 値を取得し、levelに応じて計算 (ここでは単純に level * value とします)
                            double baseValue = 0;
                            baseValue = ((Number) statEntry.get("value")).doubleValue();
                            double totalValue = baseValue * level;

                            try {
                                StatType statType = StatType.valueOf(statKey.toUpperCase());

                                // passiveStats に値を加算
                                passiveStats.setFlat(statType, totalValue); // addFlat を使用して加算
                            } catch (IllegalArgumentException e) {
                                // StatType.valueOfに失敗した場合の処理（statKeyが不正）
                                Deepwither.getInstance().getLogger().warning("Invalid StatType '" + statKey + "' in skill node: " + nodeId);
                            }
                        }
                    }
                }
            }
        }
    }
}
