package com.lunar_prototype.deepwither.aethelgard;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.llm.LlmClient;
import java.util.Random;

public class QuestGenerator {

    private final LlmClient llmClient;
    private final Random random; // Randomインスタンスをフィールドで保持推奨

    // ★追加: クエスト有効期限の範囲設定 (ミリ秒)
    private static final long MIN_DURATION_MILLIS = 1000L * 60 * 60 * 1; // 最短 1時間
    private static final long MAX_DURATION_MILLIS = 1000L * 60 * 60 * 6; // 最長 6時間

    public QuestGenerator() {
        this.llmClient = new LlmClient();
        this.random = new Random();
    }

    /**
     * LLMを使用して駆除クエストを生成するメインメソッド。
     * @param difficultyLevel クエストの難易度
     * @return 生成されたGeneratedQuestオブジェクト
     */
    public GeneratedQuest generateQuest(int difficultyLevel) {
        // 1. 構成要素をランダムに決定
        ExterminationType targetType = ExterminationType.values()[random.nextInt(ExterminationType.values().length)];
        LocationDetails locationDetails = QuestComponentPool.getRandomLocationDetails();
        String motivation = QuestComponentPool.getRandomMotivation();
        int quantity = QuestComponentPool.calculateRandomQuantity(difficultyLevel);

        // 2. 報酬を決定
        QuestComponentPool.RewardValue rewardValue = QuestComponentPool.calculateBaseCurrencyAndExp(difficultyLevel);
        String rewardItemId = QuestComponentPool.getRandomRewardItemId();
        int rewardItemQuantity = QuestComponentPool.getRandomItemQuantity(rewardItemId, difficultyLevel);

        String rewardItemDisplayName = Deepwither.getInstance().getItemNameResolver().resolveItemDisplayName(rewardItemId);

        // 3. RewardDetailsを作成
        RewardDetails rewardDetails = new RewardDetails(
                rewardValue.coin,
                rewardValue.exp,
                rewardItemId,
                rewardItemDisplayName,
                rewardItemQuantity
        );

        // 4. LLM呼び出しのためのプロンプトをアセンブル
        String prompt = QuestPromptAssembler.assemblePrompt(targetType, locationDetails, motivation, quantity, rewardDetails.getLlmRewardText());

        // 5. LLMを呼び出し、依頼文を生成
        String generatedText = llmClient.generateText(prompt);

        // 6. LLM通信失敗時のフォールバック処理
        if (generatedText == null || generatedText.trim().isEmpty()) {
            System.err.println("LLM応答が不正または通信失敗。フォールバック処理を実行します。");
            generatedText = llmClient.fallbackTextGenerator(
                    locationDetails, targetType.getDescription(), motivation
            );
        }

        // 7. 生成テキストからタイトルと本文を分離
        String title = "無題のクエスト";
        String body = generatedText;

        int titleStart = generatedText.indexOf("タイトル：「");
        int titleEnd = generatedText.indexOf("」\n");
        if (titleStart != -1 && titleEnd != -1 && titleEnd > titleStart) {
            title = generatedText.substring(titleStart + "タイトル：「".length(), titleEnd).trim();
        } else {
            title = String.format("%s周辺の警戒レベル引き下げ任務", locationDetails.getName());
        }

        int bodyStart = generatedText.indexOf("本文：「");
        if (bodyStart != -1) {
            body = generatedText.substring(bodyStart + "本文：「".length()).trim();
        }

        body = body.replace("<END>", "").replaceAll("」$", "").trim();

        // ★追加: 有効期限をランダムに決定
        // 難易度に応じて時間を変えるなどのロジックもここに追加可能
        long duration = MIN_DURATION_MILLIS + (long)(random.nextDouble() * (MAX_DURATION_MILLIS - MIN_DURATION_MILLIS));

        // 8. 最終的なクエストオブジェクトを作成して返す (durationを追加)
        return new GeneratedQuest(
                title,
                body,
                targetType.getMobId(),
                quantity,
                locationDetails,
                rewardDetails,
                duration // ★コンストラクタの変更に対応
        );
    }
}