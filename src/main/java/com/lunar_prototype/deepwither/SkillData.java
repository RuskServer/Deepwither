package com.lunar_prototype.deepwither;

import org.bukkit.configuration.file.YamlConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkillData {
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

        if (treeConfig == null) return;

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
