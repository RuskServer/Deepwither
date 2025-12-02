package com.lunar_prototype.deepwither.quest;

import java.util.Random;

public class QuestPromptAssembler {

    /**
     * LLMへ渡すプロンプトを生成します。
     * 外部で決定された構成要素を受け取り、プロンプトをアセンブルします。
     * @param targetType 討伐対象のMobタイプ
     * @param locationDetails クエスト発生場所
     * @param motivation クエストの動機
     * @param quantity 討伐目標数
     * @return LLM推論用のプロンプト文字列
     */
    public static String assemblePrompt(ExterminationType targetType, LocationDetails locationDetails, String motivation, int quantity,String llmrewardtext) {

        String llmLocationText = locationDetails.getLlmLocationText();

        // ★ ユーザーが提供した安定動作するプロンプト本体
        String finalPrompt = String.format(
                "あなたはMMORPGのクエストギルドの受付NPCです。\n" +
                        "無駄な装飾や挨拶を避け、冷徹で事務的な文体のみを使用してください。\n" +
                        "以下の【構成要素】に基づき、プレイヤーに渡す正式な依頼文を生成します。\n\n" +

                        "【重要ルール】\n" +
                        "1. 出力はタイトル行と本文行の2行のみ。\n" +
                        "2. 依頼文以外の説明を一切書かない。\n" +
                        "3. 本文は以下の順序で必ず構成すること:\n" +
                        "   (1) 動機に基づいた背景説明\n" +
                        "   (2) 世界への影響の示唆\n" +
                        "   (3) 完了条件の明確化\n" +
                        "4. 本文は一つの段落にまとめる。\n" +
                        "5. 最後に <END> を必ず付ける。\n\n" + // LLMクライアント側で '<END>' をSTOPトークンに追加することを推奨

                        "【構成要素】\n" +
                        "場所: %s\n" +
                        "目標 Mob: %s\n" +
                        "討伐数: %d\n" +
                        "動機: %s\n\n" +
                        "報酬: %s\n\n" + // 報酬は情報として残しておく方が、LLMが依頼文の文脈を理解しやすい

                        "【出力フォーマット】\n" +
                        "タイトル：「%s周辺の警戒レベル引き下げ任務」\n" + // タイトルに「警戒レベル引き下げ」を使うことが多いので固定
                        "本文：「",
                llmLocationText, // 階層情報を含む
                targetType.getDescription(),
                quantity,
                motivation,
                llmrewardtext,
                locationDetails.getName() // タイトルにはシンプルな名前を使用
        );

        return finalPrompt;
    }
}
