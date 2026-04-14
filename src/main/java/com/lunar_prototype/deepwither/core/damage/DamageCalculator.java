package com.lunar_prototype.deepwither.core.damage;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.PlayerCache;
import com.lunar_prototype.deepwither.core.playerdata.PlayerData;
import com.lunar_prototype.deepwither.util.PseudoRandom;
import org.bukkit.Location;
import org.bukkit.entity.Player;

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
     * 確率ロールを行う (単純ランダム)
     */
    public static boolean rollChance(double chance) {
        return Math.random() * 100 < chance;
    }

    /**
     * 疑似乱数分布 (PRD) に基づいた確率ロールを行う
     */
    public static boolean rollPseudoChance(Player player, double chance) {
        if (chance <= 0) return false;
        if (chance >= 100) return true;

        PlayerCache cache = Deepwither.getInstance().getCacheManager().getCache(player.getUniqueId());
        if (cache == null) return rollChance(chance);

        PlayerData data = cache.getData();
        int n = data.getCritCounter();

        boolean success = PseudoRandom.roll(chance, n);
        if (success) {
            data.setCritCounter(1); // リセット
        } else {
            data.setCritCounter(n + 1); // 次回の確率を上げる
        }

        return success;
    }
}
