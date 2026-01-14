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

    // LiquidBrainクラス内に追加
    public int secondLastActionIdx = -1; // 前々回の行動
    public int secondLastStateIdx = -1;  // 前々回の状態

    public float attentionLevel = 0.0f; // 1.0(直視されている) ～ -1.0(背を向けられている)
    public boolean isVisibleFromEnemy = true; // 相手のFOV内に入っているか

    public int actionRepeatCount = 0;

    // --- [新実装] Elastic Q-Learning: 活動電位疲労 Ft(a) ---
    // 行動の「飽き」を管理する配列 (ACTIONS[8]に対応)
    public final float[] fatigueMap = new float[8];
    private static final float FATIGUE_STRESS = 0.15f; // 1回使うごとの蓄積疲労
    private static final float FATIGUE_DECAY = 0.95f;  // 非選択時の回復率

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

        public float getQ(int sIdx, int aIdx,float fatigue) {
            float baseQ = data[(sIdx << 3) | aIdx];
            float alpha = 2.0f; // 疲労の重み（性格パラメータ）
            return baseQ - (alpha * fatigue);
        }

        public int getBestActionIdx(int sIdx, float[] currentFatigue) {
            int best = 0;
            float maxEffectiveQ = -Float.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                float eq = getQ(sIdx, i, currentFatigue[i]);
                if (eq > maxEffectiveQ) {
                    maxEffectiveQ = eq;
                    best = i;
                }
            }
            return best;
        }

        public void update(int sIdx, int aIdx, float reward, int nextSIdx, float fatigue) {
            int idx = (sIdx << 3) | aIdx;

            // [新理論] 弾性学習: 疲労している行動への報酬を抑制（当たり前化）
            float elasticity = 1.0f - Math.min(0.5f, fatigue);
            float adjustedReward = reward * elasticity;

            float maxNextQ = -1.0f;
            int nextBase = nextSIdx << 3;
            for (int i = 0; i < 8; i++) {
                if (data[nextBase | i] > maxNextQ) maxNextQ = data[nextBase | i];
            }
            data[idx] += 0.2f * (adjustedReward + 0.9f * maxNextQ - data[idx]);
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

    /**
     * [新理論] 脳の構造的トポロジーを現在の状況に合わせて再編する
     * 従来のAIにはない「物理的な脳の作り変え」を再現
     */
    /**
     * [改良] AM-QL: 構造的再編の強化
     */
    public void reshapeTopology() {
        clearTemporarySynapses();

        if (adrenaline > 0.85f) {
            // 【DSR_BYPASS: SURGE】 思考をショートカットして反射層へ直結
            aggression.connect(reflex, 1.5f);
            // 疲労の影響を一時的に無視する「リミッター解除」フラグ（演出用）
        } else if (composure > 0.7f) {
            // 【精密演算モード】 戦術層への集中
            aggression.connect(tactical, 0.5f);
            fear.connect(tactical, 0.5f);
        }

        if (frustration > 0.6f) {
            // フラストレーションによるノイズ混入
            reflex.connect(tactical, -0.4f);
        }
    }

    private void clearTemporarySynapses() {
        aggression.disconnect(reflex);
        aggression.disconnect(tactical);
        fear.disconnect(tactical);
        reflex.disconnect(tactical);
    }

    // [2026-01-12] サージ演出（FLASHカラー）との連動
    private void dramaticStructureChangeEffect() {
        // ここで Particle.FLASH + Color データの注入トリガーを引く
        this.accumulatedReward += 0.01f; // 構造変化自体に微小な期待値を与える
    }

    /**
     * [改良] 経験消化と疲労回復の同期
     */
    public void digestExperience() {
        // QTableの学習更新 (疲労度を引数に追加)
        qTable.update(lastStateIdx, lastActionIdx, accumulatedReward - accumulatedPenalty, 0, fatigueMap[lastActionIdx]);

        // [Elastic] 疲労の代謝
        for (int i = 0; i < fatigueMap.length; i++) {
            if (i == lastActionIdx) {
                fatigueMap[i] += FATIGUE_STRESS; // 使用した行動は疲労
            } else {
                fatigueMap[i] *= FATIGUE_DECAY;  // 未使用の行動は回復
            }
        }

        // 既存の神経更新
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
        reshapeTopology(); // 脳構造の更新
    }
}