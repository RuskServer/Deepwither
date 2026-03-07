package com.lunar_prototype.deepwither.seeker;

import org.bukkit.util.Vector;

import java.util.*;

/**
 * [TQH-Native] LiquidBrain v5
 * Rust 依存を排除し、Java ネイティブの NativeQEngine を使用する。
 * 感情モデル (LNN) と意思決定 (Q-Learning) のハイブリッド構成。
 */
public class LiquidBrain {
    // --- Native Engine ---
    private final NativeQEngine engine;

    // --- 既存の互換性維持用フィールド ---
    public final LiquidNeuron aggression = new LiquidNeuron(0.5);
    public final LiquidNeuron fear = new LiquidNeuron(0.4);
    public final LiquidNeuron tactical = new LiquidNeuron(0.3);
    public final LiquidNeuron reflex = new LiquidNeuron(0.3);

    public float adrenaline = 0.0f;
    public float composure = 1.0f;
    public double morale = 1.0;
    public double patience = 1.0;
    public float frustration = 0.0f;
    public float systemTemperature = 0.5f;

    public final float[] fatigueMap = new float[8];
    public int lastStateIdx = 0, lastActionIdx = 4;
    public float accumulatedReward = 0.0f, accumulatedPenalty = 0.0f;
    public Vector lastPredictedLocation = null;
    public long lastPredictionTick = 0;
    public float velocityTrust = 1.0f;

    private final UUID ownerId;
    public Map<UUID, AttackPattern> enemyPatterns = new HashMap<>();
    public final TacticalMemory tacticalMemory = new TacticalMemory();
    public final SelfPattern selfPattern = new SelfPattern();
    private final List<BrainSnapshot> combatHistory = new ArrayList<>();
    public float attentionLevel;

    // --- コンストラクタ ---
    public LiquidBrain(UUID ownerId) {
        this.ownerId = ownerId;
        this.engine = new NativeQEngine();

        // [TQH-Bootstrap] 初期知識（ハミルトニアン規則）の注入
        BanditKnowledgeBase.inject(this.engine);
    }

    /**
     * リソース解放 (ネイティブ化により特段の処理は不要)
     */
    public void dispose() {
        // 必要に応じて履歴のクリア等
    }

    /**
     * [TQH-Bootstrap] 現在の状態インデックスを更新
     */
    public void setCondition(int conditionId) {
        this.lastStateIdx = conditionId;
    }

    /**
     * [Refactored] 思考サイクル (学習 + 行動選択)
     */
    public int cycle(float[] inputs) {
        // 1. 蓄積された報酬/罰を適用 (前回の行動に対する評価)
        float netReward = accumulatedReward - accumulatedPenalty;

        // 2. LNN (感情モデル) からエンジンの状態を更新
        engine.setNeuronState(0, (float) aggression.get());
        engine.setNeuronState(1, (float) fear.get());
        engine.setNeuronState(2, (float) tactical.get());
        engine.setNeuronState(3, (float) reflex.get());

        // 3. エンジンの更新と行動選択
        this.lastActionIdx = engine.update(lastStateIdx, netReward, inputs);

        // 報酬をリセット
        accumulatedReward = 0.0f;
        accumulatedPenalty = 0.0f;

        // 内部状態をエンジンから同期
        syncFromEngine();

        return this.lastActionIdx;
    }

    /**
     * トポロジー再編 (演出用)
     */
    public void reshapeTopology() {
        syncFromEngine();
    }

    /**
     * エンジンの状態を Java フィールドへコピーする
     */
    private void syncFromEngine() {
        this.systemTemperature = engine.getSystemTemperature();
        this.frustration = engine.getFrustration();
        this.adrenaline = engine.getAdrenaline();

        // LNN の update 呼び出し
        float temp = this.systemTemperature;
        aggression.update(0, 0.1, temp);
        fear.update(0, 0.1, temp);
        tactical.update(0, 0.1, temp);
        reflex.update(0, 0.1, temp);
    }

    /**
     * エンジンに対して敵の攻撃予測値を通知する
     */
    public void updateEnemyPrediction(UUID targetId, double currentDist) {
        float imminence = calculateAttackImminence(targetId, currentDist);
        engine.setEnemyAttackImminence(imminence);
    }

    public int think(float[] inputs) {
        return cycle(inputs);
    }

    public float getGliaActivity() {
        return engine.getGliaActivity();
    }

    public double getNativeScore(int actionIdx) {
        return engine.getActionScore(lastStateIdx, actionIdx);
    }

    public void digestExperience() {
        syncFromEngine();
    }

    // --- 既存の互換性維持メソッド (そのまま) ---
    public void recordAttack(UUID targetId, double distance, boolean isMiss) {
        AttackPattern p = enemyPatterns.computeIfAbsent(targetId, k -> new AttackPattern());
        long now = System.currentTimeMillis();
        if (p.lastAttackTick > 0) {
            double interval = (now - p.lastAttackTick) / 50.0;
            p.averageInterval = (p.averageInterval * 0.9) + (interval * 0.1);
        }
        p.lastAttackTick = now;
        p.preferredDist = (p.preferredDist * 0.95) + (distance * 0.05);
        if (isMiss) tacticalMemory.myMisses++; else tacticalMemory.myHits++;
    }

    /**
     * ターゲットの攻撃リズムを解析し、次の一撃が来る「切迫度」を算出する (0.0 - 1.0)
     */
    public float calculateAttackImminence(UUID targetId, double currentDist) {
        AttackPattern p = enemyPatterns.get(targetId);
        if (p == null || p.sampleCount < 3) return 0.0f; // データ不足

        long ticksSinceLast = (System.currentTimeMillis() - p.lastAttackTick) / 50;
        
        // 1. リズムによる予測: 平均インターバルに近づくほど数値が上がる
        double rhythmFactor = 0.0;
        if (ticksSinceLast > p.averageInterval * 0.7) {
            // インターバルの 70% を過ぎたあたりから警戒度を上げる
            rhythmFactor = Math.min(1.0, (ticksSinceLast / p.averageInterval));
        }

        // 2. 距離による補正: プレイヤーの得意なリーチにいる場合は更に危険
        double distFactor = 1.0 - Math.min(1.0, Math.abs(currentDist - p.preferredDist) / 3.0);

        return (float) (rhythmFactor * distFactor);
    }

    public void recordSnapshot(String action) {
        if (combatHistory.size() > 500) combatHistory.remove(0);
        combatHistory.add(new BrainSnapshot(
                System.currentTimeMillis(),
                systemTemperature,
                (float)morale,
                frustration,
                action,
                getTQHFlashColor()
        ));
    }

    public int[] getTQHFlashColor() {
        if (systemTemperature < 0.3f) return new int[]{100, 100, 255}; // SOLID (Blue)
        if (systemTemperature < 0.8f) return new int[]{150, 255, 150}; // LIQUID (Green)
        return new int[]{255, 100, 100}; // GAS (Red)
    }

    // --- 内部データ構造 ---
    public record BrainSnapshot(long tick, float temp, float morale, float frustration, String action, int[] color) {}
    public List<BrainSnapshot> getCombatHistory() { return combatHistory; }
    public static class AttackPattern { public long lastAttackTick; public double averageInterval = 20.0; public double preferredDist = 3.0; public int sampleCount; }
    public static class TacticalMemory { public double combatAdvantage = 0.5; public int myHits, myMisses, takenHits, avoidedHits; public long lastHitTime; }
    public static class SelfPattern { public long lastAttackTick = 0; public double averageInterval = 0; public int sampleCount = 0; }
}