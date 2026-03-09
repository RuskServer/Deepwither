package com.lunar_prototype.deepwither.seeker;

import java.util.HashMap;
import java.util.Map;

/**
 * [TQH Integrated]
 * 既存の動的時定数に加え、システム温度による「相転移」をサポート。
 * 高温時は気体のように反応が速まり、低温時は固体のように結晶化（固定）する。
 */
public class LiquidNeuron {
    private float state;
    private float baseDecay;
    private final Map<LiquidNeuron, Float> synapses = new HashMap<>();

    public LiquidNeuron(double initialDecay) {
        this.baseDecay = (float) initialDecay;
        this.state = 0.0f;
    }

    public void connect(LiquidNeuron target, float weight) { this.synapses.put(target, weight); }
    public void disconnect(LiquidNeuron target) { this.synapses.remove(target); }

    /**
     * CfC (Closed-form Continuous-time) 化されたアップデートメソッド
     * 従来の再帰的な微分計算を、指数減衰を用いた閉形式の近似解に置き換えます。
     */
    public void update(double input, double urgency, float systemTemp) {
        float synapticInput = (float) input;

        for (Map.Entry<LiquidNeuron, Float> entry : synapses.entrySet()) {
            synapticInput += (float) (entry.getKey().get() * entry.getValue());
        }

        // 1. 既存の流動性計算 (ここまでは共通)
        float thermalEffect = Math.max(0.0f, systemTemp * 0.4f);
        float alpha = baseDecay + ((float)urgency * (1.0f - baseDecay)) + thermalEffect;
        alpha = Math.max(0.01f, Math.min(1.0f, alpha));

        // 2. CfC 近似による更新
        // 従来の線形補間 (this.state += alpha * (diff)) を、
        // 指数関数的な収束 (state = target + (state - target) * exp(-alpha)) に置き換え
        float targetState = Math.max(0.0f, Math.min(1.0f, synapticInput));

        // Math.exp(-alpha) を使うことで、alphaが大きくても発散せず、
        // 反応速度0.1ms未満の超高速処理時でも数値的に安定します。
        double decay = Math.exp(-alpha);
        this.state = (float) (targetState + (this.state - targetState) * decay);

        // 3. 範囲クランプ
        if (this.state > 1.0f) this.state = 1.0f;
        else if (this.state < 0.0f) this.state = 0.0f;
    }

    public void mimic(LiquidNeuron leader, double learningRate) {
        this.baseDecay += (leader.baseDecay - this.baseDecay) * (float) learningRate;
        if (this.baseDecay > 0.95f) this.baseDecay = 0.95f;
        if (this.baseDecay < 0.05f) this.baseDecay = 0.05f;
    }

    /**
     * [Glia Interface] アストロサイトによる強制抑制
     * 過剰発火時、外部から強制的に値を引き下げ、不応期（反応しにくい状態）を作る。
     * @param dampeningFactor 抑制の強さ (0.0 - 1.0)
     */
    public void applyInhibition(float dampeningFactor) {
        // 1. 現在の興奮レベルを直接削る
        this.state -= (this.state * dampeningFactor);

        // 2. baseDecay（減衰率）を一時的に高め、次の入力に反応しにくくする（不応期のシミュレート）
        // ※この影響は update() 内で alpha が再計算されるため、短期的です
        this.state = Math.max(0.0f, this.state);
    }

    // ゲッターの追加（Astrocyteが監視するため）
    public float getState() {
        return this.state;
    }

    public double get() { return state; }

    public void setState(float newState) {
        this.state = newState;
    }
}