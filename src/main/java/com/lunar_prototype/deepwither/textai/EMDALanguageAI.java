package com.lunar_prototype.deepwither.textai;

import com.lunar_prototype.deepwither.seeker.LiquidNeuron;
import java.util.*;

/**
 * EMDA_LanguageAI
 * 戦闘AIのLNN構造を流用した文章生成プロトタイプ
 */
public class EMDALanguageAI {
    // 意思決定層：LiquidNeuronによるポテンシャル管理
    private final LiquidNeuron logic = new LiquidNeuron(0.1);    // 論理的整合性
    private final LiquidNeuron emotion = new LiquidNeuron(0.15); // 感情的起伏
    private final LiquidNeuron fatigue = new LiquidNeuron(0.05); // 「飽き」の管理

    // 2026-01-12: Colorデータ（Particle.FLASH）を意識した量子化状態
    private long contextColorFlags = 0L;

    // 弾性Q学習用：単語・フレーズごとの疲労度 Ft(a)
    private final Map<String, Float> wordFatigueMap = new HashMap<>();
    private static final float FATIGUE_STRESS = 0.25f;
    private static final float FATIGUE_DECAY = 0.90f;

    // 擬似AI層（辞書）：使いながら覚えるための知識ベース
    private final Map<Long, List<String>> seedDictionary = new HashMap<>();

    public EMDALanguageAI() {
        // 初期辞書の注入（Colorビットフラグと単語の紐付け）
        // 例: 0x1L = 友好, 0x2L = 威圧
        seedDictionary.put(1L, Arrays.asList("こんにちは", "調子はどうだ？", "いい天気だな"));
        seedDictionary.put(2L, Arrays.asList("失せろ", "何の用だ？", "邪魔をするな"));
    }

    /**
     * 文章生成の核心部
     * @param urgency プレイヤーの接近速度や状況の緊迫度 (戦闘AIから流用)
     */
    public String generateResponse(long inputColor, double urgency) {
        // 1. LNNの更新：外部刺激(Color)をニューロンに流し込む
        logic.update(inputColor == 1L ? 1.0 : 0.0, urgency);
        emotion.update(inputColor == 2L ? 1.0 : 0.0, urgency);

        // 2. DSR（構造的再編）：緊急度が高い場合、論理をバイパスして感情を直結
        reshapeLanguageTopology(urgency);

        // 3. 候補の抽出とElastic Q-Learningによる選択
        List<String> candidates = seedDictionary.getOrDefault(inputColor, Arrays.asList("..."));
        String selected = selectBestWord(candidates);

        // 4. 疲労の蓄積と代謝
        updateFatigue(selected);

        return selected;
    }

    private void reshapeLanguageTopology(double urgency) {
        logic.disconnect(emotion);
        if (urgency > 0.8) {
            // 緊急時：論理を捨てて感情(反射)を強化
            emotion.connect(logic, -0.5f); // 論理を抑制
        } else {
            logic.connect(emotion, 0.3f);  // 平常時：論理が感情を制御
        }
    }

    private String selectBestWord(List<String> candidates) {
        return candidates.stream()
                .max(Comparator.comparingDouble(word -> {
                    float f = wordFatigueMap.getOrDefault(word, 0.0f);
                    // Q値の計算（ここでは単純化：基本スコア - 疲労度）
                    return 1.0 - (2.0 * f);
                })).orElse("...");
    }

    private void updateFatigue(String selected) {
        // 全体の疲労回復
        wordFatigueMap.replaceAll((k, v) -> v * FATIGUE_DECAY);
        // 選択された単語の疲労蓄積
        wordFatigueMap.put(selected, wordFatigueMap.getOrDefault(selected, 0.0f) + FATIGUE_STRESS);
    }
}
