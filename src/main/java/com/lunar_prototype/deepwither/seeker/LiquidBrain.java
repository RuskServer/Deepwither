package com.lunar_prototype.deepwither.seeker;

import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * [TQH-Hybrid] LiquidBrain
 * 構造は Java で維持し、計算負荷の高いニューラルネットワークと学習ロジックを
 * Rust (DarkSingularity) へ JNI 経由で委譲する。
 */
public class LiquidBrain {
    // --- Rust 連携用 ---
    private final long nativeHandle;

    static {
        // 1. まず標準パスを探す
        try {
            System.loadLibrary("dark_singularity");
        } catch (UnsatisfiedLinkError e) {
            String libName = System.mapLibraryName("dark_singularity");
            File lib = new File("plugins/deepwither/" + libName);
            if (lib.exists()) {
                System.load(lib.getAbsolutePath());
            } else {
                throw new IllegalStateException("DarkSingularity Native Library Missing: " + lib.getAbsolutePath(), e);
            }
        }
    }

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
        // Rust 側の Singularity インスタンスを生成し、メモリアドレス(Handle)を取得
        this.nativeHandle = initNativeSingularity();
    }

    /**
     * エンティティ消滅時に必ず呼び出し、Rust 側のメモリを解放する。
     */
    public void dispose() {
        if (nativeHandle != 0 && disposed.compareAndSet(false, true)) {
            destroyNativeSingularity(nativeHandle);
        }
    }

    /**
     * [TQH] 経験の消化 - Rust 側へ委譲
     */
    public void digestExperience() {
        // Java 側の報酬/罰を Rust に渡して学習させる
        learnNative(nativeHandle, accumulatedReward - accumulatedPenalty);

        // 学習後の内部状態を Java 側のフィールドに同期（UI表示や互換性のため）
        syncFromNative();

        // 蓄積リセット
        accumulatedReward = 0.0f;
        accumulatedPenalty = 0.0f;
    }

    /**
     * [DSR] トポロジー再編 - Rust 側で実行
     */
    public void reshapeTopology() {
        // digestExperience 内で learnNative を通じて実行されるため、
        // 個別に呼ぶ必要はないが、手動同期用にラップ
        syncFromNative();
    }

    /**
     * Rust 側の状態を Java フィールドへコピーする
     */
    private void syncFromNative() {
        if (nativeHandle == 0) return;
        this.systemTemperature = getSystemTemperature(nativeHandle);
        this.frustration = getFrustration(nativeHandle);
        this.adrenaline = getAdrenaline(nativeHandle);

        // 各ニューロンの活性度も同期
        float[] neuronStates = getNeuronStates(nativeHandle);
        if (neuronStates.length >= 4) {
            // aggression.set(neuronStates[0]) などのメソッドが必要
            // 既存の LiquidNeuron に set メソッドがない場合は直接フィールドアクセスor調整
        }
    }

    /**
     * 思考処理 (Action選択)
     */
    public int think(float[] inputs) {
        // Rust 側で Q-Table とニューロン状態から ActionID を算出
        this.lastActionIdx = selectActionNative(nativeHandle, inputs);
        return this.lastActionIdx;
    }

    /**
     * [Glia Interface] Rust側のHorizon(恒常性)の介入度を取得
     * 1.0 に近いほど、過剰興奮を抑制するために「慎重な動き」が強制される
     */
    public float getGliaActivity() {
        if (nativeHandle == 0) return 0.0f;
        return getGliaActivityNative(nativeHandle);
    }

    /**
     * [Elastic Q] 指定した行動の現在の期待値(Q値)を取得
     */
    public double getNativeScore(int actionIdx) {
        if (nativeHandle == 0) return 0.0;
        return getActionScoreNative(nativeHandle, actionIdx);
    }

    // --- Native Methods エリア ---
    private native float getGliaActivityNative(long handle);
    private native float getActionScoreNative(long handle, int actionIdx);

    // --- Native Methods ---
    private native long initNativeSingularity();
    private native void destroyNativeSingularity(long handle);
    private native int selectActionNative(long handle, float[] inputs);
    private native void learnNative(long handle, float totalReward);
    private native float getSystemTemperature(long handle);
    private native float getFrustration(long handle);
    private native float getAdrenaline(long handle);
    private native float[] getNeuronStates(long handle);

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
    public class TacticalMemory { public double combatAdvantage = 0.5; public int myHits, myMisses, takenHits, avoidedHits; public long lastHitTime; }
    public static class SelfPattern { public long lastAttackTick = 0; public double averageInterval = 0; public int sampleCount = 0; }
}