package com.lunar_prototype.deepwither.seeker;

import java.util.Random;
import java.util.UUID;

public class LiquidBrain {
    public final LiquidNeuron aggression;
    public final LiquidNeuron fear;
    public final LiquidNeuron tactical;
    public final LiquidNeuron reflex = new LiquidNeuron(0.3);

    // --- 新規パラメーター ---
    public double adrenaline = 0.0;  // 興奮度：高いほど反応速度（reflex）が上がり、冷静さが下がる
    public double composure;         // 冷静さ（性格係数）：高いほど恐怖(fear)の蓄積を抑える
    public double morale = 1.0;      // 士気：Aggression - Fear の結果に影響し、戦略を決定する
    public double patience = 1.0;    // 忍耐：ハメられている時に「様子見」を選択する確率に影響

    public double lastEnemyDist = -1.0;
    public double accumulatedReward = 0.0;
    public double accumulatedPenalty = 0.0;

    public LiquidBrain(UUID uuid) {
        Random random = new Random(uuid.getMostSignificantBits());

        // 個体による性格の固定
        this.composure = 0.3 + (random.nextDouble() * 0.7); // 0.3〜1.0
        this.patience = 0.5 + (random.nextDouble() * 0.5);  // 0.5〜1.0

        this.aggression = new LiquidNeuron(0.08 + (random.nextDouble() * 0.04));
        this.fear = new LiquidNeuron(0.08 + (random.nextDouble() * 0.04));
        this.tactical = new LiquidNeuron(0.05 + (random.nextDouble() * 0.02));

        this.aggression.update(random.nextDouble() * 0.2, 1.0);
    }

    public void digestExperience() {
        if (accumulatedReward > 0) {
            aggression.update(1.0, accumulatedReward * 0.5);
            fear.update(0.0, accumulatedReward * 0.2);
            adrenaline = Math.max(0, adrenaline - 0.1); // 成功すると冷静になる
        }
        if (accumulatedPenalty > 0) {
            // 冷静さ(composure)が高いほど、ダメージによる恐怖蓄積を軽減する
            double fearWeight = 0.8 * (1.0 - (composure * 0.5));
            fear.update(1.0, accumulatedPenalty * fearWeight);

            tactical.update(1.0, accumulatedPenalty * 0.5);
            adrenaline = Math.min(1.0, adrenaline + (accumulatedPenalty * 0.2)); // 被弾でアドレナリン上昇
        }

        accumulatedReward = 0;
        accumulatedPenalty = 0;
    }
}