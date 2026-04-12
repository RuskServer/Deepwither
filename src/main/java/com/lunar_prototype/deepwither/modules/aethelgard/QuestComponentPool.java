package com.lunar_prototype.deepwither.modules.aethelgard;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class QuestComponentPool implements IManager {

    private final Deepwither plugin;
    private final Random random = new Random();

    private List<LocationDetails> locations = new ArrayList<>();
    private List<String> motivations = new ArrayList<>();
    private List<String> rewardItemIds = new ArrayList<>();

    public QuestComponentPool(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws Exception {
        File configFile = new File(plugin.getDataFolder(), "guild_quest_config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("guild_quest_config.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection("quest_components");
        loadComponents(section);
    }

    @Override
    public void shutdown() {
        // Nothing to cleanup
    }

    /**
     * YAML設定セクションからクエストコンポーネントデータをロードします。
     * @param section guild_quest_config.yml の 'quest_components' セクション
     */
    public void loadComponents(ConfigurationSection section) {
        if (section == null) {
            plugin.getLogger().warning("[QuestPool Error] 'quest_components' セクションが見つかりません。デフォルト値が使用されます。");
            return;
        }

        // 1. LOCATIONSのロードとデバッグ
        List<LocationDetails> loadedLocations = new ArrayList<>();
        List<?> rawLocations = section.getList("locations");

        if (rawLocations != null) {
            for (int i = 0; i < rawLocations.size(); i++) {
                Object rawData = rawLocations.get(i);

                if (rawData instanceof LocationDetails) {
                    loadedLocations.add((LocationDetails) rawData);
                } else if (rawData instanceof Map) {
                    Map<String, Object> mapData = (Map<String, Object>) rawData;
                    try {
                        LocationDetails manualLoad = LocationDetails.deserialize(mapData);
                        if (manualLoad != null) {
                            loadedLocations.add(manualLoad);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("[QuestPool CRITICAL ERROR] Failed to deserialize location at index " + i);
                    }
                }
            }
        }

        this.locations = Collections.unmodifiableList(loadedLocations);

        // 2. MOTIVATIONSのロード
        this.motivations = section.getStringList("motivations");
        if (this.motivations.isEmpty()) {
            plugin.getLogger().warning("[QuestPool Warning] motivationsの設定が空です。");
        }

        // 3. REWARD_ITEM_IDSのロード
        this.rewardItemIds = section.getStringList("reward_item_ids");
        if (this.rewardItemIds.isEmpty()) {
            plugin.getLogger().warning("[QuestPool Warning] reward_item_idsの設定が空です。");
        }

        plugin.getLogger().info(String.format("[QuestPool Info] クエストコンポーネントをロードしました: 場所:%d, 動機:%d, 報酬:%d",
                this.locations.size(), this.motivations.size(), this.rewardItemIds.size()));
    }

    /**
     * 読み込み済みのロケーション一覧を返します。
     */
    public List<LocationDetails> getAllLocationDetails() {
        return locations;
    }

    /**
     * ランダムなLocationDetailsオブジェクトを取得します。
     */
    public LocationDetails getRandomLocationDetails() {
        if (locations.isEmpty()) return null;
        return locations.get(random.nextInt(locations.size()));
    }

    /**
     * ランダムな動機を取得します。
     */
    public String getRandomMotivation() {
        if (motivations.isEmpty()) return "不明な動機";
        return motivations.get(random.nextInt(motivations.size()));
    }

    /**
     * 難易度に基づいて討伐数を計算します。
     */
    public int calculateRandomQuantity(int difficultyLevel) {
        return 5 + (difficultyLevel * 3) + random.nextInt(16);
    }

    /**
     * ランダムな報酬アイテムIDを取得します。
     */
    public String getRandomRewardItemId() {
        if (rewardItemIds.isEmpty()) return "UNKNOWN_ITEM";
        return rewardItemIds.get(random.nextInt(rewardItemIds.size()));
    }

    /**
     * 難易度に基づいて報酬通貨と経験値の基本値を計算します。
     * @param difficultyLevel クエストの難易度
     * @return 報酬の通貨と経験値の基本値を含むRewardValue
     */
    public RewardValue calculateBaseCurrencyAndExp(int difficultyLevel) {
        int baseCoin = 50 + difficultyLevel * 100;
        int coin = baseCoin + random.nextInt(50);
        int baseExp = 200 + difficultyLevel * 300;
        int exp = baseExp + random.nextInt(100);
        return new RewardValue(coin, exp);
    }

    /**
     * ランダムなアイテム個数を決定します。
     */
    public int getRandomItemQuantity(String itemId, int difficultyLevel) {
        if (itemId.contains("POTION") || itemId.contains("FOOD")) {
            return 1 + random.nextInt(3);
        } else if (itemId.contains("SHARD")) {
            return 3 + random.nextInt(4) + (difficultyLevel / 2);
        } else {
            return 1;
        }
    }

    /**
     * 通貨と経験値を一時的に保持する内部クラス
     */
    public static class RewardValue {
        public final int coin;
        public final int exp;

        public RewardValue(int coin, int exp) {
            this.coin = coin;
            this.exp = exp;
        }
    }
}