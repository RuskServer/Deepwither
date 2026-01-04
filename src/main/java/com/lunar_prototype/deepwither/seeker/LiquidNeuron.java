package com.lunar_prototype.deepwither.seeker;

/**
 * リキッド・ニューロン
 * 入力に対して動的な時定数(tau)で反応するニューロンモデル。
 * 状況の緊急度が高いほど、tauが小さくなり(液体がサラサラになり)、即座に適応する。
 */
public class LiquidNeuron {
    private double state; // 現在の活性値 (0.0 ~ 1.0)

    // 基本的な減衰率 (記憶の保持力)
    private double baseDecay;

    public LiquidNeuron(double initialDecay) {
        this.baseDecay = initialDecay;
        this.state = 0.0;
    }

    /**
     * ニューロンの状態を更新
     * @param input 外部からの入力シグナル (0.0 ~ 1.0)
     * @param urgency 状況の緊迫度 (0.0 ~ 1.0) -> これが高いほど過去を捨てて今に適応する
     */
    public void update(double input, double urgency) {
        // LTC (Liquid Time-Constant) の簡易実装
        // 緊迫度が高いほど、時定数(tau)が小さくなる = 学習率(alpha)が上がる
        double dynamicAlpha = baseDecay + (urgency * (1.0 - baseDecay));

        // 数式: S_new = S_old + alpha * (Input - S_old)
        // 目標値(Input)に向かって、現在の粘性(dynamicAlpha)に従って流れる
        this.state = this.state + dynamicAlpha * (input - this.state);

        // 値のクリッピング (0.0 - 1.0)
        this.state = Math.max(0.0, Math.min(1.0, this.state));
    }

    /**
     * 他のニューロンの特性（感度）を模倣して、自分のパラメータを微調整する
     */
    public void mimic(LiquidNeuron leader, double learningRate) {
        // 内部的な「粘性（反応の速さ）」を優秀な個体に近づける
        // これにより、集団の中で「避けるのが上手い個体の反応速度」が皆に広まる
        this.baseDecay += (leader.baseDecay - this.baseDecay) * learningRate;
    }

    public double get() {
        return state;
    }

    public void reset() {
        this.state = 0.0;
    }
}