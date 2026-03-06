package com.lunar_prototype.deepwither.seeker;

import java.util.Random;

/**
 * [TQH-Native] NativeQEngine
 * Rust API (Singularity) を代替する、Javaネイティブの熱力学Q-Learningエンジン。
 * 状態空間: 512 (9-bit), アクション: 8
 */
public class NativeQEngine {
    private static final int STATE_COUNT = 512;
    private static final int ACTION_COUNT = 8;

    private final float[] qTable;
    private final float[] actionStrengths; // Hamiltonian Rules 用のバイアス

    private float systemTemperature = 0.5f;
    private float frustration = 0.0f;
    private float adrenaline = 0.0f;
    private final float[] neuronStates = new float[4]; // Aggression, Fear, Tactical, Reflex

    private int lastState = -1;
    private int lastAction = -1;

    private final Random random = new Random();

    // ハイパーパラメータ
    private static final float BASE_ALPHA = 0.1f;    // 学習率
    private static final float GAMMA = 0.9f;         // 割引率
    private static final float BASE_EPSILON = 0.1f;  // 探索率

    public NativeQEngine() {
        this.qTable = new float[STATE_COUNT * ACTION_COUNT];
        this.actionStrengths = new float[STATE_COUNT * ACTION_COUNT];
    }

    /**
     * ハミルトニアン規則（本能）を注入する
     */
    public void registerHamiltonianRules(int[] conditions, int[] actions, float[] strengths) {
        for (int i = 0; i < conditions.length; i++) {
            int state = conditions[i];
            int action = actions[i];
            float strength = strengths[i];
            if (state >= 0 && state < STATE_COUNT && action >= 0 && action < ACTION_COUNT) {
                actionStrengths[state * ACTION_COUNT + action] = strength;
                // 初期Q値にも反映させておく
                qTable[state * ACTION_COUNT + action] = strength * 0.5f;
            }
        }
    }

    /**
     * 前回の行動に対する報酬を学習し、次の行動を選択する
     */
    public int update(int currentState, float reward, float[] inputs) {
        // 1. TD学習 (前回の遷移を評価)
        if (lastState != -1 && lastAction != -1) {
            float oldQ = qTable[lastState * ACTION_COUNT + lastAction];
            float maxNextQ = -Float.MAX_VALUE;
            for (int a = 0; a < ACTION_COUNT; a++) {
                maxNextQ = Math.max(maxNextQ, qTable[currentState * ACTION_COUNT + a]);
            }

            // 熱力学的学習率の調整
            float alpha = BASE_ALPHA * (1.0f + systemTemperature);
            float target = reward + GAMMA * maxNextQ;
            qTable[lastState * ACTION_COUNT + lastAction] += alpha * (target - oldQ);
        }

        // 2. パラメータの更新 (TQHシミュレーション)
        updateInternalState(reward, inputs);

        // 3. 行動選択 (Softmax or Epsilon-Greedy)
        int selectedAction = selectAction(currentState);

        this.lastState = currentState;
        this.lastAction = selectedAction;

        return selectedAction;
    }

    private void updateInternalState(float reward, float[] inputs) {
        // 報酬による冷却、ペナルティによる加熱
        if (reward > 0) {
            systemTemperature = Math.max(0.1f, systemTemperature - reward * 0.05f);
            frustration = Math.max(0.0f, frustration - reward * 0.1f);
        } else if (reward < 0) {
            systemTemperature = Math.min(2.0f, systemTemperature - reward * 0.1f);
            frustration = Math.min(1.0f, frustration - reward * 0.05f);
        }

        // 自然減衰
        systemTemperature = (systemTemperature * 0.99f) + (0.5f * 0.01f);
        frustration *= 0.98f;

        // 入力(adrenaline等)の反映
        if (inputs.length >= 5) {
            // 例: 敵の数が多いとアドレナリン上昇
            adrenaline = (adrenaline * 0.9f) + (Math.min(inputs[4], 5) / 5.0f * 0.1f);
        }
    }

    private int selectAction(int state) {
        // GAS状態 (温度が高い) 時はランダム性を増やす
        float epsilon = BASE_EPSILON * (1.0f + systemTemperature);
        if (random.nextFloat() < epsilon) {
            return random.nextInt(ACTION_COUNT);
        }

        // Q値 + Hamiltonianバイアス + 感情バイアス
        int bestAction = 0;
        float maxScore = -Float.MAX_VALUE;

        for (int a = 0; a < ACTION_COUNT; a++) {
            float qValue = qTable[state * ACTION_COUNT + a];
            float bias = actionStrengths[state * ACTION_COUNT + a];
            
            // 感情バイアスの計算 (LNNからのフィードバックを想定)
            float emotionalBias = calculateEmotionalBias(a);

            float score = qValue + bias + emotionalBias;
            if (score > maxScore) {
                maxScore = score;
                bestAction = a;
            }
        }

        return bestAction;
    }

    private float calculateEmotionalBias(int actionIdx) {
        // ACTIONS = {"ATTACK", "EVADE", "BAITING", "COUNTER", "OBSERVE", "RETREAT", "BURST_DASH", "ORBITAL_SLIDE"}
        return switch (actionIdx) {
            case 0 -> neuronStates[0] * 0.3f; // ATTACK: Aggression
            case 1 -> neuronStates[1] * 0.3f; // EVADE: Fear
            case 5 -> neuronStates[1] * 0.5f; // RETREAT: Fear
            case 6 -> neuronStates[0] * 0.4f; // BURST_DASH: Aggression
            default -> 0.0f;
        };
    }

    // Getters & Setters
    public float getSystemTemperature() { return systemTemperature; }
    public void setSystemTemperature(float temp) { this.systemTemperature = temp; }
    public float getFrustration() { return frustration; }
    public float getAdrenaline() { return adrenaline; }
    public float[] getNeuronStates() { return neuronStates; }
    public void setNeuronState(int idx, float val) { if (idx >= 0 && idx < 4) neuronStates[idx] = val; }
    public float getGliaActivity() { return frustration * 0.5f; } // 簡易実装
    public double getActionScore(int state, int actionIdx) {
        if (state < 0 || state >= STATE_COUNT) return 0.0;
        return qTable[state * ACTION_COUNT + actionIdx];
    }
}
