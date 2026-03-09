package com.lunar_prototype.deepwither.seeker;

import java.util.ArrayList;
import java.util.List;

/**
 * [TQH-Bootstrap] Bandit AI の初期知識ベース
 * 量子化された状態空間に対して、ハミルトニアン規則（Potential Fields）を注入する。
 */
public class BanditKnowledgeBase {

    // Action Indices (LiquidCombatEngine.ACTIONS reference)
    // 0: ATTACK, 1: EVADE, 2: BAITING, 3: COUNTER, 4: OBSERVE, 
    // 5: RETREAT, 6: BURST_DASH, 7: ORBITAL_SLIDE
    private static final int ACT_ATTACK = 0;
    private static final int ACT_EVADE = 1;
    private static final int ACT_BAITING = 2;
    private static final int ACT_COUNTER = 3;
    private static final int ACT_OBSERVE = 4;
    private static final int ACT_RETREAT = 5;
    private static final int ACT_BURST_DASH = 6;
    private static final int ACT_ORBITAL_SLIDE = 7;

    /**
     * 量子化された全状態空間を走査してハミルトニアン規則（状態→行動＋強度）を生成し、指定したエンジンに登録する。
     *
     * このメソッドは内部で各状態をデコードし（優位度・距離・HP・回復中フラグ・敵数など）、生存、本能的攻撃、接近、包囲対策、回復時防御の一連のルールに基づいてルール群を構築してから
     * NativeQEngine#registerHamiltonianRules に配列として渡します。
     *
     * @param engine ルールを受け取る対象の NativeQEngine インスタンス
     */
    public static void inject(NativeQEngine engine) {
        List<Integer> conditions = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        List<Float> strengths = new ArrayList<>();

        // 全状態空間 (0-511) を走査してルールを適用
        for (int state = 0; state < 512; state++) {
            // 状態のデコード
            int advantage = (state >> 7) & 0x3; // 0:Low, 1:Mid, 2:High
            int dist = (state >> 5) & 0x3;      // 0:Close(<3), 1:Mid(3-7), 2:Far(>7)
            int hp = (state >> 3) & 0x3;        // 0:Low(<0.3), 1:Mid, 2:High
            boolean recovering = ((state >> 2) & 0x1) == 1;
            int enemies = state & 0x3;          // 0-3 (3 means >=3)

            // --- Rule 1: 瀕死時の撤退 (Survival Instinct) ---
            if (hp == 0) { // HP < 30%
                addRule(conditions, actions, strengths, state, ACT_RETREAT, 0.9f);
                // 敵が近い場合は回避も検討
                if (dist == 0) {
                    addRule(conditions, actions, strengths, state, ACT_EVADE, 0.7f);
                }
            }

            // --- Rule 2: 圧倒的優位時の攻撃 (Aggression) ---
            if (advantage == 2 && dist == 0) { // High Adv & Close
                addRule(conditions, actions, strengths, state, ACT_ATTACK, 0.6f);
            }

            // --- Rule 3: 遠距離での接近 (Gap Closing) ---
            if (dist == 2 && advantage >= 1) { // Far & Not Losing
                addRule(conditions, actions, strengths, state, ACT_BURST_DASH, 0.4f);
            }

            // --- Rule 4: 包囲時の離脱 (Anti-Gank) ---
            if (enemies == 3) { // Surrounded
                addRule(conditions, actions, strengths, state, ACT_ORBITAL_SLIDE, 0.5f);
                addRule(conditions, actions, strengths, state, ACT_EVADE, 0.3f);
            }

            // --- Rule 5: 回復中の防御 (Defensive Recovery) ---
            if (recovering) {
                addRule(conditions, actions, strengths, state, ACT_EVADE, 0.5f);
                if (dist == 0) {
                    addRule(conditions, actions, strengths, state, ACT_COUNTER, 0.4f);
                }
            }
        }

        // 配列に変換して注入
        int[] cArray = conditions.stream().mapToInt(i -> i).toArray();
        int[] aArray = actions.stream().mapToInt(i -> i).toArray();
        float[] sArray = new float[strengths.size()];
        for (int i = 0; i < strengths.size(); i++) sArray[i] = strengths.get(i);

        engine.registerHamiltonianRules(cArray, aArray, sArray);
    }

    /**
     * 状態と対応する行動および強度を与えられたリストへ追加する。
     *
     * @param c      ルールの条件（状態インデックス）を格納する整数リスト
     * @param a      ルールの行動（行動インデックス）を格納する整数リスト
     * @param s      ルールの強度を格納する浮動小数点リスト
     * @param state  追加する状態のインデックス
     * @param action 追加する行動のインデックス
     * @param strength 追加するルールの強度（影響度）
     */
    private static void addRule(List<Integer> c, List<Integer> a, List<Float> s, int state, int action, float strength) {
        c.add(state);
        a.add(action);
        s.add(strength);
    }
}
