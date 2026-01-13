package com.lunar_prototype.deepwither.seeker;

/**
 * リキッド・ニューロン
 * 入力に対して動的な時定数(tau)で反応するニューロンモデル。
 * 状況の緊急度が高いほど、tauが小さくなり(液体がサラサラになり)、即座に適応する。
 */
public class LiquidNeuron {
    private float state;
    private float baseDecay;

    public LiquidNeuron(double initialDecay) {
        this.baseDecay = (float) initialDecay;
        this.state = 0.0f;
    }

    public void update(double input, double urgency) {
        // dynamicAlpha = baseDecay + (urgency * (1.0 - baseDecay))
        float alpha = baseDecay + ((float)urgency * (1.0f - baseDecay));
        this.state += alpha * ((float)input - this.state);
        if (this.state > 1.0f) this.state = 1.0f;
        else if (this.state < 0.0f) this.state = 0.0f;
    }

    /**
     * 他のニューロンの特性（感度）を模倣して、自分のパラメータを微調整する
     * 量子化(float)対応版
     */
    public void mimic(LiquidNeuron leader, double learningRate) {
        // leader.baseDecay も float になっているため、キャストなしで直接演算
        // 感度（反応の粘性）を集団のリーダーに同期させる
        this.baseDecay += (leader.baseDecay - this.baseDecay) * (float) learningRate;

        // baseDecay が極端な値（0以下や1以上）にならないようクリッピング
        if (this.baseDecay > 0.95f) this.baseDecay = 0.95f;
        if (this.baseDecay < 0.05f) this.baseDecay = 0.05f;
    }

    public double get() {
        return state;
    }
}