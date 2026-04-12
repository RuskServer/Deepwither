package com.lunar_prototype.deepwither.modules.outpost;

import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class OutpostConfig implements IManager {

    private final Plugin plugin;
    private final String fileName;
    private Map<String, OutpostData> outposts = new HashMap<>();
    private GlobalSettings globalSettings = new GlobalSettings();
    private ScoreWeights weights = new ScoreWeights();

    public OutpostConfig(Plugin plugin) {
        this(plugin, "outpost.yml");
    }

    public OutpostConfig(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
    }

    @Override
    public void init() throws Exception {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        this.globalSettings = loadGlobalSettings(config);
        this.weights = loadScoreWeights(config);
        this.outposts = loadOutposts(config);
    }

    @Override
    public void shutdown() {
        // No cleanup needed
    }

    // --- ロード処理メソッド ---

    private GlobalSettings loadGlobalSettings(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("global");
        if (section == null) return new GlobalSettings(); // デフォルト設定を返す

        return new GlobalSettings(
                section.getInt("check_interval_minutes", 3),
                section.getLong("cooldown_minutes", 30),
                section.getInt("min_players_online", 1),
                section.getInt("event_chance_percent", 5)
        );
    }

    private ScoreWeights loadScoreWeights(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("contribution_weights");
        if (section == null) return new ScoreWeights(); // デフォルト設定を返す

        return new ScoreWeights(
                section.getDouble("damage_dealt", 1.0),
                section.getDouble("damage_taken", 0.5),
                section.getDouble("kills", 5.0)
        );
    }

    private Map<String, OutpostData> loadOutposts(YamlConfiguration config) {
        ConfigurationSection outpostsSection = config.getConfigurationSection("outposts");
        if (outpostsSection == null) return Collections.emptyMap();

        Map<String, OutpostData> outpostMap = new HashMap<>();
        for (String id : outpostsSection.getKeys(false)) {
            ConfigurationSection dataSection = outpostsSection.getConfigurationSection(id);
            if (dataSection == null) continue;

            // Y座標のロード
            double spawnY = dataSection.getDouble("spawn_y_coordinate", 64.0); // デフォルト値 64.0

            outpostMap.put(id, new OutpostData(
                    dataSection.getString("display_name", id),
                    dataSection.getString("world_name", "world"),
                    dataSection.getString("region_name", "none"),
                    spawnY, // ★引数に追加
                    loadWaves(dataSection),
                    loadRewards(dataSection)
            ));
        }
        return outpostMap;
    }

    private Map<Integer, WaveData> loadWaves(ConfigurationSection parentSection) {
        ConfigurationSection wavesSection = parentSection.getConfigurationSection("waves");
        if (wavesSection == null) return Collections.emptyMap();

        Map<Integer, WaveData> waveMap = new HashMap<>();
        for (String key : wavesSection.getKeys(false)) {
            try {
                int waveNum = Integer.parseInt(key);
                ConfigurationSection waveSection = wavesSection.getConfigurationSection(key);
                if (waveSection == null) continue;

                Map<String, Integer> mobList = waveSection.getConfigurationSection("mob_list").getKeys(false).stream()
                        .collect(Collectors.toMap(
                                mobId -> mobId,
                                mobId -> waveSection.getInt("mob_list." + mobId)
                        ));

                waveMap.put(waveNum, new WaveData(
                        waveSection.getLong("duration_seconds", 60),
                        mobList
                ));
            } catch (NumberFormatException e) {
                // ウェーブキーが整数でない場合の処理
            }
        }
        return waveMap;
    }

    private Rewards loadRewards(ConfigurationSection parentSection) {
        ConfigurationSection rewardsSection = parentSection.getConfigurationSection("rewards");
        if (rewardsSection == null) {
            Bukkit.getLogger().warning("[OutpostConfig] - 報酬セクションが見つかりません。");
            return new Rewards();
        }

        Bukkit.getLogger().info("[OutpostConfig] - 報酬設定をロード中...");

        // ★修正: rewardsSectionから直接リストを取得し、loadRewardListに渡す
        List<?> topList = rewardsSection.getList("top_contributor");
        List<?> avgList = rewardsSection.getList("average_contributor");
        List<?> minList = rewardsSection.getList("minimum_reward");

        return new Rewards(
                loadRewardList(topList, "top_contributor"),
                loadRewardList(avgList, "average_contributor"),
                loadRewardList(minList, "minimum_reward")
        );
    }

    // ★修正: 引数として List<?> とデバッグ用のセクション名を受け取る
    private List<RewardItem> loadRewardList(List<?> rawList, String sectionName) {
        if (rawList == null) {
            Bukkit.getLogger().warning("[OutpostConfig] -- 報酬ティア '" + sectionName + "' は null です。設定を確認してください。");
            return Collections.emptyList();
        }

        List<RewardItem> rewards = rawList.stream()
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<?, ?>) obj)
                .map(map -> {
                    try {
                        String customItemId = (String) map.get("custom_item_id");
                        int min = ((Number) map.get("min_quantity")).intValue();
                        int max = ((Number) map.get("max_quantity")).intValue();
                        return new RewardItem(customItemId, min, max);
                    } catch (Exception e) {
                        Bukkit.getLogger().severe("[OutpostConfig] -- 報酬アイテムのパース中にエラーが発生しました (" + sectionName + "): " + map.toString());
                        e.printStackTrace();
                        return null; // パース失敗
                    }
                })
                .filter(Objects::nonNull) // パース失敗した要素を削除
                .collect(Collectors.toList());

        Bukkit.getLogger().info("[OutpostConfig] -- 報酬ティア '" + sectionName + "': アイテム " + rewards.size() + " 個ロード完了。");
        return rewards;
    }

    // --- ゲッター ---
    public Map<String, OutpostData> getOutposts() { return outposts; }
    public GlobalSettings getGlobalSettings() { return globalSettings; }
    public ScoreWeights getWeights() { return weights; }

    // --- ネストされた静的クラス: 設定階層の表現 ---

    public static class GlobalSettings {
        private final int checkIntervalMinutes;
        private final long cooldownMinutes;
        private final int minPlayersOnline;
        private final int eventChancePercent;

        // コンストラクタ (省略)
        public GlobalSettings() {
            this(3, 30, 1, 5); // デフォルト値
        }
        public GlobalSettings(int checkIntervalMinutes, long cooldownMinutes, int minPlayersOnline, int eventChancePercent) {
            this.checkIntervalMinutes = checkIntervalMinutes;
            this.cooldownMinutes = cooldownMinutes;
            this.minPlayersOnline = minPlayersOnline;
            this.eventChancePercent = eventChancePercent;
        }
        // ゲッター (省略)
        public int getCheckIntervalMinutes() { return checkIntervalMinutes; }
        public long getCooldownMinutes() { return cooldownMinutes; }
        public int getMinPlayersOnline() { return minPlayersOnline; }
        public int getEventChancePercent() { return eventChancePercent; }
    }

    public static class ScoreWeights {
        private final double damageDealt;
        private final double damageTaken;
        private final double kills;

        // コンストラクタ (省略)
        public ScoreWeights() {
            this(1.0, 0.5, 5.0); // デフォルト値
        }
        public ScoreWeights(double damageDealt, double damageTaken, double kills) {
            this.damageDealt = damageDealt;
            this.damageTaken = damageTaken;
            this.kills = kills;
        }
        // ゲッター (省略)
        public double getDamageDealt() { return damageDealt; }
        public double getDamageTaken() { return damageTaken; }
        public double getKills() { return kills; }
    }

    public static class OutpostData {
        private final String displayName;
        private final String worldName;
        private final String regionName;
        private final double spawnYCoordinate; // ★追加
        private final Map<Integer, WaveData> waves;
        private final Rewards rewards;

        // コンストラクタ (省略)
        public OutpostData(String displayName, String worldName, String regionName, double spawnYCoordinate, Map<Integer, WaveData> waves, Rewards rewards) {
            this.displayName = displayName;
            this.worldName = worldName;
            this.regionName = regionName;
            this.spawnYCoordinate = spawnYCoordinate; // ★追加
            this.waves = waves;
            this.rewards = rewards;
        }
        // ゲッター (省略)
        public String getDisplayName() { return displayName; }
        public String getWorldName() { return worldName; }
        public String getRegionName() { return regionName; }
        public Map<Integer, WaveData> getWaves() { return waves; }
        public double getSpawnYCoordinate() { return spawnYCoordinate; } // ★追加
        public Rewards getRewards() { return rewards; }
    }

    public static class WaveData {
        private final long durationSeconds;
        private final Map<String, Integer> mobList; // mob_id: 数量

        // コンストラクタ (省略)
        public WaveData(long durationSeconds, Map<String, Integer> mobList) {
            this.durationSeconds = durationSeconds;
            this.mobList = mobList;
        }
        // ゲッター (省略)
        public long getDurationSeconds() { return durationSeconds; }
        public Map<String, Integer> getMobList() { return mobList; }
    }

    public static class Rewards {
        private final List<RewardItem> topContributor;
        private final List<RewardItem> averageContributor;
        private final List<RewardItem> minimumReward;

        // コンストラクタ (省略)
        public Rewards() {
            this(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
        public Rewards(List<RewardItem> topContributor, List<RewardItem> averageContributor, List<RewardItem> minimumReward) {
            this.topContributor = topContributor;
            this.averageContributor = averageContributor;
            this.minimumReward = minimumReward;
        }
        // ゲッター (省略)
        public List<RewardItem> getTopContributor() { return topContributor; }
        public List<RewardItem> getAverageContributor() { return averageContributor; }
        public List<RewardItem> getMinimumReward() { return minimumReward; }
    }

    public static class RewardItem {
        private final String customItemId;
        private final int minQuantity;
        private final int maxQuantity;

        // コンストラクタ (省略)
        public RewardItem(String customItemId, int minQuantity, int maxQuantity) {
            this.customItemId = customItemId;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
        }
        // ゲッター (省略)
        public String getCustomItemId() { return customItemId; }
        public int getMinQuantity() { return minQuantity; }
        public int getMaxQuantity() { return maxQuantity; }
    }
}