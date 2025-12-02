package com.lunar_prototype.deepwither.data;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SerializableAs("DailyTaskData")
public class DailyTaskData implements ConfigurationSerializable {

    private final UUID playerId;
    private LocalDate lastResetDate;

    private final Map<String, Integer> completionCounts;

    // Key: Trader ID, Value: [0: Current Kill Count, 1: Target Kill Count]
    private final Map<String, int[]> currentProgress;

    // ★追加: Key: Trader ID, Value: Target Mob ID (Internal Name)
    private final Map<String, String> targetMobIds;

    public DailyTaskData(UUID playerId) {
        this.playerId = playerId;
        this.lastResetDate = LocalDate.now();
        this.completionCounts = new HashMap<>();
        this.currentProgress = new HashMap<>();
        this.targetMobIds = new HashMap<>(); // ★初期化
    }

    public UUID getPlayerId() {
        return playerId;
    }

    // --- Serialization (YAML保存用) ---
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("playerId", playerId.toString());
        map.put("lastResetDate", lastResetDate.toString());
        map.put("completionCounts", completionCounts);
        map.put("targetMobIds", targetMobIds); // ★保存

        Map<String, List<Integer>> progressForSerialization = new HashMap<>();
        for (Map.Entry<String, int[]> entry : currentProgress.entrySet()) {
            progressForSerialization.put(entry.getKey(), List.of(entry.getValue()[0], entry.getValue()[1]));
        }
        map.put("currentProgress", progressForSerialization);
        return map;
    }

    // --- Deserialization (YAMLロード用) ---
    public static DailyTaskData deserialize(Map<String, Object> map) {
        UUID playerId = UUID.fromString((String) map.get("playerId"));
        DailyTaskData data = new DailyTaskData(playerId);

        String dateString = (String) map.get("lastResetDate");
        data.lastResetDate = LocalDate.parse(dateString);

        if (map.containsKey("completionCounts")) {
            data.completionCounts.putAll((Map<String, Integer>) map.get("completionCounts"));
        }

        if (map.containsKey("currentProgress")) {
            Map<String, List<Integer>> progressMap = (Map<String, List<Integer>>) map.get("currentProgress");
            for (Map.Entry<String, List<Integer>> entry : progressMap.entrySet()) {
                List<Integer> list = entry.getValue();
                data.currentProgress.put(entry.getKey(), new int[]{list.get(0), list.get(1)});
            }
        }

        // ★追加: ターゲットMob IDのロード
        if (map.containsKey("targetMobIds")) {
            data.targetMobIds.putAll((Map<String, String>) map.get("targetMobIds"));
        }

        return data;
    }

    public void checkAndReset() {
        if (!lastResetDate.isEqual(LocalDate.now())) {
            this.completionCounts.clear();
            this.currentProgress.clear();
            this.targetMobIds.clear(); // ★リセット
            this.lastResetDate = LocalDate.now();
        }
    }

    public int getCompletionCount(String traderId) {
        checkAndReset();
        return completionCounts.getOrDefault(traderId, 0);
    }

    public void incrementCompletionCount(String traderId) {
        checkAndReset();
        completionCounts.put(traderId, completionCounts.getOrDefault(traderId, 0) + 1);
    }

    public int[] getProgress(String traderId) {
        checkAndReset();
        return currentProgress.getOrDefault(traderId, new int[]{0, 0});
    }

    public void setProgress(String traderId, int current, int target) {
        checkAndReset();
        currentProgress.put(traderId, new int[]{current, target});
    }

    // ★追加: ターゲットMobの設定と取得
    public void setTargetMob(String traderId, String mobId) {
        checkAndReset();
        targetMobIds.put(traderId, mobId);
    }

    public String getTargetMob(String traderId) {
        checkAndReset();
        // デフォルトは "bandit" (後方互換性のため)
        return targetMobIds.getOrDefault(traderId, "bandit");
    }

    public Map<String, int[]> getCurrentProgress() {
        checkAndReset();
        return currentProgress;
    }
}