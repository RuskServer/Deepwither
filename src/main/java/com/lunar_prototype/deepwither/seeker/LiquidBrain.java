package com.lunar_prototype.deepwither.seeker;

import java.util.Random;
import java.util.UUID;

public class LiquidBrain {
    // 思考状態を表すニューロン群
    public final LiquidNeuron aggression; // 攻撃性
    public final LiquidNeuron fear;       // 恐怖/慎重さ
    public final LiquidNeuron tactical;   // 戦術的思考 (遮蔽物利用など)

    // 最後に確認した敵との距離 (変化量検出用)
    public double lastEnemyDist = -1.0;

    public double accumulatedReward = 0.0;
    public double accumulatedPenalty = 0.0;

    public final LiquidNeuron reflex = new LiquidNeuron(0.3);

    public LiquidBrain(UUID uuid) {
        // UUIDから乱数生成器を作る（その個体だけの固有の性格が決まる）
        Random random = new Random(uuid.getMostSignificantBits());

        // 個体によって初期の「粘性（反応の速さ）」や「初期値」をバラつかせる
        // 例: 0.05 ~ 0.15 の間で反応速度に差をつける
        this.aggression = new LiquidNeuron(0.08 + (random.nextDouble() * 0.04));
        this.fear = new LiquidNeuron(0.08 + (random.nextDouble() * 0.04));
        this.tactical = new LiquidNeuron(0.05 + (random.nextDouble() * 0.02));

        // 初期値も少しランダムに（0.0 ~ 0.2）
        this.aggression.update(random.nextDouble() * 0.2, 1.0);
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