package com.lunar_prototype.deepwither.util;

import java.util.TreeMap;

/**
 * Pseudo-Random Distribution (PRD) ユーティリティ
 * クリティカル判定の偏りを減らし、期待値を安定させる。
 */
public class PseudoRandom {

    private static final TreeMap<Double, Double> C_TABLE = new TreeMap<>();

    static {
        // 表示確率(%)に対する C値のルックアップテーブル (近似値)
        C_TABLE.put(1.0, 0.000156);
        C_TABLE.put(5.0, 0.003802);
        C_TABLE.put(10.0, 0.014746);
        C_TABLE.put(15.0, 0.032210);
        C_TABLE.put(20.0, 0.055704);
        C_TABLE.put(25.0, 0.084744);
        C_TABLE.put(30.0, 0.118577);
        C_TABLE.put(35.0, 0.157357);
        C_TABLE.put(40.0, 0.201547);
        C_TABLE.put(45.0, 0.249307);
        C_TABLE.put(50.0, 0.302103);
        C_TABLE.put(60.0, 0.422650);
        C_TABLE.put(70.0, 0.571429);
        C_TABLE.put(80.0, 0.750000);
        C_TABLE.put(90.0, 0.900000);
        C_TABLE.put(100.0, 1.000000);
    }

    /**
     * 表示確率に対応する C値を返します。
     * @param p 表示確率 (0.0 - 100.0)
     * @return C値
     */
    public static double getCFromProbability(double p) {
        if (p <= 0) return 0.0;
        if (p >= 100.0) return 1.0;

        // テーブルから最も近い値を取得 (線形補間)
        Double floorKey = C_TABLE.floorKey(p);
        Double ceilKey = C_TABLE.ceilingKey(p);

        if (floorKey == null) return C_TABLE.get(ceilKey);
        if (ceilKey == null) return C_TABLE.get(floorKey);
        if (floorKey.equals(ceilKey)) return C_TABLE.get(floorKey);

        double floorC = C_TABLE.get(floorKey);
        double ceilC = C_TABLE.get(ceilKey);

        return floorC + (ceilC - floorC) * (p - floorKey) / (ceilKey - floorKey);
    }

    /**
     * PRDに基づいた判定を行います。
     * @param p 表示確率 (0.0 - 100.0)
     * @param n 現在の試行回数 (前回成功してから何回目か、1から始まる)
     * @return 成功した場合は true
     */
    public static boolean roll(double p, int n) {
        double c = getCFromProbability(p);
        double currentP = c * n;
        return Math.random() < currentP;
    }
}
