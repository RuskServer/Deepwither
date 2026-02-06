package com.lunar_prototype.deepwither.aethelgard;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.llm.LlmClient;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class QuestGenerator {

    private final LlmClient llmClient;
    private final Random random;

    // 直近の抽選結果を記録し、同一構成の連続出現を抑える
    private final Deque<ExterminationType> recentTargets = new ArrayDeque<>();
    private final Deque<String> recentLocations = new ArrayDeque<>();
    private static final int RECENT_HISTORY_LIMIT = 3;
    private static final double RECENT_REPEAT_PENALTY = 0.35;

    private static final long MIN_DURATION_MILLIS = 1000L * 60 * 60 * 1;
    private static final long MAX_DURATION_MILLIS = 1000L * 60 * 60 * 6;

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
        ExterminationType targetType = pickWeightedTargetType(difficultyLevel);
        LocationDetails locationDetails = pickWeightedLocation(difficultyLevel);
        String motivation = QuestComponentPool.getRandomMotivation();
        int quantity = QuestComponentPool.calculateRandomQuantity(difficultyLevel);

        QuestComponentPool.RewardValue rewardValue = QuestComponentPool.calculateBaseCurrencyAndExp(difficultyLevel);
        String rewardItemId = QuestComponentPool.getRandomRewardItemId();
        int rewardItemQuantity = QuestComponentPool.getRandomItemQuantity(rewardItemId, difficultyLevel);

        String rewardItemDisplayName = Deepwither.getInstance().getItemNameResolver().resolveItemDisplayName(rewardItemId);

        RewardDetails rewardDetails = new RewardDetails(
                rewardValue.coin,
                rewardValue.exp,
                rewardItemId,
                rewardItemDisplayName,
                rewardItemQuantity
        );

        String prompt = QuestPromptAssembler.assemblePrompt(targetType, locationDetails, motivation, quantity, rewardDetails.getLlmRewardText());

        String generatedText = llmClient.generateText(prompt);

        if (generatedText == null || generatedText.trim().isEmpty()) {
            System.err.println("LLM応答が不正または通信失敗。フォールバック処理を実行します。");
            generatedText = llmClient.fallbackTextGenerator(
                    locationDetails, targetType.getDescription(), motivation
            );
        }

        String title;
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

        long duration = MIN_DURATION_MILLIS + (long) (random.nextDouble() * (MAX_DURATION_MILLIS - MIN_DURATION_MILLIS));

        return new GeneratedQuest(
                title,
                body,
                targetType.getMobId(),
                quantity,
                locationDetails,
                rewardDetails,
                duration
        );
    }

    private ExterminationType pickWeightedTargetType(int difficultyLevel) {
        Map<ExterminationType, Double> weights = new LinkedHashMap<>();
        ExterminationType[] values = ExterminationType.values();

        for (int i = 0; i < values.length; i++) {
            ExterminationType type = values[i];
            // 高難易度ほど後ろのMobを引きやすくする
            double baseWeight = 1.0 + (i * 0.5 * Math.max(1, difficultyLevel));
            if (recentTargets.contains(type)) {
                baseWeight *= RECENT_REPEAT_PENALTY;
            }
            weights.put(type, Math.max(0.05, baseWeight));
        }

        ExterminationType selected = weightedRandom(weights, ExterminationType.ZOMBIE_GUARD);
        pushRecent(recentTargets, selected);
        return selected;
    }

    private LocationDetails pickWeightedLocation(int difficultyLevel) {
        List<LocationDetails> locations = QuestComponentPool.getAllLocationDetails();
        if (locations.isEmpty()) {
            throw new IllegalStateException("Quest locations are not loaded.");
        }

        Map<LocationDetails, Double> weights = new LinkedHashMap<>();
        for (LocationDetails location : locations) {
            double baseWeight = 1.0;
            String hierarchy = location.getHierarchy().toLowerCase();

            if (hierarchy.contains("地下") || hierarchy.contains("深層") || hierarchy.contains("danger")) {
                baseWeight += 0.6 * difficultyLevel;
            }

            if (recentLocations.contains(location.getName())) {
                baseWeight *= RECENT_REPEAT_PENALTY;
            }
            weights.put(location, Math.max(0.05, baseWeight));
        }

        LocationDetails selected = weightedRandom(weights, locations.get(0));
        pushRecent(recentLocations, selected.getName());
        return selected;
    }

    private <T> T weightedRandom(Map<T, Double> weights, T fallback) {
        double total = 0.0;
        for (double weight : weights.values()) {
            total += Math.max(0.0, weight);
        }

        if (total <= 0.0) {
            return fallback;
        }

        double threshold = random.nextDouble() * total;
        double cumulative = 0.0;
        for (Map.Entry<T, Double> entry : weights.entrySet()) {
            cumulative += Math.max(0.0, entry.getValue());
            if (threshold <= cumulative) {
                return entry.getKey();
            }
        }

        return fallback;
    }

    private <T> void pushRecent(Deque<T> deque, T item) {
        deque.addLast(item);
        while (deque.size() > RECENT_HISTORY_LIMIT) {
            deque.removeFirst();
        }
    }
}
