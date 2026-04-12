package com.lunar_prototype.deepwither.modules.outpost;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ContributionTracker {

    private final Map<UUID, Double> damageDealt = new HashMap<>();
    private final Map<UUID, Double> damageTaken = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final OutpostConfig.ScoreWeights weights;

    public ContributionTracker(OutpostConfig.ScoreWeights weights) {
        this.weights = weights;
    }

    public void addDamageDealt(UUID playerUUID, double amount) {
        damageDealt.merge(playerUUID, amount, Double::sum);
    }

    public void addDamageTaken(UUID playerUUID, double amount) {
        damageTaken.merge(playerUUID, amount, Double::sum);
    }

    public void addKill(UUID playerUUID) {
        kills.merge(playerUUID, 1, Integer::sum);
    }

    /**
     * 設定された重みを使ってプレイヤーの最終貢献度スコアを計算します。
     */
    private double calculateFinalScore(UUID playerUUID) {
        double dealt = damageDealt.getOrDefault(playerUUID, 0.0);
        double taken = damageTaken.getOrDefault(playerUUID, 0.0);
        int killCount = kills.getOrDefault(playerUUID, 0);

        // 貢献度 = (与ダメ * 重み) + (被ダメ * 重み) + (キル数 * 重み)
        return (dealt * weights.getDamageDealt()) +
                (taken * weights.getDamageTaken()) +
                (killCount * weights.getKills());
    }

    /**
     * 全参加者のスコアを計算し、降順にソートされたランキングを返します。
     */
    public List<Map.Entry<UUID, Double>> getRankings() {
        // 参加者UUIDのユニークなリストを作成
        java.util.Set<UUID> participants = new java.util.HashSet<>();
        participants.addAll(damageDealt.keySet());
        participants.addAll(damageTaken.keySet());
        participants.addAll(kills.keySet());

        // スコアを計算し、マップに格納
        Map<UUID, Double> scores = participants.stream()
                .collect(Collectors.toMap(
                        uuid -> uuid,
                        this::calculateFinalScore
                ));

        // スコアで降順ソート
        return scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .toList();
    }
}