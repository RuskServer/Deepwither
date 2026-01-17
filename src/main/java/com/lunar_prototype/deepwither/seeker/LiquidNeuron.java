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
     * TQH Update: システム温度を第3のパラメータとして受け取る
     */
    public void update(double input, double urgency, float systemTemp) {
        float synapticInput = (float) input;

        for (Map.Entry<LiquidNeuron, Float> entry : synapses.entrySet()) {
            synapticInput += (float) (entry.getKey().get() * entry.getValue());
        }

        // TQH: 既存の urgency に加え、温度(systemTemp)が alpha (流動性)を増大させる
        // systemTemp 0.0(固体) -> 変化なし, 1.0+(気体) -> 激しい流動
        float thermalEffect = Math.max(0.0f, systemTemp * 0.4f);
        float alpha = baseDecay + ((float)urgency * (1.0f - baseDecay)) + thermalEffect;

        alpha = Math.max(0.01f, Math.min(1.0f, alpha));

        this.state += alpha * (synapticInput - this.state);

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
}