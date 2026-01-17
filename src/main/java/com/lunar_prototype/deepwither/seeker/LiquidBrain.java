package com.lunar_prototype.deepwither.seeker;

import org.bukkit.util.Vector;
import java.util.*;

public class LiquidBrain {
    // ニューロン群
    public final LiquidNeuron aggression;
    public final LiquidNeuron fear;
    public final LiquidNeuron tactical;
    public final LiquidNeuron reflex = new LiquidNeuron(0.3);

    // 既存の動的パラメータ
    public float adrenaline = 0.0f;
    public float composure;
    public double morale = 1.0;
    public double patience = 1.0;
    public float frustration = 0.0f;

    // [TQH] 新たに追加される物理量
    public float systemTemperature = 0.5f; // 0.0:SOLID, 0.5:LIQUID, 1.2+:GAS
    private static final float THERMAL_CONDUCTIVITY = 0.25f; // TD誤差 -> 熱への変換
    private static final float COOLING_COEFFICIENT = 0.45f;   // 報酬 -> 冷却への変換

    // 既存のフィールド群
    public final float[] fatigueMap = new float[8];
    private static final float FATIGUE_STRESS = 0.15f;
    private static final float FATIGUE_DECAY = 0.95f;
    public int lastStateIdx = 0, lastActionIdx = 4;
    public float accumulatedReward = 0.0f, accumulatedPenalty = 0.0f;
    public Vector lastPredictedLocation = null;
    public long lastPredictionTick = 0;
    public float velocityTrust = 0.5f;

    public int secondLastActionIdx = -1; // 前々回の行動
    public int secondLastStateIdx = -1;  // 前々回の状態

    public float attentionLevel = 0.0f; // 1.0(直視されている) ～ -1.0(背を向けられている)
    public boolean isVisibleFromEnemy = true; // 相手のFOV内に入っているか

    public int actionRepeatCount = 0;

    public class QTable {
        private final float[] data = new float[512 * 8];

        public int packState(float adv, float dist, float hp, boolean isRec, int crowd) {
            int a = (adv > 0.7f) ? 2 : (adv < 0.3f) ? 0 : 1;
            int d = (dist < 4f) ? 0 : (dist < 10f) ? 1 : 2;
            int h = (hp > 0.7f) ? 2 : (hp < 0.3f) ? 0 : 1;
            int r = isRec ? 1 : 0;
            int c = (crowd <= 1) ? 0 : (crowd >= 3 ? 2 : 1);
            return (a << 7) | (d << 5) | (h << 3) | (r << 2) | c;
        }

        public float getQ(int sIdx, int aIdx, float fatigue) {
            float baseQ = data[(sIdx << 3) | aIdx];
            return baseQ - (2.0f * fatigue);
        }

        /**
         * [TQH Re-mapped] 戻り値としてTD誤差（Surprise）を返し、熱源とする
         */
        public float updateTQH(int sIdx, int aIdx, float reward, int nextSIdx, float fatigue) {
            int idx = (sIdx << 3) | aIdx;
            float currentQ = data[idx];

            float elasticity = 1.0f - Math.min(0.5f, fatigue);
            float adjustedReward = reward * elasticity;

            float maxNextQ = -1.0f;
            for (int i = 0; i < 8; i++) {
                if (data[(nextSIdx << 3) | i] > maxNextQ) maxNextQ = data[(nextSIdx << 3) | i];
            }

            float targetQ = adjustedReward + 0.9f * maxNextQ;
            float tdError = targetQ - currentQ;

            data[idx] += 0.2f * tdError;
            return tdError; // 熱源として誤差を返す
        }

