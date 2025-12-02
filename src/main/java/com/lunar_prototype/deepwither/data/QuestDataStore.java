package com.lunar_prototype.deepwither.data;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.quest.GeneratedQuest;
import com.lunar_prototype.deepwither.quest.LocationDetails;
import com.lunar_prototype.deepwither.quest.QuestLocation;
import com.lunar_prototype.deepwither.quest.RewardDetails;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * プラグインのデータフォルダ内にあるYAMLファイルを使用してクエストデータの永続化と読み込みを管理するクラス。
 * (試作版のため、Firestoreからローカルファイル保存に変更)
 */
public class QuestDataStore {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final YamlConfiguration dataConfig;

    // Firestoreの代わりにJavaPluginを受け取るように変更
    public QuestDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "quests.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * 全てのギルドロケーションのクエストをYAMLファイルに保存します。
     * Bukkit Schedulerではなく、専用の非同期ExecutorServiceを使用するように変更します。
     * @param questLocations 保存するQuestLocationのリスト
     * @return 完了Future
     */
    public CompletableFuture<Void> saveAllQuests(List<QuestLocation> questLocations) {

        // ExecutorServiceの取得（Deepwitherにキャストしてアクセス）
        ExecutorService executor = ((Deepwither) plugin).getAsyncExecutor();

        // CompletableFuture.runAsync() で専用のExecutor上で非同期に実行
        return CompletableFuture.runAsync(() -> {
            try {
                // YamlConfigurationをクリア
                dataConfig.getKeys(false).forEach(key -> dataConfig.set(key, null));

                // すべてのロケーションをシリアライズして保存
                for (QuestLocation location : questLocations) {
                    Map<String, Object> locationData = serializeLocation(location);
                    // locationIdをトップレベルのキーとして設定
                    dataConfig.createSection(location.getLocationId(), locationData);
                }

                // ファイルI/Oを実行 (非同期スレッドで実行される)
                dataConfig.save(dataFile);

            } catch (IOException e) {
                System.err.println("Error saving quests to quests.yml: " + e.getMessage());
                e.printStackTrace();
                // 例外をCompletableFutureに伝播させる
                throw new RuntimeException("Failed to save guild quest data.", e);
            }
        }, executor);
        // CompletableFutureが成功または失敗すると、対応する状態になります。
    }

    /**
     * YAMLファイルから全てのクエストを読み込みます。
     * @param initialLocations 初期化に使用するLocationデータ（ギルドID情報など）
     * @return ロードされたQuestLocationのリストを含むCompletableFuture
     */
    public CompletableFuture<List<QuestLocation>> loadAllQuests(List<QuestLocation> initialLocations) {
        CompletableFuture<List<QuestLocation>> future = new CompletableFuture<>();

        // 非同期タスクでファイルI/Oを実行
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<QuestLocation> loadedLocations = new ArrayList<>();
            YamlConfiguration loadedConfig = YamlConfiguration.loadConfiguration(dataFile); // 最新のデータをロード

            for (QuestLocation initialLocation : initialLocations) {
                String locationId = initialLocation.getLocationId();

                if (loadedConfig.contains(locationId)) {
                    // データが存在する場合
                    Map<String, Object> data = loadedConfig.getConfigurationSection(locationId).getValues(false);

                    // currentQuestsリストを取得
                    List<Map<String, Object>> questMaps = (List<Map<String, Object>>) data.get("currentQuests");
                    List<GeneratedQuest> quests = deserializeQuests(questMaps);

                    loadedLocations.add(new QuestLocation(
                            locationId,
                            (String) data.get("locationName"),
                            quests
                    ));
                } else {
                    // データがない場合は初期ロケーションをそのまま使用
                    loadedLocations.add(initialLocation);
                }
            }
            future.complete(loadedLocations);
        });

        return future;
    }

    // --- シリアライズ/デシリアライズ ヘルパー ---

    // Map<String, Object>への変換（YamlConfigurationで保存可能な形式）
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
        data.put("targetEntityType", quest.getTargetMobId());
        data.put("requiredQuantity", quest.getRequiredQuantity());
        data.put("locationName", quest.getLocationDetails());
        data.put("rewardDetails", quest.getRewardDetails());

        return data;
    }

    /**
     * MapのリストからGeneratedQuestのリストに変換します。
     * @param questMapsRaw YamlConfigurationから取得したリスト（要素はMap<String, Object>と想定）
     * @return デシリアライズされたGeneratedQuestのリスト
     */
    private List<GeneratedQuest> deserializeQuests(List<?> questMapsRaw) {
        List<GeneratedQuest> quests = new ArrayList<>();
        if (questMapsRaw == null) return quests;

        for (Object obj : questMapsRaw) {
            if (!(obj instanceof Map)) continue;

            // 安全にキャスト (YAMLのMapキーがObjectになっている可能性を考慮)
            Map<?, ?> mapRaw = (Map<?, ?>) obj;
            Map<String, Object> map = new HashMap<>();
            mapRaw.forEach((k, v) -> map.put(String.valueOf(k), v));

            try {
                // requiredQuantityはYAMLからIntegerまたはLongとして読み込まれるため、Numberとして取得しintに変換
                Object quantityObj = map.get("requiredQuantity");
                int quantity = 0; // デフォルト値
                if (quantityObj instanceof Number) {
                    quantity = ((Number) quantityObj).intValue();
                } else {
                    System.err.println("Required Quantity is not a number: " + quantityObj);
                    continue; // 処理をスキップ
                }

                // GeneratedQuestのコンストラクタは現在の定義 (title, text, entityType, quantity, locationName) に合わせます。
                GeneratedQuest quest = new GeneratedQuest(
                        (String) map.get("title"),
                        (String) map.get("questText"),
                        (String) map.get("targetEntityType"),
                        quantity,
                        (LocationDetails) map.get("locationName"),
                        (RewardDetails) map.get("rewardDetails")
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