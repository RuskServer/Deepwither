package com.lunar_prototype.deepwither.aethelgard;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.MobSpawnManager;
import com.lunar_prototype.deepwither.llm.LlmClient;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestGenerator {

    private final LlmClient llmClient;
    private final Random random;

    private final Deque<String> recentTargetMobIds = new ArrayDeque<>();
    private final Deque<String> recentLocations = new ArrayDeque<>();
    private static final int RECENT_HISTORY_LIMIT = 3;
    private static final double RECENT_REPEAT_PENALTY = 0.35;

    private static final long MIN_DURATION_MILLIS = 1000L * 60 * 60 * 1;
    private static final long MAX_DURATION_MILLIS = 1000L * 60 * 60 * 6;

    private static final Pattern FLOOR_PATTERN = Pattern.compile("第([0-9一二三四五六七八九十百千]+)階層");
    private static final Pattern TIER_PATTERN = Pattern.compile("(?:^|[^a-zA-Z])t([0-9]+)(?:[^a-zA-Z]|$)");

    public QuestGenerator() {
        this.llmClient = new LlmClient();
        this.random = new Random();
    }

    public GeneratedQuest generateQuest(int difficultyLevel) {
        LocationDetails locationDetails = pickWeightedLocation(difficultyLevel);
        TargetMob targetMob = pickWeightedTargetMob(locationDetails, difficultyLevel);
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

        String prompt = QuestPromptAssembler.assemblePrompt(
                targetMob.description(),
                locationDetails,
                motivation,
                quantity,
                rewardDetails.getLlmRewardText()
        );

        String generatedText = llmClient.generateText(prompt);

        if (generatedText == null || generatedText.trim().isEmpty()) {
            System.err.println("LLM応答が不正または通信失敗。フォールバック処理を実行します。");
            generatedText = llmClient.fallbackTextGenerator(
                    locationDetails, targetMob.description(), motivation
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
                targetMob.mobId(),
                quantity,
                locationDetails,
                rewardDetails,
                duration
        );
    }

    private TargetMob pickWeightedTargetMob(LocationDetails locationDetails, int difficultyLevel) {
        int tier = getTierFromHierarchy(locationDetails.getHierarchy());

        Deepwither plugin = Deepwither.getInstance();
        MobSpawnManager spawnManager = plugin.getMobSpawnManager();
        if (spawnManager != null && tier > 0) {
            List<String> candidateMobIds = spawnManager.getQuestCandidateMobIdsByTier(tier);
            if (!candidateMobIds.isEmpty()) {
                Map<String, Double> weights = new LinkedHashMap<>();
                for (int i = 0; i < candidateMobIds.size(); i++) {
                    String mobId = candidateMobIds.get(i);
                    double baseWeight = 1.0 + (i * 0.15 * Math.max(1, difficultyLevel));
                    if (recentTargetMobIds.contains(mobId)) {
                        baseWeight *= RECENT_REPEAT_PENALTY;
                    }
                    weights.put(mobId, Math.max(0.05, baseWeight));
                }

                String selectedMobId = weightedRandom(weights, candidateMobIds.get(0));
                pushRecent(recentTargetMobIds, selectedMobId);
                return new TargetMob(selectedMobId, toDisplayName(selectedMobId));
            }
        }

        ExterminationType fallback = pickFallbackTargetType(difficultyLevel);
        pushRecent(recentTargetMobIds, fallback.getMobId());
        return new TargetMob(fallback.getMobId(), fallback.getDescription());
    }

    private ExterminationType pickFallbackTargetType(int difficultyLevel) {
        Map<ExterminationType, Double> weights = new LinkedHashMap<>();
        ExterminationType[] values = ExterminationType.values();

        for (int i = 0; i < values.length; i++) {
            ExterminationType type = values[i];
            double baseWeight = 1.0 + (i * 0.5 * Math.max(1, difficultyLevel));
            if (recentTargetMobIds.contains(type.getMobId())) {
                baseWeight *= RECENT_REPEAT_PENALTY;
            }
            weights.put(type, Math.max(0.05, baseWeight));
        }

        return weightedRandom(weights, ExterminationType.ZOMBIE_GUARD);
    }

    private LocationDetails pickWeightedLocation(int difficultyLevel) {
        List<LocationDetails> locations = QuestComponentPool.getAllLocationDetails();
        if (locations.isEmpty()) {
            throw new IllegalStateException("Quest locations are not loaded.");
        }

        Map<LocationDetails, Double> weights = new LinkedHashMap<>();
        for (LocationDetails location : locations) {
            double baseWeight = 1.0;
            String hierarchy = location.getHierarchy();

            baseWeight += getHierarchyDifficultyBias(hierarchy, difficultyLevel);

            if (recentLocations.contains(location.getName())) {
                baseWeight *= RECENT_REPEAT_PENALTY;
            }
            weights.put(location, Math.max(0.05, baseWeight));
        }

        LocationDetails selected = weightedRandom(weights, locations.get(0));
        pushRecent(recentLocations, selected.getName());
        return selected;
    }

    private double getHierarchyDifficultyBias(String hierarchyRaw, int difficultyLevel) {
        if (hierarchyRaw == null || hierarchyRaw.isEmpty()) {
            return 0.0;
        }

        String hierarchy = hierarchyRaw.toLowerCase();
        double bias = 0.0;

        if (hierarchy.contains("地下") || hierarchy.contains("深層") || hierarchy.contains("danger")) {
            bias += 0.6 * difficultyLevel;
        }

        int tier = getTierFromHierarchy(hierarchyRaw);
        if (tier > 0) {
            // 全階層に対応: 階層が深いほど重みを段階的に上げる
            bias += Math.min(1.5, 0.18 * tier) * difficultyLevel;
        }

        return bias;
    }

    private int getTierFromHierarchy(String hierarchyRaw) {
        if (hierarchyRaw == null || hierarchyRaw.isEmpty()) {
            return 0;
        }

        Matcher floorMatcher = FLOOR_PATTERN.matcher(hierarchyRaw);
        if (floorMatcher.find()) {
            String token = floorMatcher.group(1);
            if (token.chars().allMatch(Character::isDigit)) {
                return Integer.parseInt(token);
            }

            int kanjiNumber = parseKanjiNumber(token);
            if (kanjiNumber > 0) {
                return kanjiNumber;
            }
        }

        Matcher tierMatcher = TIER_PATTERN.matcher(hierarchyRaw.toLowerCase());
        if (tierMatcher.find()) {
            return Integer.parseInt(tierMatcher.group(1));
        }

        return 0;
    }

    private int parseKanjiNumber(String text) {
        int total = 0;
        int current = 0;

        for (char c : text.toCharArray()) {
            if (c == '千') {
                total += Math.max(1, current) * 1000;
                current = 0;
            } else if (c == '百') {
                total += Math.max(1, current) * 100;
                current = 0;
            } else if (c == '十') {
                total += Math.max(1, current) * 10;
                current = 0;
            } else {
                int digit = switch (c) {
                    case '一' -> 1;
                    case '二' -> 2;
                    case '三' -> 3;
                    case '四' -> 4;
                    case '五' -> 5;
                    case '六' -> 6;
                    case '七' -> 7;
                    case '八' -> 8;
                    case '九' -> 9;
                    case '零', '〇' -> 0;
                    default -> -1;
                };

                if (digit < 0) {
                    return 0;
                }
                current = digit;
            }
        }

        return total + current;
    }

    private String toDisplayName(String mobId) {
        return mobId.replace('_', ' ').replace('-', ' ').trim();
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

    private record TargetMob(String mobId, String description) {
    }
}
