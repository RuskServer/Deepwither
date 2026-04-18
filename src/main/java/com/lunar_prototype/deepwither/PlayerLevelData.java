package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.playerdata.IPlayerComponent;

public class PlayerLevelData implements IPlayerComponent {
    private int level;
    private double exp;

    // MMOCore互換の経験値テーブル
    // 全体的に必要経験値を引き上げ、序盤の進行速度を抑制したテーブル
    private static final int[] EXP_TABLE = {
            // Lv1 - 10 (雑魚10匹〜30匹ペース)
            500, 900, 1400, 2000, 2700, 3500, 4400, 5400, 6500, 7800,
            // Lv11 - 20 (ここから1レベルあたり雑魚50匹〜ペース)
            9200, 10700, 12300, 14000, 15800, 17700, 19700, 21800, 24000, 26500,
            // Lv21 - 30 (中盤：ダンジョン周回前提)
            29000, 31800, 34800, 38000, 41400, 45000, 48800, 52800, 57000, 61500,
            // Lv31 - 40
            66500, 72000, 78000, 84500, 91500, 99000, 107000, 115500, 124500, 134000,
            // Lv41 - 50 (かなりマゾくなってくる)
            144000, 155000, 167000, 180000, 194000, 209000, 225000, 242000, 260000, 280000,
            // Lv51 - 60
            305000, 335000, 370000, 410000, 455000, 505000, 560000, 620000, 685000, 755000,
            // Lv61 - 70 (上位コンテンツ向け)
            830000, 910000, 1000000, 1100000, 1210000, 1330000, 1460000, 1600000, 1750000, 1910000,
            // Lv71 - 80
            2100000, 2300000, 2520000, 2760000, 3020000, 3300000, 3600000, 3920000, 4260000, 4620000,
            // Lv81 - 90
            5000000, 5400000, 5820000, 6260000, 6720000, 7200000, 7700000, 8220000, 8760000, 9320000,
            // Lv91 - 100 (カンストへの道：伝説級)
            10000000, 10800000, 11700000, 12700000, 13800000, 15000000, 16300000, 17700000, 19200000, 21000000
    };

    private static final int MAX_LEVEL = 100;

    public PlayerLevelData(int level, double exp) {
        this.level = level;
        this.exp = exp;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setExp(double exp) {
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