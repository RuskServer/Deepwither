package com.lunar_prototype.deepwither.modules.economy.advancement;

import java.util.HashMap;
import java.util.Map;

public class PlayerAdvancementData {

    // どのモブを何体倒したか (key: mobId, value: kill count)
    private Map<String, Integer> mobKillCounts = new HashMap<>();
    
    // 現在の最大到達階層
    private int maxFloorReached = 0;

    // チュートリアルをクリアしたかどうか
    private boolean tutorialCleared = false;

    // 総討伐数
    private int totalMobKills = 0;

    public Map<String, Integer> getMobKillCounts() {
        return mobKillCounts;
    }

    public void setMobKillCounts(Map<String, Integer> mobKillCounts) {
        this.mobKillCounts = mobKillCounts;
    }

    public void addMobKill(String mobId) {
        mobKillCounts.put(mobId, mobKillCounts.getOrDefault(mobId, 0) + 1);
        totalMobKills++;
    }

    public int getMobKillCount(String mobId) {
        return mobKillCounts.getOrDefault(mobId, 0);
    }
    
    public int getTotalMobKills() {
        return totalMobKills;
    }
    
    public void setTotalMobKills(int totalMobKills) {
        this.totalMobKills = totalMobKills;
    }

    public int getMaxFloorReached() {
        return maxFloorReached;
    }

    public void setMaxFloorReached(int maxFloorReached) {
        this.maxFloorReached = maxFloorReached;
    }

    public boolean isTutorialCleared() {
        return tutorialCleared;
    }

    public void setTutorialCleared(boolean tutorialCleared) {
        this.tutorialCleared = tutorialCleared;
    }
}
