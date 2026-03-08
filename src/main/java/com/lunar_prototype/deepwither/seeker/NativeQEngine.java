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
    private float enemyAttackImminence = 0.0f; // 敵の攻撃予測 (0.0 - 1.0)
    private final float[] neuronStates = new float[4]; // Aggression, Fear, Tactical, Reflex

    private int lastState = -1;
    private int lastAction = -1;

    private final Random random = new Random();

    // ハイパーパラメータ
    private static final float BASE_ALPHA = 0.1f;    // 学習率
    private static final float GAMMA = 0.9f;         // 割引率
    private static final float BASE_EPSILON = 0.1f;  /**
     * Qテーブルとハミルトニアン作用強度を保持する配列を初期化するコンストラクタ。
     *
     * qTable と actionStrengths を、STATE_COUNT と ACTION_COUNT の積の長さを持つ float 配列として割り当てる。
     */

    public NativeQEngine() {
        this.qTable = new float[STATE_COUNT * ACTION_COUNT];
        this.actionStrengths = new float[STATE_COUNT * ACTION_COUNT];
    }

    /**
     * 各状態・行動ペアに対してハミルトニアン規則（本能）によるバイアスを注入し、対応する初期Q値を設定する。
     *
     * @param conditions 各エントリが対応する状態インデックスを表す配列（ループはこの配列長で行われ、同インデックスのactionsおよびstrengthsが参照される）
     * @param actions    各エントリが対応する行動インデックスを表す配列
     * @param strengths  各エントリが注入するバイアス強度を表す配列（対応するactionStrengthsに格納され、qTableには strength * 0.5f を初期値として設定される）
     * 
     * 無効な状態／行動インデックス（範囲外）は無視される。
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
     * 前回の遷移に対するQ値を更新し、与えられた現在状態に基づいて次の行動を選択する。
     *
     * <p>処理順序:
     * 1) 前回の状態・行動が存在すればTD学習でQ値を更新する。2) 内部状態（温度・フラストレーション・アドレナリンなど）を入力と報酬に基づいて更新する。3) 更新後の状態に基づき行動選択（探索と活用のトレードオフ）を行う。</p>
     *
     * @param currentState 現在の状態インデックス（0 以上 STATE_COUNT-1 以下）
     * @param reward 前回の遷移で観測した報酬（正の値は好ましい結果、負の値は望ましくない結果を示す）
     * @param inputs センサーや外部入力の配列。長さが5以上の場合は inputs[4] がアドレナリン更新に使用される可能性がある（null または短い配列は許容される）
     * @return 選択された行動のインデックス（0 から ACTION_COUNT-1 の範囲）
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

    /**
     * 報酬と外部入力に基づいて内部状態（systemTemperature、frustration、adrenaline）を更新する。
     *
     * ポジティブな報酬では systemTemperature を下げ（最小 0.1）、frustration を減らし、
     * ネガティブな報酬では systemTemperature と frustration を増加させ（それぞれ上限 2.0／1.0）。
     * 更新後は systemTemperature と frustration に自然減衰を適用し、inputs の 5 番目要素が存在する場合は
     * その値（上限 5 ）に基づいて adrenaline を漸近的に増やす。
     *
     * @param reward 即時の報酬（正で好結果、負で罰則）
     * @param inputs センサーや環境情報の配列。length >= 5 のとき inputs[4] がアドレナリン調整に使われる（値は 5 を上限として正規化される）
     */
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

    /**
     * 状態に応じて行動を選択する。温度で拡張されたε-greedy戦略を用い、Q値・Hamiltonianバイアス・感情バイアスの合計スコアで最良アクションを決定する。
     *
     * @param state 選択対象の状態インデックス（0 以上 STATE_COUNT-1 以下を想定）
     * @return 選択されたアクションのインデックス（0..ACTION_COUNT-1） 
     */
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

    /**
     * 指定した行動に対する感情的および予測に基づくバイアスを計算する。
     *
     * @param actionIdx 0〜7の行動インデックス
     * @return 指定された行動に対するバイアス値。正の値はその行動を促進し、負の値は抑制する。 
     */
    private float calculateEmotionalBias(int actionIdx) {
        // 攻撃が予測される瞬間のバイアス
        float predictiveBias = 0.0f;
        if (enemyAttackImminence > 0.5f) {
            if (actionIdx == 1) predictiveBias = enemyAttackImminence * 0.8f; // EVADE: 回避率アップ
            if (actionIdx == 3) predictiveBias = enemyAttackImminence * 0.6f; // COUNTER: カウンター率アップ
        }

        // ACTIONS = {"ATTACK", "EVADE", "BAITING", "COUNTER", "OBSERVE", "RETREAT", "BURST_DASH", "ORBITAL_SLIDE"}
        return switch (actionIdx) {
            case 0 -> neuronStates[0] * 0.3f; // ATTACK: Aggression
            case 1 -> (neuronStates[1] * 0.3f) + predictiveBias; // EVADE: Fear + Prediction
            case 3 -> predictiveBias; // COUNTER: Prediction
            case 5 -> neuronStates[1] * 0.5f; // RETREAT: Fear
            case 6 -> neuronStates[0] * 0.4f; // BURST_DASH: Aggression
            default -> 0.0f;
        };
    }

    /**
 * 敵の攻撃が差し迫っている度合い（臨近度）を設定する。
 *
 * @param imminence 攻撃の臨近度を表す値。通常は 0.0（無）〜1.0（非常に差し迫っている）の範囲で扱われ、値が大きいほど防御的・回避的なバイアスが強くなります。
 */
    public void setEnemyAttackImminence(float imminence) { this.enemyAttackImminence = imminence; }
    /**
 * エンジンの現在のシステム温度を取得する。
 *
 * @return 現在のシステム温度（行動選択や学習率の調整に用いるスカラー値）。
 */
