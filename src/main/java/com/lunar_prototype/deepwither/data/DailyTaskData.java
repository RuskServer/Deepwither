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

    // ★追加: Key: Trader ID, Value: Today's accumulated credit from sales
    private final Map<String, Integer> dailySellCredits;

    public DailyTaskData(UUID playerId) {
        this.playerId = playerId;
        this.lastResetDate = LocalDate.now();
        this.completionCounts = new HashMap<>();
        this.currentProgress = new HashMap<>();
        this.targetMobIds = new HashMap<>(); // ★初期化
        this.dailySellCredits = new HashMap<>(); // ★初期化
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
        map.put("dailySellCredits", dailySellCredits); // ★保存

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

        if (map.containsKey("dailySellCredits")) {
            data.dailySellCredits.putAll((Map<String, Integer>) map.get("dailySellCredits"));
        }

        return data;
    }

    public void checkAndReset() {
        // フィールドがnullの場合（古いデータからのロード時）に初期化
        ensureFieldsInitialized();

        if (!lastResetDate.isEqual(LocalDate.now())) {
            this.completionCounts.clear();
            this.currentProgress.clear();
            this.targetMobIds.clear(); // ★リセット
            this.dailySellCredits.clear(); // ★リセット
            this.lastResetDate = LocalDate.now();
        }
    }

    /**
     * デシリアライズ時などにフィールドがnullになるのを防止します。
     */
    private void ensureFieldsInitialized() {
        // completionCounts自体はGson/ConfigurationSerializableでMapとしてロードされるが念のため
        // currentProgress, targetMobIds, dailySellCredits は新しく追加されたフィールドなので
        // 古いデータには存在せず null になる可能性がある。
        try {
            java.lang.reflect.Field completionCountsField = DailyTaskData.class.getDeclaredField("completionCounts");
            completionCountsField.setAccessible(true);
            if (completionCountsField.get(this) == null) completionCountsField.set(this, new HashMap<>());

            java.lang.reflect.Field currentProgressField = DailyTaskData.class.getDeclaredField("currentProgress");
            currentProgressField.setAccessible(true);
            if (currentProgressField.get(this) == null) currentProgressField.set(this, new HashMap<>());

            java.lang.reflect.Field targetMobIdsField = DailyTaskData.class.getDeclaredField("targetMobIds");
            targetMobIdsField.setAccessible(true);
            if (targetMobIdsField.get(this) == null) targetMobIdsField.set(this, new HashMap<>());

            java.lang.reflect.Field dailySellCreditsField = DailyTaskData.class.getDeclaredField("dailySellCredits");
            dailySellCreditsField.setAccessible(true);
            if (dailySellCreditsField.get(this) == null) dailySellCreditsField.set(this, new HashMap<>());
        } catch (Exception e) {
            // リフレクションが失敗した場合のフォールバック（通常は発生しないはず）
            // finalフィールドへの再代入を試みる一環として
        }
    }

    public int getCompletionCount(String traderId) {
        checkAndReset();
        return completionCounts.getOrDefault(traderId, 0);
    }

    public int getTotalCompletionCount() {
        checkAndReset();
        return completionCounts.values().stream().mapToInt(Integer::intValue).sum();
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

    /**
     * 売却による本日の累計獲得信用度を取得します。
     */
    public int getDailySellCredit(String traderId) {
        checkAndReset();
        return dailySellCredits.getOrDefault(traderId, 0);
    }

    /**
     * 売却による信用度を加算します。上限(150)を超える場合は上限まで加算されます。
     * @return 実際に加算された信用度
     */
    public int addDailySellCredit(String traderId, int amount) {
        checkAndReset();
        int current = dailySellCredits.getOrDefault(traderId, 0);
        int limit = 150;
        
        if (current >= limit) return 0;
        
        int canAdd = Math.min(amount, limit - current);
        dailySellCredits.put(traderId, current + canAdd);
        return canAdd;
    }
}