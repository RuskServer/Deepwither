package com.lunar_prototype.deepwither.data;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.aethelgard.GeneratedQuest;
import com.lunar_prototype.deepwither.aethelgard.LocationDetails;
import com.lunar_prototype.deepwither.aethelgard.QuestLocation;
import com.lunar_prototype.deepwither.aethelgard.RewardDetails;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * プラグインのデータフォルダ内にあるYAMLファイルを使用してクエストデータの永続化と読み込みを管理するクラス。
 */
public class QuestDataStore {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final YamlConfiguration dataConfig;

    public QuestDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "quests.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * 全てのギルドロケーションのクエストをYAMLファイルに保存します。
     */
    public CompletableFuture<Void> saveAllQuests(List<QuestLocation> questLocations) {
        ExecutorService executor = ((Deepwither) plugin).getAsyncExecutor();

        return CompletableFuture.runAsync(() -> {
            try {
                // YamlConfigurationをクリア
                dataConfig.getKeys(false).forEach(key -> dataConfig.set(key, null));

                // すべてのロケーションをシリアライズして保存
                for (QuestLocation location : questLocations) {
                    Map<String, Object> locationData = serializeLocation(location);
                    dataConfig.createSection(location.getLocationId(), locationData);
                }

                dataConfig.save(dataFile);

            } catch (IOException e) {
                System.err.println("Error saving quests to quests.yml: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Failed to save guild quest data.", e);
            }
        }, executor);
    }

    /**
     * YAMLファイルから全てのクエストを読み込みます。
     */
    public CompletableFuture<List<QuestLocation>> loadAllQuests(List<QuestLocation> initialLocations) {
        CompletableFuture<List<QuestLocation>> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<QuestLocation> loadedLocations = new ArrayList<>();
            YamlConfiguration loadedConfig = YamlConfiguration.loadConfiguration(dataFile);

            for (QuestLocation initialLocation : initialLocations) {
                String locationId = initialLocation.getLocationId();

                if (loadedConfig.contains(locationId)) {
                    Map<String, Object> data = loadedConfig.getConfigurationSection(locationId).getValues(false);

                    List<Map<String, Object>> questMaps = (List<Map<String, Object>>) data.get("currentQuests");
                    List<GeneratedQuest> quests = deserializeQuests(questMaps);

                    loadedLocations.add(new QuestLocation(
                            locationId,
                            (String) data.get("locationName"),
                            quests
                    ));
                } else {
                    loadedLocations.add(initialLocation);
                }
            }
            future.complete(loadedLocations);
        });

        return future;
    }

    // --- シリアライズ/デシリアライズ ヘルパー ---

    private Map<String, Object> serializeLocation(QuestLocation location) {
        Map<String, Object> data = new HashMap<>();
        data.put("locationId", location.getLocationId());
        data.put("locationName", location.getLocationName());

        List<Map<String, Object>> questList = location.getCurrentQuests().stream()
                .map(this::serializeQuest)
                .toList();
        data.put("currentQuests", questList);

        return data;
    }

    // GeneratedQuestをMap<String, Object>に変換
    private Map<String, Object> serializeQuest(GeneratedQuest quest) {
        Map<String, Object> data = new HashMap<>();
        data.put("questId", quest.getQuestId().toString());
        data.put("title", quest.getTitle());
        data.put("questText", quest.getQuestText());
        data.put("targetEntityType", quest.getTargetMobId()); // targetMobId
        data.put("requiredQuantity", quest.getRequiredQuantity());
        data.put("locationName", quest.getLocationDetails()); // locationDetails
        data.put("rewardDetails", quest.getRewardDetails());

        // ★追加: 有効期限(絶対時刻ミリ秒)を保存
        data.put("expirationTime", quest.getExpirationTime());

        return data;
    }

    /**
     * MapのリストからGeneratedQuestのリストに変換します。
     */
    private List<GeneratedQuest> deserializeQuests(List<?> questMapsRaw) {
        List<GeneratedQuest> quests = new ArrayList<>();
        if (questMapsRaw == null) return quests;

        for (Object obj : questMapsRaw) {
            if (!(obj instanceof Map)) continue;

            Map<?, ?> mapRaw = (Map<?, ?>) obj;
            Map<String, Object> map = new HashMap<>();
            mapRaw.forEach((k, v) -> map.put(String.valueOf(k), v));

            try {
                Object quantityObj = map.get("requiredQuantity");
                int quantity = 0;
                if (quantityObj instanceof Number) {
                    quantity = ((Number) quantityObj).intValue();
                } else {
                    continue;
                }

                // ★追加: 有効期限の読み込み処理
                Object expirationObj = map.get("expirationTime");
                long expirationTime;

                if (expirationObj instanceof Number) {
                    expirationTime = ((Number) expirationObj).longValue();
                } else {
                    // 古いデータなどで存在しない場合は、仮に現在から24時間後とする
                    expirationTime = System.currentTimeMillis() + 86400000L;
                }

                // ★重要: GeneratedQuestのコンストラクタは「有効期間(duration)」を受け取って「現在時刻+期間」で期限を設定する仕様に変更されたため、
                // ここでは「保存された期限 - 現在時刻」を計算して渡すことで、元の期限時刻を復元する。
                // (値がマイナスになれば、生成直後に期限切れとして扱われるので整合性は保たれる)
                long durationToRestore = expirationTime - System.currentTimeMillis();

                GeneratedQuest quest = new GeneratedQuest(
                        (String) map.get("title"),
                        (String) map.get("questText"),
                        (String) map.get("targetEntityType"),
                        quantity,
                        (LocationDetails) map.get("locationName"),
                        (RewardDetails) map.get("rewardDetails"),
                        durationToRestore // ★追加: 復元された期間
                );
                quests.add(quest);

            } catch (Exception e) {
                System.err.println("Failed to deserialize quest data: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return quests;
    }
}