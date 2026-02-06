package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.data.DailyTaskDataStore;
import com.lunar_prototype.deepwither.data.FileDailyTaskDataStore;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@DependsOn({FileDailyTaskDataStore.class})
public class DailyTaskManager implements IManager {

    private final Deepwither plugin;
    private final DailyTaskDataStore dataStore;
    private final Map<UUID, DailyTaskData> playerTaskData;

    // フィールドに追加
    private final Map<UUID, BukkitTask> activeCountdowns = new ConcurrentHashMap<>();

    private FileConfiguration taskConfig;


    public DailyTaskManager(Deepwither plugin, DailyTaskDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.playerTaskData = new ConcurrentHashMap<>();
    }

    @Override
    public void init() {
        loadTaskConfig();
    }

    @Override
    public void shutdown() {
        saveAllData();
    }

    // コンストラクタ等でタスク設定をロードするメソッド
    public void loadTaskConfig() {
        File configFile = new File(plugin.getDataFolder(), "task_config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("task_config.yml", false);
        }
        this.taskConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    public FileConfiguration getTaskConfig() {
        if (taskConfig == null) loadTaskConfig();
        return taskConfig;
    }

    // --- 既存の永続化メソッド (省略なしでそのまま使用してください) ---
    public void loadPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        if (playerTaskData.containsKey(playerId)) return;
        dataStore.loadTaskData(playerId).thenAccept(loadedData -> {
            DailyTaskData data = (loadedData != null) ? loadedData : new DailyTaskData(playerId);
            playerTaskData.put(playerId, data);
            data.checkAndReset();
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error loading daily task data: " + ex.getMessage());
            return null;
        });
    }

    public void saveAndUnloadPlayer(UUID playerId) {
        DailyTaskData data = playerTaskData.remove(playerId);
        if (data != null) {
            data.checkAndReset();
            dataStore.saveTaskData(data);
        }
    }

    public void saveAllData() {
        for (DailyTaskData data : playerTaskData.values()) {
            data.checkAndReset();
            dataStore.saveTaskData(data);
        }
    }

    public DailyTaskData getTaskData(Player player) {
        UUID playerId = player.getUniqueId();
        DailyTaskData data = playerTaskData.get(playerId);
        if (data == null) {
            data = new DailyTaskData(playerId);
        }
        data.checkAndReset();
        return data;
    }

    // --- ★変更: タスク開始ロジック ---

    public void startNewTask(Player player, String traderId) {
        DailyTaskData data = getTaskData(player);

        // 1. プレイヤーの場所からTierを取得
        int currentTier = getTierFromLocation(player.getLocation());
        if (currentTier == 0) currentTier = 1;

        // --- AREAタスクの候補を探す ---
        List<String> areaTaskKeys = new ArrayList<>();
        ConfigurationSection traderTasks = getTaskConfig().getConfigurationSection("tasks." + traderId);
        if (traderTasks != null) {
            for (String key : traderTasks.getKeys(false)) {
                ConfigurationSection taskNode = traderTasks.getConfigurationSection(key);
                if (taskNode != null && taskNode.getInt("tier") == currentTier) {
                    if ("AREA".equalsIgnoreCase(taskNode.getString("type"))) {
                        areaTaskKeys.add(key);
                    }
                }
            }
        }

        // 2. 抽選 (AREAタスクが候補にある場合、50%の確率でAREAタスクにする)
        // 確率や条件は自由に変更可能です
        if (!areaTaskKeys.isEmpty() && plugin.getRandom().nextBoolean()) {
            // --- AREAタスクを割り当てる ---
            String selectedTaskKey = areaTaskKeys.get(plugin.getRandom().nextInt(areaTaskKeys.size()));
            ConfigurationSection taskConfig = traderTasks.getConfigurationSection(selectedTaskKey);

            int targetSeconds = taskConfig.getInt("seconds", 10);
            String taskName = taskConfig.getString("display_name", "重要地点の調査");

            // データにセット (AREA型は [0]/[targetSeconds] で管理)
            data.setProgress(traderId, 0, targetSeconds);
            data.setTargetMob(traderId, "AREA_TASK:" + selectedTaskKey); // IDにプレフィックスを付けて保存

            player.sendMessage("§e[タスク] §a" + traderId + "§fからの設置・調査任務を受注しました。");
            player.sendMessage("§7目標: §b" + taskName + " §7を完了させろ！");

        } else {
            // --- 従来のキルタスクを割り当てる ---
            FileConfiguration config = plugin.getConfig();
            List<String> mobList = config.getStringList("mob_spawns." + currentTier + ".regular_mobs");

            String targetMobId;
            if (mobList == null || mobList.isEmpty()) {
                targetMobId = "bandit";
            } else {
                targetMobId = mobList.get(plugin.getRandom().nextInt(mobList.size()));
            }

            int targetCount = plugin.getRandom().nextInt(11) + 5; // 5-15

            data.setProgress(traderId, 0, targetCount);
            data.setTargetMob(traderId, targetMobId);

            String displayName = targetMobId.equals("bandit") ? "バンディット" : targetMobId;
            player.sendMessage("§e[タスク] §a" + traderId + "§fからの討伐任務を受注しました。");
            player.sendMessage("§7目標: " + displayName + " を §c" + targetCount + "体 §7倒せ！");
        }

        dataStore.saveTaskData(data);
    }

