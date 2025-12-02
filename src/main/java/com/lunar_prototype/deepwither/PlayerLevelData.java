package com.lunar_prototype.deepwither;

public class PlayerLevelData {
    private int level;
    private double exp;

    // MMOCore互換の経験値テーブル
    private static final int[] EXP_TABLE = {
            1000, 1200, 1400, 1600, 1800, 2000, 2200, 2400, 2600, 3800,
            4100, 4400, 4700, 5000, 5300, 5600, 5900, 6200, 6500, 8800,
            9200, 9600, 10000, 10400, 10800, 11200, 11600, 12000, 12400, 15800,
            16300, 16800, 17300, 17800, 18300, 18800, 19300, 19800, 20300, 24800,
            25400, 26000, 26600, 27200, 27800, 28400, 29000, 29600, 30200, 40800,
            41600, 42400, 43200, 44000, 44800, 45600, 46400, 47200, 48000, 60800,
            61800, 62800, 63800, 64800, 65800, 66800, 67800, 68800, 69800, 105800,
            107300, 108800, 110300, 111800, 113300, 114800, 116300, 117800, 119300, 160800,
            162800, 164800, 166800, 168800, 170800, 172800, 174800, 176800, 178800, 450800,
            455800, 460800, 465800, 470800, 475800, 480800, 485800, 490800, 495800, 500800
    };

    private static final int MAX_LEVEL = 100;

    public PlayerLevelData(int level, double exp) {
        this.level = level;
        this.exp = exp;
    }

    public int getLevel() {
        return level;
    }

    public double getExp() {
        return exp;
    }

    public void addExp(double amount) {
        exp += amount;
        while (level < MAX_LEVEL && exp >= getRequiredExp()) {
            exp -= getRequiredExp();
            level++;
        }

        // 上限に達したら余剰経験値は捨てる
        if (level >= MAX_LEVEL) {
            level = MAX_LEVEL;
            exp = 0;
        }
    }

    public double getRequiredExp() {
        if (level < 1) return EXP_TABLE[0];
        if (level > EXP_TABLE.length) return EXP_TABLE[EXP_TABLE.length - 1];
        return EXP_TABLE[level - 1];
    }
}