package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.api.playerdata.IPlayerComponent;

/**
 * プレイヤーのクラフトスキルデータ。
 * PlayerCache に保持し、DatabaseManager (player_levels テーブル) で永続化する。
 */
public class CraftingSkillData implements IPlayerComponent {

    private static final int MAX_LEVEL = 100;

    /**
     * クラフトLv別の必要経験値テーブル。
     * 1製作で 10〜15 exp 付与 → Lv1は7〜10回製作でLvUP。
     */
    private static final int[] EXP_TABLE = {
        // Lv1〜10
        100, 100, 120, 140, 160, 180, 200, 220, 240, 260,
        // Lv11〜20
        300, 320, 340, 360, 380, 400, 420, 440, 460, 480,
        // Lv21〜30
        520, 540, 560, 580, 600, 640, 680, 720, 760, 800,
        // Lv31〜40
        850, 900, 950, 1000, 1050, 1100, 1150, 1200, 1250, 1300,
        // Lv41〜50
        1400, 1500, 1600, 1700, 1800, 1900, 2000, 2100, 2200, 2300,
        // Lv51〜60
        2500, 2700, 2900, 3100, 3300, 3500, 3700, 3900, 4100, 4300,
        // Lv61〜70
        4600, 4900, 5200, 5500, 5800, 6100, 6400, 6700, 7000, 7300,
        // Lv71〜80
        7700, 8100, 8500, 8900, 9300, 9700, 10100, 10500, 10900, 11300,
        // Lv81〜90
        11800, 12300, 12800, 13300, 13800, 14400, 15000, 15600, 16200, 16800,
        // Lv91〜99 (Lv100 は上限なのでダミー)
        17500, 18200, 18900, 19600, 20300, 21000, 21700, 22400, 23100, Integer.MAX_VALUE
    };

    private int craftLevel;
    private double craftExp;

    public CraftingSkillData() {
        this.craftLevel = 1;
        this.craftExp = 0;
    }

    public CraftingSkillData(int craftLevel, double craftExp) {
        this.craftLevel = Math.min(craftLevel, MAX_LEVEL);
        this.craftExp = craftExp;
    }

    public int getCraftLevel() { return craftLevel; }
    public double getCraftExp() { return craftExp; }

    /** 次のLvに必要な経験値量 */
    public int getRequiredExp() {
        if (craftLevel <= 0) return EXP_TABLE[0];
        if (craftLevel > EXP_TABLE.length) return Integer.MAX_VALUE;
        return EXP_TABLE[craftLevel - 1];
    }

    /**
     * クラフト経験値を加算し、自動でレベルアップを処理する。
     * @return レベルアップが発生したか
     */
    public boolean addExp(double amount) {
        if (craftLevel >= MAX_LEVEL) return false;

        craftExp += amount;
        boolean leveledUp = false;

        while (craftLevel < MAX_LEVEL && craftExp >= getRequiredExp()) {
            craftExp -= getRequiredExp();
            craftLevel++;
            leveledUp = true;
        }

        if (craftLevel >= MAX_LEVEL) {
            craftLevel = MAX_LEVEL;
            craftExp = 0;
        }

        return leveledUp;
    }

    /** 経験値の進捗率（0.0〜1.0） */
    public double getExpProgress() {
        if (craftLevel >= MAX_LEVEL) return 1.0;
        return craftExp / getRequiredExp();
    }

    public static int getMaxLevel() { return MAX_LEVEL; }
}
