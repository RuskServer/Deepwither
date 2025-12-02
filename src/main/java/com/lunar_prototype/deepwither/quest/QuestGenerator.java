package com.lunar_prototype.deepwither.quest;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.llm.LlmClient;
import java.util.Random;

public class QuestGenerator {

    private final LlmClient llmClient;

    public QuestGenerator() {
        this.llmClient = new LlmClient();
    }

    /**
     * LLMを使用して駆除クエストを生成するメインメソッド。
     * @param difficultyLevel クエストの難易度
     * @return 生成されたGeneratedQuestオブジェクト
     */
    public GeneratedQuest generateQuest(int difficultyLevel) {
        // 1. 構成要素をランダムに決定
        ExterminationType targetType = ExterminationType.values()[new Random().nextInt(ExterminationType.values().length)];
        LocationDetails locationDetails = QuestComponentPool.getRandomLocationDetails(); // LocationDetailsを取得
        String motivation = QuestComponentPool.getRandomMotivation();
        int quantity = QuestComponentPool.calculateRandomQuantity(difficultyLevel);

        // 2. 報酬を決定
        QuestComponentPool.RewardValue rewardValue = QuestComponentPool.calculateBaseCurrencyAndExp(difficultyLevel); // 通貨と経験値
        String rewardItemId = QuestComponentPool.getRandomRewardItemId(); // アイテムID
        int rewardItemQuantity = QuestComponentPool.getRandomItemQuantity(rewardItemId, difficultyLevel); // アイテム個数

        // ItemNameResolverを使用してカスタムアイテムIDから表示名を取得
        // 例: "SMALL_HEALTH_POTION" -> "小さな回復薬"
        String rewardItemDisplayName = Deepwither.getInstance().getItemNameResolver().resolveItemDisplayName(rewardItemId);

        // 3. RewardDetailsを作成 (ID、表示名、個数をすべて渡す)
        RewardDetails rewardDetails = new RewardDetails(
                rewardValue.coin,
                rewardValue.exp,
                rewardItemId,               // ゲームロジック用ID
                rewardItemDisplayName,      // LLM/表示用名
                rewardItemQuantity          // 個数
        );

        // 4. LLM呼び出しのためのプロンプトをアセンブル
        String prompt = QuestPromptAssembler.assemblePrompt(targetType,locationDetails,motivation,quantity,rewardDetails.getLlmRewardText());

        // 5. LLMを呼び出し、依頼文を生成
        String generatedText = llmClient.generateText(prompt);

        // 6. LLM通信失敗時のフォールバック処理
        if (generatedText == null || generatedText.trim().isEmpty()) {
            System.err.println("LLM応答が不正または通信失敗。フォールバック処理を実行します。");
            generatedText = llmClient.fallbackTextGenerator(
                    locationDetails, targetType.getDescription(), motivation
            );
        }

        // 7. 生成テキストからタイトルと本文を分離（LLMの出力形式に依存）
        // 安定プロンプトの形式: "タイトル：「...」\n本文：「...<END>" の形式を想定してパース
        String title = "無題のクエスト";
        String body = generatedText;

        // タイトル抽出
        int titleStart = generatedText.indexOf("タイトル：「");
        int titleEnd = generatedText.indexOf("」\n");
        if (titleStart != -1 && titleEnd != -1 && titleEnd > titleStart) {
            // "タイトル：「" の長さだけ進める
            title = generatedText.substring(titleStart + "タイトル：「".length(), titleEnd).trim();
        } else {
            // タイトルが見つからない場合、暫定的に場所名を使用
            title = String.format("%s周辺の警戒レベル引き下げ任務", locationDetails.getName());
        }

        // 本文抽出
        int bodyStart = generatedText.indexOf("本文：「");
        if (bodyStart != -1) {
            body = generatedText.substring(bodyStart + "本文：「".length()).trim();
        }

        // <END>と末尾の引用符を除去
        body = body.replace("<END>", "").replaceAll("」$", "").trim();

        // 8. 最終的なクエストオブジェクトを作成して返す
        return new GeneratedQuest(
                title,
                body,
                targetType.getMobId(), // Mob IDを使用
                quantity,
                locationDetails,
                rewardDetails
        );
    }
}