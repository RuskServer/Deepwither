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

    /**
     * 指定されたエージェントIDで LiquidBrain インスタンスを初期化する。
     *
     * ネイティブの思考エンジンを生成し、初期ブートストラップ知識をエンジンに注入することで
     * エージェントの初期状態を構築する。
     *
     * @param ownerId エージェント（このBrainの所有者）を識別する UUID
     */
    public LiquidBrain(UUID ownerId) {
        this.ownerId = ownerId;
        this.engine = new NativeQEngine();

        // [TQH-Bootstrap] 初期知識（ハミルトニアン規則）の注入
        BanditKnowledgeBase.inject(this.engine);
    }

    /**
     * ブレインが保持するリソースを解放するためのフック。
     *
     * <p>現状は実際の解放処理を行わず、必要に応じて履歴のクリアなどの後始末処理を追加できる。
     */
    public void dispose() {
        // 必要に応じて履歴のクリア等
    }

    /**
     * エージェントの内部状態インデックスを設定する。
     *
     * @param conditionId 設定する状態インデックス（状態ID）
     */
    public void setCondition(int conditionId) {
        this.lastStateIdx = conditionId;
    }

    /**
     * 環境入力を与えて学習を適用し、次の行動を決定する思考サイクルを実行する。
     *
     * エンジンへ感情ニューロンの状態を反映し、蓄積報酬・罰を評価に適用して行動を選択し、
     * 選択結果に応じて内部状態をエンジンから同期する。
     *
     * @param inputs 環境／センサーからの入力を表す特徴ベクトル
     * @return 選択された行動のインデックス
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
     * 表示や演出のために脳のトポロジー表現をエンジンから同期する。
     *
     * 内部状態をネイティブエンジンから読み出して、描画や可視化に用いる表現を更新する。
     */
    public void reshapeTopology() {
        syncFromEngine();
    }

    /**
     * ネイティブエンジンの状態を読み取り、対応するフィールドと感情ニューロンの状態を更新する。
     *
     * このメソッドはエンジンから systemTemperature、frustration、adrenaline を取得して
     * Java 側の対応フィールドへ反映し、その後 aggression／fear／tactical／reflex の各
     * LiquidNeuron に対して小さなデルタで update を呼び出して内部状態を同期する。
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
     * 指定した敵に対する攻撃の差し迫り度（imminence）を計算してエンジンに通知する。
     *
     * @param targetId    攻撃対象の識別子（UUID）
     * @param currentDist 対象までの現在の距離
     */
    public void updateEnemyPrediction(UUID targetId, double currentDist) {
        float imminence = calculateAttackImminence(targetId, currentDist);
        engine.setEnemyAttackImminence(imminence);
    }

    /**
     * 入力センサ値の配列を受け取り、思考サイクルを実行して次に取るべき行動を決定する。
     *
     * @param inputs  環境・センサからの入力特徴ベクトル（状態表現）を格納した配列
     * @return        選択された行動のインデックス
     */
    public int think(float[] inputs) {
        return cycle(inputs);
    }

    /**
     * グリア活動の現在の強度を取得する。
     *
     * @return グリア活動の強度（エンジンが提供する正規化された浮動小数点値）。
     */
    public float getGliaActivity() {
        return engine.getGliaActivity();
    }

    /**
     * 現在の内部状態（lastStateIdx）に対する指定行動のエンジン内スコアを取得する。
     *
     * @param actionIdx 取得する行動のインデックス
     * @return 指定した行動に対する内部エンジンのスコア（現在の状態に基づく数値）
     */
    public double getNativeScore(int actionIdx) {
        return engine.getActionScore(lastStateIdx, actionIdx);
    }

    /**
     * ネイティブエンジンの学習結果や内部状態をJava側のフィールドに反映して同期する。
     *
     * <p>内部でエンジンから状態を取得し、関連するフィールド（例：systemTemperature, frustration, adrenaline 等）を更新する副作用がある。</p>
     */
    public void digestExperience() {
        syncFromEngine();
    }

    /**
     * 指定した敵（targetId）への攻撃記録を更新し、その敵の攻撃パターン情報と戦術メモリのヒット／ミス統計を反映する。
     *
     * 更新内容:
     * - 敵の AttackPattern の lastAttackTick を現在時刻で更新する。
     * - 前回攻撃間隔を加重平均で更新し averageInterval を調整する（初回以外）。
     * - preferredDist を距離情報に対して小さく平滑化して更新する。
     * - isMiss に応じて tacticalMemory の myMisses または myHits をインクリメントする。
     *
     * @param targetId 攻撃対象の UUID
     * @param distance その攻撃時の距離（ゲーム単位）
     * @param isMiss true の場合は攻撃が外れたことを示す（ミスなら myMisses を増加、そうでなければ myHits を増加）
     */
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
     * 指定した敵の過去の攻撃履歴と現在の距離から、次の攻撃が来る切迫度を0.0〜1.0で算出する。
     *
     * @param targetId 攻撃パターンを参照する敵のUUID
     * @param currentDist 現在の敵との距離（同クラスで扱う距離単位）
     * @return 0.0〜1.0の切迫度。値が大きいほど次の攻撃が近いことを示す。過去データが不十分な場合は0.0を返す。
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

    /**
     * 現在の脳状態をタイムスタンプ付きで戦闘履歴に記録する。
     *
     * 履歴は上限を超えた場合に最も古いエントリを削除して新しいスナップショットを追加する。
     *
     * @param action 記録するアクションのラベル（実行または選択されたアクションを表す文字列）
     */
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

    /**
     * システム温度に応じた視覚表現用の色を決定する。
     *
     * 温度が低いほど青寄り、中程度は緑、そして高いと赤を返す。
     *
     * @return RGB 値を格納した長さ3の int 配列。順序は `[R, G, B]`。低温は青、中央値は緑、高温は赤。 
     */
    public int[] getTQHFlashColor() {
        if (systemTemperature < 0.3f) return new int[]{100, 100, 255}; // SOLID (Blue)
        if (systemTemperature < 0.8f) return new int[]{150, 255, 150}; // LIQUID (Green)
        return new int[]{255, 100, 100}; // GAS (Red)
    }

    // --- 内部データ構造 ---
    public record BrainSnapshot(long tick, float temp, float morale, float frustration, String action, int[] color) {}
    /**
 * 過去の戦闘スナップショットの時系列履歴を取得する。
 *
 * 履歴は古い順から新しい順に並んだ BrainSnapshot のリストで、最大約500件に制限される。
 *
 * @return 履歴を格納した List<BrainSnapshot>（内部の可変リストへの参照）
 */
public List<BrainSnapshot> getCombatHistory() { return combatHistory; }
    public static class AttackPattern { public long lastAttackTick; public double averageInterval = 20.0; public double preferredDist = 3.0; public int sampleCount; }
    public static class TacticalMemory { public double combatAdvantage = 0.5; public int myHits, myMisses, takenHits, avoidedHits; public long lastHitTime; }
    public static class SelfPattern { public long lastAttackTick = 0; public double averageInterval = 0; public int sampleCount = 0; }
}