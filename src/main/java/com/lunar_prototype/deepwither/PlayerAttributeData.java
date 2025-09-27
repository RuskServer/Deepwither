package com.lunar_prototype.deepwither;

import java.util.EnumMap;

public class PlayerAttributeData {
    private int totalPoints;
    private final EnumMap<StatType, Integer> allocated;

    public PlayerAttributeData(int totalPoints) {
        this.totalPoints = totalPoints;
        this.allocated = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            allocated.put(type, 0);
        }
    }

    public PlayerAttributeData(int totalPoints, EnumMap<StatType, Integer> allocated) {
        this.totalPoints = totalPoints;
        this.allocated = allocated;
    }

    public int getAllocated(StatType type) {
        return allocated.getOrDefault(type, 0);
    }

    public void addPoint(StatType type) {
        if (totalPoints <= 0) return;
        allocated.put(type, getAllocated(type) + 1);
        totalPoints--;
    }

    public void addPoints(int amount) {
        totalPoints += amount;
    }

    public int getRemainingPoints() {
        return totalPoints;
    }

    public void setAllocated(StatType type, int value) {
        allocated.put(type, value);
    }

    public EnumMap<StatType, Integer> getAllAllocated() {
        return allocated;
    }
}