        public int getBestActionIdx(int sIdx, float[] currentFatigue) {
            int best = 0;
            float maxEffectiveQ = -Float.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                float eq = getQ(sIdx, i, currentFatigue[i]);
                if (eq > maxEffectiveQ) { maxEffectiveQ = eq; best = i; }
            }
            return best;
        }
    }

    public final QTable qTable = new QTable();
    public Map<UUID, AttackPattern> enemyPatterns = new HashMap<>();
    public final TacticalMemory tacticalMemory = new TacticalMemory();
    public final SelfPattern selfPattern = new SelfPattern();

    private final LiquidAstrocyte astrocyte;

    public LiquidBrain(UUID uuid) {
        Random random = new Random(uuid.getMostSignificantBits());
        this.composure = (float) (0.3 + (random.nextDouble() * 0.7));
        this.aggression = new LiquidNeuron(0.08 + (random.nextDouble() * 0.04));
        this.fear = new LiquidNeuron(0.08 + (random.nextDouble() * 0.04));
        this.tactical = new LiquidNeuron(0.05 + (random.nextDouble() * 0.02));

        this.astrocyte = new LiquidAstrocyte(aggression, fear, tactical, reflex);
    }

    /**
     * [TQH core] 経験消化と熱力学的平衡の計算
     */
    public void digestExperience() {
        // 1. TD誤差の算出と加熱（予測が外れるほど熱くなる）
        float tdError = qTable.updateTQH(lastStateIdx, lastActionIdx, accumulatedReward - accumulatedPenalty, 0, fatigueMap[lastActionIdx]);
        this.systemTemperature += Math.abs(tdError) * THERMAL_CONDUCTIVITY;

        // 2. 冷却材としての報酬（成功体験がシステムを安定・結晶化させる）
        if (accumulatedReward > 0) {
            this.systemTemperature -= accumulatedReward * COOLING_COEFFICIENT;
        }

        // 3. 放熱とクランプ（自然なエントロピー増大と限界設定）
        this.systemTemperature = Math.max(0.0f, Math.min(2.0f, systemTemperature * 0.94f));

        // 4. 疲労の代謝（既存ロジック）
        for (int i = 0; i < fatigueMap.length; i++) {
            if (i == lastActionIdx) fatigueMap[i] += FATIGUE_STRESS;
            else fatigueMap[i] *= FATIGUE_DECAY;
        }

        // 5. 神経更新（システム温度を考慮）
        float urgency = Math.min(1.0f, (accumulatedReward + accumulatedPenalty) * 5.0f);
        aggression.update(accumulatedReward > 0 ? 1.0 : 0.0, urgency, systemTemperature);
        fear.update(accumulatedPenalty > 0 ? 1.0 : 0.0, urgency, systemTemperature);
        tactical.update(0.5, 0.1, systemTemperature);
        reflex.update(adrenaline, 1.0, systemTemperature);

        // アドレナリン・不満度の同期
        if (accumulatedReward > 0) {
            adrenaline = Math.max(0, adrenaline - 0.1f);
            frustration = Math.max(0, frustration - 0.2f);
        }
        if (accumulatedPenalty > 0) {
            adrenaline = Math.min(1.0f, adrenaline + (accumulatedPenalty * 0.2f));
        }

        accumulatedReward = 0; accumulatedPenalty = 0;

        // 6. 構造的再編 (TQHによる相転移)
        reshapeTopology();

        // 2. [NEW] アストロサイトによる空間統制
        // ニューロン同士が勝手に結合を強めすぎた場合、ここでグリアが冷や水を浴びせる
        astrocyte.regulate(systemTemperature);
    }

    public float getGliaActivity() {
        return astrocyte.getInterventionLevel();
    }

    /**
     * [TQH Phase Transition]
     * システム温度に基づき、脳の物理構造（トポロジー）を書き換える
     */
    public void reshapeTopology() {
        clearTemporarySynapses();

        if (systemTemperature > 1.2f) {
            // 【気体状態: GAS】 超高エントロピー・混沌探索
            // 全ての抑制を解除し、反射層を攻撃層に直結。予測不能な動きを生む。
            aggression.connect(reflex, 1.8f);
            reflex.connect(tactical, -0.8f); // 冷静さを破壊
        }
        else if (systemTemperature < 0.3f) {
            // 【固体状態: SOLID】 低エントロピー・結晶化
            // 成功パターンを「正解」として強固に固定。無駄のない動き。
            tactical.connect(aggression, 0.9f);
            aggression.connect(tactical, 0.3f);
        }
        else {
            // 【液体状態: LIQUID】 既存の adrenaline/composure に基づく流動的判断
            if (adrenaline > 0.85f) aggression.connect(reflex, 1.2f);
            if (composure > 0.7f) {
                aggression.connect(tactical, 0.5f);
                fear.connect(tactical, 0.5f);
            }
            if (frustration > 0.6f) reflex.connect(tactical, -0.4f);
        }
    }

    private void clearTemporarySynapses() {
        aggression.disconnect(reflex);
        aggression.disconnect(tactical);
        fear.disconnect(tactical);
        reflex.disconnect(tactical);
    }

    // [2026-01-12] 代表への報告用：Particle.FLASHに渡すRGBデータ
    public int[] getTQHFlashColor() {
        if (systemTemperature > 1.2f) return new int[]{255, 0, 255}; // Magenta (気体・混乱)
        if (systemTemperature < 0.3f) return new int[]{0, 200, 255}; // Cyan (固体・冷徹)
        return new int[]{255, 100, 0}; // Orange (液体・平常)
    }

    /**
     * [TQH-Analytics] 戦闘中の脳状態を記録するデータポイント
     */
    public record BrainSnapshot(
            long tick,
            float temp,
            float morale,
            float frustration,
            String action,
            int[] color
    ) {}

    public List<BrainSnapshot> getCombatHistory() {
        return combatHistory;
    }

    // LiquidBrain内に追加
    private final List<BrainSnapshot> combatHistory = new ArrayList<>();

    public void recordSnapshot(String action) {
        if (combatHistory.size() > 500) combatHistory.remove(0); // 直近500回（約25秒分）を保持
        combatHistory.add(new BrainSnapshot(
                System.currentTimeMillis(),
                systemTemperature,
                (float)morale,
                frustration,
                action,
                getTQHFlashColor()
        ));
    }

    // --- 以下、既存の AttackPattern 等の内部クラス・メソッドを保持 ---
    public void recordAttack(UUID targetId, double distance, boolean isMiss) { /* 既存通り */ }
    public void recordSelfAttack(long currentTick) { /* 既存通り */ }
    public class AttackPattern { public long lastAttackTick; public double averageInterval; public double preferredDist; public int sampleCount; }
    public class TacticalMemory { public double combatAdvantage = 0.5; public int myHits, myMisses, takenHits, avoidedHits; }
    public static class SelfPattern { public long lastAttackTick = 0; public double averageInterval = 0; public int sampleCount = 0; }
}