public float getSystemTemperature() { return systemTemperature; }
    /**
 * エージェントの内部「systemTemperature」を設定する。
 *
 * 値は学習率や探索率のスケーリングに使われる内部温度として扱われる。
 *
 * @param temp 新しい内部温度（特別な検証は行わないので必要に応じて呼び出し側で範囲を管理する）
 */
public void setSystemTemperature(float temp) { this.systemTemperature = temp; }
    /**
 * 現在のフラストレーション値を取得する。
 *
 * @return 現在のフラストレーション（通常は 0.0 から 1.0 の範囲）
 */
public float getFrustration() { return frustration; }
    /**
 * アドレナリンの現在値を取得する。
 *
 * @return 現在のアドレナリン値
 */
public float getAdrenaline() { return adrenaline; }
    /**
 * 攻撃性・恐怖・戦術・反射の4つのニューロン様状態を取得する。
 *
 * @return 長さ4のfloat配列 `[攻撃性, 恐怖, 戦術, 反射]`。配列は内部状態の参照を返すため、要素を変更するとエンジン内部の状態も変更される。
 */
public float[] getNeuronStates() { return neuronStates; }
    /**
 * 指定したインデックスのニューロン状態（攻撃、恐怖、戦術、反射の順）を設定する。
 *
 * @param idx  ニューロンのインデックス（0: 攻撃, 1: 恐怖, 2: 戦術, 3: 反射）。範囲外の値は無視される。
 * @param val  設定する状態値
 */
public void setNeuronState(int idx, float val) { if (idx >= 0 && idx < 4) neuronStates[idx] = val; }
    /**
 * グリア活動の簡易的な指標を取得する。
 *
 * <p>内部状態 `frustration` の値に基づく単純なプロキシとして振る舞う。</p>
 *
 * @return 内部の `frustration` 値の 0.5 倍
 */
public float getGliaActivity() { return frustration * 0.5f; } /**
     * 指定した状態と行動に対応するQ値を取得する。
     *
     * @param state 対象の状態インデックス（有効範囲: 0〜511）
     * @param actionIdx 対象の行動インデックス（有効範囲: 0〜7）
     * @return 指定した状態と行動のQ値。stateが有効範囲外の場合は0.0を返す。
     */
    public double getActionScore(int state, int actionIdx) {
        if (state < 0 || state >= STATE_COUNT) return 0.0;
        return qTable[state * ACTION_COUNT + actionIdx];
    }
}
