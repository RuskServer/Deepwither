package com.lunar_prototype.deepwither.seeker;

import org.bukkit.util.Vector;
import java.util.*;

public class LiquidBrain {
    public final LiquidNeuron aggression;
    public final LiquidNeuron fear;
    public final LiquidNeuron tactical;
    public final LiquidNeuron reflex = new LiquidNeuron(0.3);

    public float adrenaline = 0.0f;
    public float composure;
    public double morale = 1.0;
    public double patience = 1.0;
    public float frustration = 0.0f;

    public int actionRepeatCount = 0;

    // 前回予測した「20Tick後の座標」
    public Vector lastPredictedLocation = null;
    public long lastPredictionTick = 0;

    // 速度ベクトルの信用度 (0.0f ～ 1.0f)
    // 1.0なら直進すると信じる、0.0なら今の場所から動かないと疑う
    public float velocityTrust = 0.5f;

    // 量子化エンジン用フィールド
    public int lastStateIdx = 0;
    public int lastActionIdx = 4; // Default: OBSERVE

    public float accumulatedReward = 0.0f;
    public float accumulatedPenalty = 0.0f;

    public class QTable {
        // 512状態 * 8アクション = 4096要素
        private final float[] data = new float[512 * 8];

        public int packState(float adv, float dist, float hp, boolean isRec, int crowd) {
            int a = (adv > 0.7f) ? 2 : (adv < 0.3f) ? 0 : 1;
            int d = (dist < 4f) ? 0 : (dist < 10f) ? 1 : 2;
            int h = (hp > 0.7f) ? 2 : (hp < 0.3f) ? 0 : 1;
            int r = isRec ? 1 : 0;
            int c = (crowd <= 1) ? 0 : (crowd >= 3 ? 2 : 1);
            return (a << 7) | (d << 5) | (h << 3) | (r << 2) | c;
        }

        public float getQ(int sIdx, int aIdx) {
            return data[(sIdx << 3) | aIdx];
        }

        public int getBestActionIdx(int sIdx) {
            int best = 0;
            float maxQ = -Float.MAX_VALUE;
            int base = sIdx << 3;
            for (int i = 0; i < 8; i++) {
                if (data[base | i] > maxQ) {
                    maxQ = data[base | i];
                    best = i;
                }
            }
            return best;
        }

        public void update(int sIdx, int aIdx, float reward, int nextSIdx) {
            int idx = (sIdx << 3) | aIdx;
            float maxNextQ = -1.0f;
            int nextBase = nextSIdx << 3;
            for (int i = 0; i < 8; i++) {
                float q = data[nextBase | i];
                if (q > maxNextQ) maxNextQ = q;
            }
            data[idx] += 0.2f * (reward + 0.9f * maxNextQ - data[idx]);
        }
    }

    public final QTable qTable = new QTable();
    // 既存のAttackPatternなどの内部クラスは維持
    public Map<UUID, AttackPattern> enemyPatterns = new HashMap<>();
    public final TacticalMemory tacticalMemory = new TacticalMemory();
    public final SelfPattern selfPattern = new SelfPattern();

    public class AttackPattern {
        public long lastAttackTick;
        public double averageInterval;
        public double preferredDist;
        public int sampleCount;
    }

    public class TacticalMemory {
        public double combatAdvantage = 0.5;
        public int myHits, myMisses, takenHits, avoidedHits;
    }

    public static class SelfPattern {
        public long lastAttackTick = 0;
        public double averageInterval = 0;
        public int sampleCount = 0;
    }

    public LiquidBrain(UUID uuid) {
        Random random = new Random(uuid.getMostSignificantBits());
        this.composure = (float) (0.3 + (random.nextDouble() * 0.7));
        this.aggression = new LiquidNeuron(0.08 + (random.nextDouble() * 0.04));
        this.fear = new LiquidNeuron(0.08 + (random.nextDouble() * 0.04));
        this.tactical = new LiquidNeuron(0.05 + (random.nextDouble() * 0.02));
    }

    public void updateTacticalAdvantage() {
        double offense = (double) tacticalMemory.myHits / Math.max(1, tacticalMemory.myHits + tacticalMemory.myMisses);
        double defense = (double) tacticalMemory.avoidedHits / Math.max(1, tacticalMemory.takenHits + tacticalMemory.avoidedHits);
        double currentSnapshot = (offense * 0.6) + (defense * 0.4);
        tacticalMemory.combatAdvantage = (tacticalMemory.combatAdvantage * 0.8) + (currentSnapshot * 0.2);
    }

    /**
     * 敵（プレイヤー）の攻撃リズムと間合いを記録する
     */
    public void recordAttack(UUID targetId, double distance, boolean isMiss) {
        AttackPattern p = enemyPatterns.computeIfAbsent(targetId, k -> new AttackPattern());
        long currentTick = System.currentTimeMillis() / 50; // BukkitのTick相当に変換、または外部から渡す

        // A. エイムフィルタ (空振りの場合、ターゲットの方を向いていない攻撃は無視)
        // ※ 厳密な判定はListener側で行うため、ここでは受け取ったフラグを尊重

        if (p.lastAttackTick > 0) {
            long interval = currentTick - p.lastAttackTick;

            // B. ノイズ除去 (5tick未満の連打や、200tick以上の無反応は無視)
            if (interval >= 5 && interval < 200) {
                // C. 量子化重み付け学習 (命中は0.3, 空振りは0.1の重み)
                float weight = isMiss ? 0.1f : 0.3f;

                p.averageInterval = (p.averageInterval * (1.0f - weight)) + (interval * weight);
                p.preferredDist = (p.preferredDist * (1.0f - weight)) + ((float)distance * weight);
                p.sampleCount++;
            }
        }
        p.lastAttackTick = currentTick;
    }

    /**
     * 自分自身の攻撃成功リズムを記録する (自己同期用)
     */
    public void recordSelfAttack(long currentTick) {
        if (selfPattern.lastAttackTick > 0) {
            long interval = currentTick - selfPattern.lastAttackTick;

            if (interval >= 4 && interval < 100) {
                // 自分のリズムは 0.2 の固定重みで学習
                selfPattern.averageInterval = (selfPattern.averageInterval * 0.8f) + (interval * 0.2f);
                selfPattern.sampleCount++;
            }
        }
        selfPattern.lastAttackTick = currentTick;
    }

    public void digestExperience() {
        if (accumulatedReward > 0) {
            aggression.update(1.0f, accumulatedReward * 0.5f);
            fear.update(0.0f, accumulatedReward * 0.2f);
            adrenaline = Math.max(0, adrenaline - 0.1f);
            frustration = Math.max(0, frustration - 0.2f);
        }
        if (accumulatedPenalty > 0) {
            float fearWeight = 0.8f * (1.0f - (composure * 0.5f));
            fear.update(1.0f, accumulatedPenalty * fearWeight);
            adrenaline = Math.min(1.0f, adrenaline + (accumulatedPenalty * 0.2f));
        }
        accumulatedReward = 0; accumulatedPenalty = 0;
    }
}