package com.lunar_prototype.deepwither.seeker;

import com.lunar_prototype.dark_singularity_api.Singularity;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * [TQH-Hybrid] LiquidBrain
 * 構造は Java で維持し、計算負荷の高いニューラルネットワークと学習ロジックを
 * Rust (DarkSingularity) へ API 経由で委譲する。
 */
public class LiquidBrain {
    // --- Rust API 連携用 ---
    private final Singularity singularity;

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
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private final UUID ownerId;
    public Map<UUID, AttackPattern> enemyPatterns = new HashMap<>();
    public final TacticalMemory tacticalMemory = new TacticalMemory();
    public final SelfPattern selfPattern = new SelfPattern();
    private final List<BrainSnapshot> combatHistory = new ArrayList<>();
    public float attentionLevel;

    // --- コンストラクタ ---
    public LiquidBrain(UUID ownerId) {
        this.ownerId = ownerId;
        // Singularity インスタンスを生成 (入力5次元, 出力8アクション)
        this.singularity = new Singularity(5, 8);
    }

    /**
     * エンティティ消滅時に必ず呼び出し、Rust 側のメモリを解放する。
     */
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            singularity.close();
        }
    }

    /**
     * [Refactored] 思考サイクル (学習 + 行動選択)
     * API の learn() と selectAction() を組み合わせて使用する。
     */
    public int cycle(float[] inputs) {
        if (disposed.get()) return 4; // Default fallback

        // 1. 蓄積された報酬/罰を適用 (前回の行動に対する評価)
        float netReward = accumulatedReward - accumulatedPenalty;
        singularity.learn(netReward);

        // 2. 次の状態(inputs)に基づいて行動を選択
        this.lastActionIdx = singularity.selectAction(inputs);

        // 報酬をリセット
        accumulatedReward = 0.0f;
        accumulatedPenalty = 0.0f;

        // 内部状態をAPIから同期
        syncFromAPI();

        return this.lastActionIdx;
    }

    /**
     * [DSR] トポロジー再編
     */
    public void reshapeTopology() {
        // API側で自動管理されるため、ここでは状態同期のみ行う
        syncFromAPI();
    }

    /**
     * API 側の状態を Java フィールドへコピーする
     */
    private void syncFromAPI() {
        if (disposed.get()) return;

        // 1. 基本パラメータの同期
        this.systemTemperature = singularity.getSystemTemperature();
        this.frustration = singularity.getFrustration();
        this.adrenaline = singularity.getAdrenaline();

        // 2. ニューロン状態の同期
        float[] neuronStates = singularity.getNeuronStates();
        if (neuronStates != null && neuronStates.length >= 4) {
            this.aggression.setState(neuronStates[0]);
            this.fear.setState(neuronStates[1]);
            this.tactical.setState(neuronStates[2]);
            this.reflex.setState(neuronStates[3]);
        }
    }

    /**
     * 思考処理 (Legacy Support)
     * 新しい実装では cycle() を使用することを推奨。
     */
    public int think(float[] inputs) {
        return cycle(inputs);
    }

    /**
     * [Glia Interface] Rust側のHorizon(恒常性)の介入度を取得
     */
    public float getGliaActivity() {
        if (disposed.get()) return 0.0f;
        return singularity.getGliaActivity();
    }

    /**
     * [Elastic Q] 指定した行動の現在の期待値(Q値)を取得
     */
    public double getNativeScore(int actionIdx) {
        if (disposed.get()) return 0.0;
        return singularity.getActionScore(actionIdx);
    }
    
    // --- 経験の消化 (Legacy Wrapper for manual calls) ---
    public void digestExperience() {
        // cycle() に統合されたため、単独では何もしないか、syncのみ行う
        syncFromAPI();
        // 報酬リセットは行わない（次のcycleで適用するため）
    }

    // --- 既存の互換性維持メソッド (中身はそのまま) ---
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

    public void recordSelfAttack(long currentTick) {
        if (selfPattern.lastAttackTick > 0) {
            double interval = (currentTick - selfPattern.lastAttackTick);
            selfPattern.averageInterval = (selfPattern.averageInterval * 0.8) + (interval * 0.2);
        }
        selfPattern.lastAttackTick = currentTick;
        selfPattern.sampleCount++;
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

    // --- 内部データ構造 (そのまま保持) ---
    public record BrainSnapshot(long tick, float temp, float morale, float frustration, String action, int[] color) {}
    public List<BrainSnapshot> getCombatHistory() { return combatHistory; }
    public static class AttackPattern { public long lastAttackTick; public double averageInterval = 20.0; public double preferredDist = 3.0; public int sampleCount; }
    public static class TacticalMemory { public double combatAdvantage = 0.5; public int myHits, myMisses, takenHits, avoidedHits; public long lastHitTime; }
    public static class SelfPattern { public long lastAttackTick = 0; public double averageInterval = 0; public int sampleCount = 0; }
}