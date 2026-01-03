package com.lunar_prototype.deepwither.seeker;

public class LiquidBrain {
    // 思考状態を表すニューロン群
    public final LiquidNeuron aggression; // 攻撃性
    public final LiquidNeuron fear;       // 恐怖/慎重さ
    public final LiquidNeuron tactical;   // 戦術的思考 (遮蔽物利用など)

    // 最後に確認した敵との距離 (変化量検出用)
    public double lastEnemyDist = -1.0;

    public double accumulatedReward = 0.0;
    public double accumulatedPenalty = 0.0;

    public LiquidBrain() {
        // aggressionは冷めにくい (baseDecay低め)
        this.aggression = new LiquidNeuron(0.05);
        // fearは反応しやすい (baseDecay高め)
        this.fear = new LiquidNeuron(0.1);
        // tacticalは中庸
        this.tactical = new LiquidNeuron(0.08);
    }

    /**
     * 思考の直前に呼ばれ、蓄積した経験をニューロンに反映してリセットする
     */
    public void digestExperience() {
        // 成功体験(Reward)は攻撃性を高め、恐怖を僅かに下げる
        if (accumulatedReward > 0) {
            aggression.update(1.0, accumulatedReward * 0.5);
            fear.update(0.0, accumulatedReward * 0.2);
        }
        // 失敗体験(Penalty)は恐怖を一気に高め、戦術的思考を促す
        if (accumulatedPenalty > 0) {
            fear.update(1.0, accumulatedPenalty * 0.8);
            tactical.update(1.0, accumulatedPenalty * 0.5);
        }

        // バッファをリセット
        accumulatedReward = 0;
        accumulatedPenalty = 0;
    }
}