    // --- ★追加: WorldGuard連携によるTier取得 ---
    public int getTierFromLocation(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        int maxTier = 0;

        for (ProtectedRegion region : set) {
            String id = region.getId().toLowerCase();

            int tierIndex = id.indexOf("t");
            if (tierIndex != -1 && tierIndex + 1 < id.length()) {
                char nextChar = id.charAt(tierIndex + 1);

                if (Character.isDigit(nextChar)) {
                    StringBuilder tierStr = new StringBuilder();
                    int i = tierIndex + 1;
                    while (i < id.length() && Character.isDigit(id.charAt(i))) {
                        tierStr.append(id.charAt(i));
                        i++;
                    }

                    try {
                        int tier = Integer.parseInt(tierStr.toString());
                        if (tier > maxTier) {
                            maxTier = tier;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return maxTier;
    }

    // --- 進捗更新 (ロジックはListenerに委譲し、ここは更新処理のみ) ---
    public void updateKillProgress(Player player, String traderId) {
        DailyTaskData data = getTaskData(player);
        int[] progress = data.getProgress(traderId);

        if (progress[1] > 0) {
            progress[0]++;
            data.setProgress(traderId, progress[0], progress[1]);

            if (progress[0] >= progress[1]) {
                player.sendMessage("§6§lタスク目標達成！§f トレーダーに報告してください。");
            } else {
                // Mob名を取得してメッセージに含める
                String mobName = data.getTargetMob(traderId);
                String displayMobName = mobName.equals("bandit") ? "バンディット" : mobName;
                player.sendMessage("§e[進捗] " + displayMobName + "討伐: §a" + progress[0] + "§7/" + progress[1]);
            }

            dataStore.saveTaskData(data);
        }
    }

    /**
     * AREA待機タスクの進行を開始・更新する
     */
    public void startAreaProgress(Player player, String traderId, int targetSeconds) {
        UUID uuid = player.getUniqueId();
        if (activeCountdowns.containsKey(uuid)) return;

        DailyTaskData data = getTaskData(player);

        BukkitTask task = new BukkitRunnable() {
            int elapsed = data.getProgress(traderId)[0]; // 現在の進捗秒数を取得

            @Override
            public void run() {
                // 離脱判定
                if (!player.isOnline() || !isInTaskArea(player, traderId)) {
                    player.sendTitle("§c§l× 中断", "§7エリアから離脱しました", 5, 20, 5);
                    activeCountdowns.remove(uuid);
                    this.cancel();
                    return;
                }

                elapsed++;
                data.setProgress(traderId, elapsed, targetSeconds);

                // アクションバーでタルコフ風のプログレスを表示
                player.sendActionBar("§e§l設置中... " + elapsed + " / " + targetSeconds + "s");

                if (elapsed >= targetSeconds) {
                    completeTask(player, traderId);
                    player.sendTitle("§a§l完了", "§f指定地点への設置が完了しました", 10, 40, 10);
                    activeCountdowns.remove(uuid);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1秒周期

        activeCountdowns.put(uuid, task);
    }

    // --- タスク完了 ---
    public void completeTask(Player player, String traderId) {
        DailyTaskData data = getTaskData(player);

        int goldReward = 1000 + plugin.getRandom().nextInt(3000);
        int creditReward = 100 + plugin.getRandom().nextInt(300);

        Deepwither.getEconomy().depositPlayer(player, goldReward);
        plugin.getCreditManager().addCredit(player.getUniqueId(), traderId, creditReward);

        player.sendMessage("§6§lタスク完了！§f " + traderId + "のタスクをクリアしました！");
        player.sendMessage("§6報酬: §6" + Deepwither.getEconomy().format(goldReward) + " §fと §b" + creditReward + " §f信用度を獲得！");

        data.incrementCompletionCount(traderId);
        data.setProgress(traderId, 0, 0);
        // targetMobIdのリセットは必須ではないが、次回のsetで上書きされる

        dataStore.saveTaskData(data);
    }

    public Set<String> getActiveTaskTraders(Player player) {
        DailyTaskData data = getTaskData(player);
        Set<String> activeTraders = new HashSet<>();
        for (Map.Entry<String, int[]> entry : data.getCurrentProgress().entrySet()) {
            if (entry.getValue()[1] > 0) {
                activeTraders.add(entry.getKey());
            }
        }
        return activeTraders;
    }

    /**
     * プレイヤーが指定されたトレーダーのAREAタスク範囲内にいるか判定する
     */
    public boolean isInTaskArea(Player player, String traderId) {
        DailyTaskData data = getTaskData(player);
        String targetMob = data.getTargetMob(traderId);

        // AREAタスクとして保存されているかチェック
        if (targetMob == null || !targetMob.startsWith("AREA_TASK:")) return false;

        String taskKey = targetMob.replace("AREA_TASK:", "");
        ConfigurationSection config = getTaskConfig().getConfigurationSection("tasks." + traderId + "." + taskKey);

        if (config == null) return false;

        Location targetLoc = new Location(
                Bukkit.getWorld(config.getString("world", "world")),
                config.getDouble("x"),
                config.getDouble("y"),
                config.getDouble("z")
        );
        double radius = config.getDouble("radius", 3.0);

        return player.getWorld().equals(targetLoc.getWorld()) &&
                player.getLocation().distanceSquared(targetLoc) <= (radius * radius);
    }
}