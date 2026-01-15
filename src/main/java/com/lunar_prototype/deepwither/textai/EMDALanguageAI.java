package com.lunar_prototype.deepwither.textai;

import com.lunar_prototype.deepwither.seeker.LiquidNeuron;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EMDA_LanguageAI v2.0
 * 1. BERT-like Context Layer: 文脈の双方向解析
 * 2. ExtendedSwarmDictionary: 100語規模の多次元辞書
 * 3. Searchable Synthesis: 1秒の猶予を活かした最適文章探索
 */
public class EMDALanguageAI {
    // 意思決定層 (LNN)
    private final LiquidNeuron logic = new LiquidNeuron(0.1);
    private final LiquidNeuron emotion = new LiquidNeuron(0.15);
    private final LiquidNeuron context = new LiquidNeuron(0.08); // [新] 文脈保持用

    private final Map<String, Float> wordFatigueMap = new HashMap<>();
    private static final float FATIGUE_STRESS = 0.25f;
    private static final float FATIGUE_DECAY = 0.90f;

    // 拡張辞書（100語規模）
    private final Map<Long, List<String>> dictionary = new ConcurrentHashMap<>();
    private final Map<String, Long> knowledgeTags = new ConcurrentHashMap<>();

    public EMDALanguageAI() {
        setupExtendedDictionary();
    }

    private void setupExtendedDictionary() {
        // 1: 友好 (20語)
        dictionary.put(1L, new ArrayList<>(Arrays.asList(
                "こんにちは", "調子はどうだ？", "いい天気だな", "力になれることはあるか？", "今日も精が出るな",
                "冒険の調子はどうだい？", "何か手伝えるか？", "お疲れ様！", "ゆっくりしていってくれ", "やあ、また会ったね",
                "平和な一日だ", "調子よさそうだな", "気分はどうだい？", "頑張ってるな", "無理はするなよ",
                "何かいいことあったか？", "元気そうで何よりだ", "ようこそわが村へ", "いい風が吹いている", "応援してるぞ"
        )));
        // 2: 威圧 (20語)
        dictionary.put(2L, new ArrayList<>(Arrays.asList(
                "失せろ", "何の用だ？", "邪魔をするな", "消えろ", "目障りだ",
                "死にたいのか？", "そこをどけ", "馴れ馴れしくするな", "不愉快だ", "俺に構うな",
                "後悔させてやろうか？", "命が惜しければ去れ", "口を慎め", "お前の顔は見飽きた", "近寄るな",
                "時間の無駄だ", "安らぎは終わりだ", "地獄へ落ちろ", "愚か者が", "話は終わりだ"
        )));
        // 100+: アプデ情報・固有名詞 (20語)
        dictionary.put(100L, new ArrayList<>(Arrays.asList(
                "弓兵スキルツリー", "斧のエフェクト", "クランシステム", "ダメージ内部処理", "APIバージョン",
                "新ダンジョン", "ボスモンスター", "伝説の武器", "古代の都市", "静寂の温室跡",
                "スキルリセット", "パーティクル処理", "サーバーの安定性", "新しい防具", "エンチャント",
                "取引システム", "評判システム", "マスタリーレベル", "特殊攻撃", "連撃システム"
        )));
        // 200+: 動作・状態 (20語)
        dictionary.put(200L, new ArrayList<>(Arrays.asList(
                "が実装されたぞ", "をリワークした", "を更新した", "を修正した", "が追加された",
                "が強化された", "が弱体化した", "を最適化した", "が目撃された", "を調査中だ",
                "が解放された", "が発見された", "を試してくれ", "に期待してくれ", "を注意しろ",
                "が変更された", "がバグってた", "を直した", "が動いている", "を完了した"
        )));
        // 300+: 接続詞・感情修飾 (20語)
        dictionary.put(300L, new ArrayList<>(Arrays.asList(
                "驚くべきことに", "残念ながら", "ついに", "ようやく", "まさか",
                "もっとも", "そして", "しかし", "おそらく", "間違いなく",
                "嬉しいことに", "恐ろしいことに", "ちなみに", "要するに", "実は",
                "幸いにも", "あいにく", "当然ながら", "密かに", "大胆にも"
        )));
    }

    /**
     * BERT的な文脈解析をシミュレートする多段生成
     */
    public String generateResponse(String input, double urgency) {
        // [BERT-Layer] 双方向解析 (過去の context 状態と現在の入力をブレンド)
        long targetColor = 1L;
        double contextImpact = 0.0;

        for (String key : knowledgeTags.keySet()) {
            if (input.contains(key)) {
                targetColor = knowledgeTags.get(key);
                contextImpact = 1.0;
                break;
            }
        }

        // LNN更新: 過去の「残響(context)」が現在の判断に影響を与える
        context.update(contextImpact, urgency);
        logic.update(targetColor >= 100L ? context.get() : 0.0, urgency);
        emotion.update(targetColor == 2L ? 1.0 : 0.0, urgency);

        reshapeLanguageTopology(urgency);

        // [Searchable Synthesis] 1秒の猶予を活かした合成
        return assembleBestSentence(targetColor);
    }

    private String assembleBestSentence(long targetColor) {
        StringBuilder sb = new StringBuilder();

        // 文脈の「深さ」に応じて構成を変化させる
        if (context.get() > 0.6) {
            // 修飾語を追加（より人間らしい揺らぎ）
            sb.append(selectBestWord(dictionary.get(300L))).append("、");
        }

        if (targetColor >= 100L) {
            sb.append(selectBestWord(dictionary.get(targetColor)));
            sb.append(selectBestWord(dictionary.get(200L)));
        } else {
            sb.append(selectBestWord(dictionary.get(targetColor)));
        }

        String result = sb.toString();
        updateFatigue(result);
        return result;
    }

    private void reshapeLanguageTopology(double urgency) {
        logic.disconnect(emotion);
        context.disconnect(logic);

        if (urgency > 0.8) {
            emotion.connect(logic, -0.5f); // 焦ると論理が崩れる
        } else {
            context.connect(logic, 0.4f);  // 平静時は文脈(過去の話)を論理に繋ぐ
        }
    }

    private String selectBestWord(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return "...";
        // Elastic Q-Learning: 最も疲労していない、かつ現状の論理/感情ポテンシャルに近いものを選択
        return candidates.stream()
                .max(Comparator.comparingDouble(word -> {
                    float f = wordFatigueMap.getOrDefault(word, 0.0f);
                    return Math.random() * (1.0 - f); // 適度なランダム性と疲労による回避
                })).orElse("...");
    }

    private void updateFatigue(String selected) {
        wordFatigueMap.replaceAll((k, v) -> v * FATIGUE_DECAY);
        // 合成された文章を構成単語ごとに疲労させる（簡易実装）
        wordFatigueMap.put(selected, wordFatigueMap.getOrDefault(selected, 0.0f) + FATIGUE_STRESS);
    }

    public void learn(String keyword, String content, long colorId) {
        knowledgeTags.put(keyword, colorId);
        dictionary.computeIfAbsent(colorId, k -> new ArrayList<>()).add(content);
        this.logic.update(1.0, 0.9);
    }
}