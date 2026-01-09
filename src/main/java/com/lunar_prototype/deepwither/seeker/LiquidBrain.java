package com.lunar_prototype.deepwither.seeker;

import com.lunar_prototype.deepwither.seeker.v2.BehaviorProfile;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
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
    public double frustration = 0.0; // 【追加】ハメられた時のストレス蓄積

    public double accumulatedReward = 0.0;
    public double accumulatedPenalty = 0.0;

    public class AttackPattern {
        public long lastAttackTick;
        public double lastAttackDist;
        public double averageInterval; // 平均攻撃間隔 (Ticks)
        public double preferredDist;   // 好みの攻撃距離
        public int sampleCount;
    }

    // LiquidBrain 内にプレイヤーUUID紐付けで保持
    public Map<UUID, AttackPattern> enemyPatterns = new HashMap<>();

    public class TacticalMemory {
        public double hitRate = 0.5;       // 自分の攻撃が当たっている率
        public double dodgeRate = 0.5;     // 敵の攻撃を避けている率
        public double combatAdvantage = 0.5; // 総合的な優位性 (0.0: 絶望 ~ 1.0: 圧倒)

        // 過去10回分の攻防記録（簡略化のためカウンターで管理）
        public int myHits = 0;
        public int myMisses = 0;
        public int takenHits = 0;
        public int avoidedHits = 0;
    }

    public final TacticalMemory tacticalMemory = new TacticalMemory();

    public static class SelfPattern {
        public long lastAttackTick = 0;
        public double averageInterval = 0; // 自分の攻撃の平均間隔（スキルのクールタイム含む）
        public int sampleCount = 0;
    }

    public final SelfPattern selfPattern = new SelfPattern();

    /**
     * 自分が攻撃を繰り出した（バニラ近接 or MMスキル）瞬間に呼び出す
     */
    public void recordSelfAttack(long currentTick) {
        if (selfPattern.lastAttackTick > 0) {
            long interval = currentTick - selfPattern.lastAttackTick;
            if (interval > 5 && interval < 100) { // 短すぎる連打や長すぎる間隔を除外
                if (selfPattern.sampleCount == 0) {
                    selfPattern.averageInterval = interval;
                } else {
                    // 自分のリズムは正確なので、少し強めに学習(0.5)
                    selfPattern.averageInterval = (selfPattern.averageInterval * 0.5) + (interval * 0.5);
                }
                selfPattern.sampleCount++;
            }
        }
        selfPattern.lastAttackTick = currentTick;
    }

    /**
     * 戦術的優位性の更新
     */
    public void updateTacticalAdvantage() {
        // 自分の攻撃成功率と回避成功率から計算
        double offense = (double) tacticalMemory.myHits / Math.max(1, tacticalMemory.myHits + tacticalMemory.myMisses);
        double defense = (double) tacticalMemory.avoidedHits / Math.max(1, tacticalMemory.takenHits + tacticalMemory.avoidedHits);

        // 指数移動平均で優位性をじわじわ更新
        double currentSnapshot = (offense * 0.6) + (defense * 0.4);
        tacticalMemory.combatAdvantage = (tacticalMemory.combatAdvantage * 0.8) + (currentSnapshot * 0.2);
    }

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
            frustration = Math.max(0, frustration - 0.2);
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

    /**
     * @param isMiss 空振り（ダメージが発生していない）かどうか
     */
    public void recordAttack(UUID playerUUID, long currentTick, double distance, boolean isMiss, Vector playerDirection, Vector toMob) {
        AttackPattern p = enemyPatterns.computeIfAbsent(playerUUID, k -> new AttackPattern());

        // --- タクティカル・フィルタ ---

        // 1. 距離フィルタ (8m以上の空振りは無視)
        if (isMiss && distance > 8.0) return;

        // 2. エイムフィルタ (Mobの方向を向いていない空振りは無視)
        // ドット積が 0.7 程度（約45度以内）でなければ攻撃の意思なしと判定
        if (isMiss && playerDirection.dot(toMob) < 0.7) return;

        if (p.lastAttackTick > 0) {
            long interval = currentTick - p.lastAttackTick;

            // 3. インターバル・下限フィルタ (5tick未満の連打はノイズ)
            if (interval < 5) return;

            if (interval < 200) {
                // 空振りの場合は少し控えめに学習させる（重み 0.1）
                // 命中弾は 0.3 の重みで学習し、リズムの中心とする
                double weight = isMiss ? 0.1 : 0.3;

                p.averageInterval = (p.averageInterval * (1.0 - weight)) + (interval * weight);
                p.preferredDist = (p.preferredDist * (1.0 - weight)) + (distance * weight);
                p.sampleCount++;
            }
        }
        p.lastAttackTick = currentTick;
    }

    // V2: 事前学習モデルの適用
    public void applyProfile(BehaviorProfile profile) {
        if (profile == null) return;

        // 初期状態にバイアスをかける (例: 攻撃的な個体として事前学習されている場合)
        if (profile.neuron_biases.containsKey("aggression")) {
            this.aggression.update(profile.neuron_biases.get("aggression"), 1.0);
        }

        // 攻撃リズムの初期値を設定 (V2 頻度予測用)
        if (profile.default_attack_interval > 0) {
            AttackPattern p = new AttackPattern();
            p.averageInterval = profile.default_attack_interval;
            p.preferredDist = profile.default_attack_dist;
            p.sampleCount = 5; // 既知の知識としてカウント
            // enemyPatterns は全プレイヤー共有のベース知識として扱うか検討
        }
    }
}