package com.lunar_prototype.deepwither.core.damage;

import org.bukkit.Location;

/**
 * ダメージ計算に関する純粋な計算ロジックを提供するユーティリティ
 */
public class DamageCalculator {

    /**
     * 防御力によるダメージ減算を計算する
     */
    public static double applyDefense(double damage, double defense, double divisor) {
        if (defense <= 0) return damage;
        double reduction = defense / (defense + divisor);
        return damage * (1.0 - reduction);
    }

    /**
     * 距離によるダメージ倍率を計算する (遠距離武器用)
     */
    public static double calculateDistanceMultiplier(Location attackerLoc, Location victimLoc) {
        double distance = attackerLoc.distance(victimLoc);
        final double MIN_DISTANCE = 10.0;
        final double MAX_BOOST_DISTANCE = 40.0;
        final double MAX_MULTIPLIER = 1.2;
        final double MIN_MULTIPLIER = 0.6;
        
        double distanceMultiplier;
        if (distance <= MIN_DISTANCE) {
            double range = MIN_DISTANCE;
            double minMaxDiff = 1.0 - MIN_MULTIPLIER;
            distanceMultiplier = MIN_MULTIPLIER + (distance / range) * minMaxDiff;
        } else if (distance >= MAX_BOOST_DISTANCE) {
            distanceMultiplier = MAX_MULTIPLIER;
        } else {
            double range = MAX_BOOST_DISTANCE - MIN_DISTANCE;
            double current = distance - MIN_DISTANCE;
            double minMaxDiff = MAX_MULTIPLIER - 1.0;
            distanceMultiplier = 1.0 + (current / range) * minMaxDiff;
        }
        return Math.max(MIN_MULTIPLIER, Math.min(distanceMultiplier, MAX_MULTIPLIER));
    }

    /**
     * 確率ロールを行う
     */
    public static boolean rollChance(double chance) {
        return (Math.random() * 100) + 1 <= chance;
    }
}
