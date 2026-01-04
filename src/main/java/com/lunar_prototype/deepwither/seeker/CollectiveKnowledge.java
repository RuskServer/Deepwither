package com.lunar_prototype.deepwither.seeker;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CollectiveKnowledge {
    // プレイヤーごとの「危険度」の共有
    public static Map<UUID, Double> playerDangerLevel = new HashMap<>();

    // 集団としての「警戒パラメータ」
    public static double globalAggressionBias = 0.0;
    public static double globalFearBias = 0.0;

    /**
     * 周囲で仲間が倒された時に呼ばれる（SeekerAIEngineのDeathListener等から）
     */
    public static void reportAllyDeath(Player killer, double dist) {
        // 仲間が殺されたのを見た（あるいは聞いた）ことで、集団全体の恐怖値にバイアスをかける
        globalFearBias = Math.min(1.0, globalFearBias + 0.1);

        // プレイヤーの危険度を更新
        playerDangerLevel.merge(killer.getUniqueId(), 0.2, Double::sum);
    }
}
