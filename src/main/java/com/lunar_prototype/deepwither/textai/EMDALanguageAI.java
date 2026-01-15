package com.lunar_prototype.deepwither.textai;

import com.lunar_prototype.deepwither.seeker.LiquidNeuron;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 改良版 EMDA_LanguageAI
 * 1. 動的知識獲得 (DSRによる知識ノードの物理接続)
 * 2. 構文合成 (複数の辞書ノードを組み合わせて文章を構成)
 */
public class EMDALanguageAI {
    private final LiquidNeuron logic = new LiquidNeuron(0.1);
    private final LiquidNeuron emotion = new LiquidNeuron(0.15);
    private final LiquidNeuron fatigue = new LiquidNeuron(0.05);

    private final Map<String, Float> wordFatigueMap = new HashMap<>();
    private static final float FATIGUE_STRESS = 0.25f;
    private static final float FATIGUE_DECAY = 0.90f;

    // 辞書層：動的追加を可能にするため ConcurrentHashMap へ変更
    private final Map<Long, List<String>> seedDictionary = new ConcurrentHashMap<>();

    // [新理論] 知識タグ：特定のキーワードとColorIDを紐付ける
    private final Map<String, Long> knowledgeTags = new ConcurrentHashMap<>();

    public EMDALanguageAI() {
        // 基本辞書
        seedDictionary.put(1L, new ArrayList<>(Arrays.asList("こんにちは", "調子はどうだ？", "いい天気だな")));
        seedDictionary.put(2L, new ArrayList<>(Arrays.asList("失せろ", "何の用だ？", "邪魔をするな")));
        // 構文用パーツ (ColorID 100+: 名詞, 200+: 動詞)
        seedDictionary.put(100L, new ArrayList<>(Arrays.asList("弓兵スキルツリー", "斧のエフェクト", "クランシステム")));
        seedDictionary.put(200L, new ArrayList<>(Arrays.asList("を実装したぞ", "がリワークされた", "が追加された")));
    }

    /**
     * 知らない情報を記憶する (DSRによる焼き付け)
     */
    public void learn(String keyword, String content, long colorId) {
        knowledgeTags.put(keyword, colorId);
        seedDictionary.computeIfAbsent(colorId, k -> new ArrayList<>()).add(content);

        // 知識獲得時に一時的な「サージ」を発生させ、回路を馴染ませる
        this.logic.update(1.0, 0.9);
    }

    public String generateResponse(String input, double urgency) {
        // 1. 入力解析：キーワードが含まれているかチェック
        long targetColor = 1L; // デフォルト
        for (String key : knowledgeTags.keySet()) {
            if (input.contains(key)) {
                targetColor = knowledgeTags.get(key);
                break;
            }
        }

        // 2. LNN更新
        logic.update(targetColor >= 100L ? 1.0 : 0.0, urgency);
        emotion.update(targetColor == 2L ? 1.0 : 0.0, urgency);

        reshapeLanguageTopology(urgency);

        // 3. [新機能] 構文の組み合わせ（合成的出力）
        String response;
        if (targetColor >= 100L) {
            // 知識ノードの場合、主語 + 述語を合成
            String subject = selectBestWord(seedDictionary.get(targetColor));
            String verb = selectBestWord(seedDictionary.get(200L));
            response = subject + verb;
        } else {
            response = selectBestWord(seedDictionary.getOrDefault(targetColor, Arrays.asList("...")));
        }

        updateFatigue(response);
        return response;
    }

    private void reshapeLanguageTopology(double urgency) {
        logic.disconnect(emotion);
        if (urgency > 0.8) {
            emotion.connect(logic, -0.5f);
        } else {
            logic.connect(emotion, 0.3f);
        }
    }

    private String selectBestWord(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return "...";
        return candidates.stream()
                .max(Comparator.comparingDouble(word -> {
                    float f = wordFatigueMap.getOrDefault(word, 0.0f);
                    return 1.0 - (2.0 * f);
                })).orElse("...");
    }

    private void updateFatigue(String selected) {
        wordFatigueMap.replaceAll((k, v) -> v * FATIGUE_DECAY);
        wordFatigueMap.put(selected, wordFatigueMap.getOrDefault(selected, 0.0f) + FATIGUE_STRESS);
    }
}