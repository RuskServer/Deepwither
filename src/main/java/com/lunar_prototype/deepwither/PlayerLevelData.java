package com.lunar_prototype.deepwither;

public class PlayerLevelData {
    private int level;
    private double exp;

    // MMOCore互換の経験値テーブル
    private static final int[] EXP_TABLE = {
            600, 800, 1000, 1200, 1400, 1600, 1800, 2000, 2200, 3400,
            3700, 4000, 4300, 4600, 4900, 5200, 5500, 5800, 6100, 8400,
            8800, 9200, 9600, 10000, 10400, 10800, 11200, 11600, 12000, 15400,
            15900, 16400, 16900, 17400, 17900, 18400, 18900, 19400, 19900, 24400,
            25000, 25600, 26200, 26800, 27400, 28000, 28600, 29200, 29800, 40400,
            41200, 42000, 42800, 43600, 44400, 45200, 46000, 46800, 47600, 60400,
            61400, 62400, 63400, 64400, 65400, 66400, 67400, 68400, 69400, 105400,
            106900, 108400, 109900, 111400, 112900, 114400, 115900, 117400, 118900, 160400,
            162400, 164400, 166400, 168400, 170400, 172400, 174400, 176400, 178400, 450400,
            455400, 460400, 465400, 470400, 475400, 480400, 485400, 490400, 495400, 500400
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