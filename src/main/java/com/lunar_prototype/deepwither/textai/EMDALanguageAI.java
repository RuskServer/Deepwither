package com.lunar_prototype.deepwither.textai;

import com.lunar_prototype.deepwither.seeker.LiquidNeuron;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EMDA_LanguageAI v3.0
 * 1. Semantic Resonance (意味共鳴): 単語をLNNのポテンシャルとして扱う
 * 2. Vectorized Dictionary: 属性（Colorフラグ）を持つ単語ノード
 * 3. 1sec Simulation: 1秒間で最適な「意味の響き」を探索
 */
public class EMDALanguageAI {
    // 意思決定層 (LNN)
    private final LiquidNeuron logic = new LiquidNeuron(0.1);
    private final LiquidNeuron emotion = new LiquidNeuron(0.15);
    private final LiquidNeuron context = new LiquidNeuron(0.08);

    // 単語ノード（文字列 + 属性ベクトル）
    private static class WordNode {
        String text;
        long colorFlags; // 0x1:友好, 0x2:威圧, 0x100:知識, 0x200:動作, 0x400:修飾
        float potential; // LNNを揺らす強さ

        WordNode(String text, long flags, float pot) {
            this.text = text;
            this.colorFlags = flags;
            this.potential = pot;
        }
    }

    private final Map<Long, List<WordNode>> swarmDictionary = new ConcurrentHashMap<>();
    private final Map<String, Float> wordFatigueMap = new HashMap<>();
    private static final float FATIGUE_STRESS = 0.25f;
    private static final float FATIGUE_DECAY = 0.90f;

    public EMDALanguageAI() {
        setupVectorizedDictionary();
    }

    private void setupVectorizedDictionary() {
        // 辞書100語を「属性付きノード」として定義
        addWords(1L, 0x1L, 0.4f, "こんにちは", "調子はどうだ？", "いい天気だな", "力になれることはあるか？", "今日も精が出るな", "冒険の調子はどうだい？", "何か手伝えるか？", "お疲れ様！", "ゆっくりしていってくれ", "やあ、また会ったね", "平和な一日だ", "調子よさそうだな", "気分はどうだい？", "頑張ってるな", "無理はするなよ", "何かいいことあったか？", "元気そうで何よりだ", "ようこそわが村へ", "いい風が吹いている", "応援してるぞ");
        addWords(2L, 0x2L, 0.8f, "失せろ", "何の用だ？", "邪魔をするな", "消えろ", "目障りだ", "死にたいのか？", "そこをどけ", "馴れ馴れしくするな", "不愉快だ", "俺に構うな", "後悔させてやろうか？", "命が惜しければ去れ", "口を慎め", "お前の顔は見飽きた", "近寄るな", "時間の無駄だ", "安らぎは終わりだ", "地獄へ落ちろ", "愚か者が", "話は終わりだ");
        addWords(100L, 0x100L, 0.6f, "弓兵スキルツリー", "斧のエフェクト", "クランシステム", "ダメージ内部処理", "APIバージョン", "新ダンジョン", "ボスモンスター", "伝説の武器", "古代の都市", "静寂の温室跡", "スキルリセット", "パーティクル処理", "サーバーの安定性", "新しい防具", "エンチャント", "取引システム", "評判システム", "マスタリーレベル", "特殊攻撃", "連撃システム");
        addWords(200L, 0x200L, 0.5f, "が実装されたぞ", "をリワークした", "を更新した", "を修正した", "が追加された", "が強化された", "が弱体化した", "を最適化した", "が目撃された", "を調査中だ", "が解放された", "が発見された", "を試してくれ", "に期待してくれ", "を注意しろ", "が変更された", "がバグってた", "を直した", "が動いている", "を完了した");
        addWords(300L, 0x400L, 0.3f, "驚くべきことに", "残念ながら", "ついに", "ようやく", "まさか", "もっとも", "そして", "しかし", "おそらく", "間違いなく", "嬉しいことに", "恐ろしいことに", "ちなみに", "要するに", "実は", "幸いにも", "あいにく", "当然ながら", "密かに", "大胆にも");
    }

    private void addWords(long cat, long flags, float pot, String... texts) {
        List<WordNode> list = swarmDictionary.computeIfAbsent(cat, k -> new ArrayList<>());
        for (String t : texts) list.add(new WordNode(t, flags, pot));
    }

    /**
     * 1秒の猶予を活かした「共鳴探索」
     */
    public String generateResponse(String input, double urgency) {
        // 1. 文脈の「波形」を解析
        long matchedFlags = analyzeInputFlags(input);

        // 2. LNN更新（過去の context と現在の入力を干渉させる）
        context.update(matchedFlags != 0 ? 1.0 : 0.0, urgency);
        logic.update((matchedFlags & 0x100L) != 0 ? context.get() : 0.0, urgency);
        emotion.update((matchedFlags & 0x2L) != 0 ? 1.0 : 0.0, urgency);

        reshapeLanguageTopology(urgency);

        // 3. 1秒のシミュレーション：ポテンシャルに「共鳴」する単語セットを探索
        return simulateBestResonance(matchedFlags);
    }

    private String simulateBestResonance(long matchedFlags) {
        // logic, emotion, context の現在の状態を「目標波形」とする
        double targetL = logic.get();
        double targetE = emotion.get();

        StringBuilder sb = new StringBuilder();

        // 修飾語の選択（感情ポテンシャルが高い時につく）
        if (targetE > 0.6 || context.get() > 0.5) {
            sb.append(resonate(300L, targetL, targetE)).append("、");
        }

        // 本文の合成
        if ((matchedFlags & 0x100L) != 0) {
            sb.append(resonate(100L, targetL, targetE));
            sb.append(resonate(200L, targetL, targetE));
        } else {
            long cat = (targetE > 0.5) ? 2L : 1L;
            sb.append(resonate(cat, targetL, targetE));
        }

        String result = sb.toString();
        updateFatigue(result);
        return result;
    }

    private String resonate(long category, double targetL, double targetE) {
        List<WordNode> nodes = swarmDictionary.get(category);
        if (nodes == null) return "...";

        // 単語の潜在能力(potential)とLNNの状態がどれだけ「共鳴」するかで選ぶ
        return nodes.stream()
                .max(Comparator.comparingDouble(node -> {
                    float f = wordFatigueMap.getOrDefault(node.text, 0.0f);
                    // 距離計算：LNNの目標状態に近いポテンシャルを持つ単語ほど選ばれやすい（Attentionの模倣）
                    double resonance = 1.0 - Math.abs(node.potential - (targetL + targetE)/2.0);
                    return resonance * (1.0 - f) * Math.random();
                })).map(n -> n.text).orElse("...");
    }

    private long analyzeInputFlags(String input) {
        long flags = 0;
        if (input.contains("弓") || input.contains("スキル") || input.contains("Ver")) flags |= 0x100L;
        if (input.contains("殺") || input.contains("敵") || input.contains("どけ")) flags |= 0x2L;
        return flags;
    }

    private void reshapeLanguageTopology(double urgency) {
        logic.disconnect(emotion);
        context.disconnect(logic);
        if (urgency > 0.8) emotion.connect(logic, -0.5f);
        else context.connect(logic, 0.4f);
    }

    private void updateFatigue(String selected) {
        wordFatigueMap.replaceAll((k, v) -> v * FATIGUE_DECAY);
        wordFatigueMap.put(selected, wordFatigueMap.getOrDefault(selected, 0.0f) + FATIGUE_STRESS);
    }
}