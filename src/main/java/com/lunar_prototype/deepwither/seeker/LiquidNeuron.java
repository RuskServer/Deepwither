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

    public double get() { return state; }
}