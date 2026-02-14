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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
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

    public void startNewTask(Player player, String traderId) {
        DailyTaskData data = getTaskData(player);
        int currentTier = getTierFromLocation(player.getLocation());
        if (currentTier == 0) currentTier = 1;

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

        if (!areaTaskKeys.isEmpty() && plugin.getRandom().nextBoolean()) {
            String selectedTaskKey = areaTaskKeys.get(plugin.getRandom().nextInt(areaTaskKeys.size()));
            ConfigurationSection taskNode = traderTasks.getConfigurationSection(selectedTaskKey);

            int targetSeconds = taskNode.getInt("seconds", 10);
            String taskName = taskNode.getString("display_name", "重要地点の調査");

            data.setProgress(traderId, 0, targetSeconds);
            data.setTargetMob(traderId, "AREA_TASK:" + selectedTaskKey);

            player.sendMessage(Component.text("[タスク] ", NamedTextColor.YELLOW)
                    .append(Component.text(traderId, NamedTextColor.GREEN))
                    .append(Component.text("からの設置・調査任務を受注しました。", NamedTextColor.WHITE)));
            player.sendMessage(Component.text("目標: ", NamedTextColor.GRAY)
                    .append(Component.text(taskName, NamedTextColor.AQUA))
                    .append(Component.text(" を完了させろ！", NamedTextColor.GRAY)));

        } else {
            FileConfiguration config = plugin.getConfig();
            List<String> mobList = config.getStringList("mob_spawns." + currentTier + ".regular_mobs");

            String targetMobId = (mobList == null || mobList.isEmpty()) ? "bandit" : mobList.get(plugin.getRandom().nextInt(mobList.size()));
            int targetCount = plugin.getRandom().nextInt(11) + 5;

            data.setProgress(traderId, 0, targetCount);
            data.setTargetMob(traderId, targetMobId);

            String displayName = targetMobId.equals("bandit") ? "バンディット" : targetMobId;
            player.sendMessage(Component.text("[タスク] ", NamedTextColor.YELLOW)
                    .append(Component.text(traderId, NamedTextColor.GREEN))
                    .append(Component.text("からの討伐任務を受注しました。", NamedTextColor.WHITE)));
            player.sendMessage(Component.text("目標: ", NamedTextColor.GRAY)
                    .append(Component.text(displayName, NamedTextColor.WHITE))
                    .append(Component.text(" を ", NamedTextColor.GRAY))
                    .append(Component.text(targetCount + "体", NamedTextColor.RED))
                    .append(Component.text(" 倒せ！", NamedTextColor.GRAY)));
        }

        dataStore.saveTaskData(data);
    }

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
                        if (tier > maxTier) maxTier = tier;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return maxTier;
    }

    public void updateKillProgress(Player player, String traderId) {
        DailyTaskData data = getTaskData(player);
        int[] progress = data.getProgress(traderId);

        if (progress[1] > 0) {
            progress[0]++;
            data.setProgress(traderId, progress[0], progress[1]);

            if (progress[0] >= progress[1]) {
                player.sendMessage(Component.text("タスク目標達成！", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text(" トレーダーに報告してください。", NamedTextColor.WHITE)));
            } else {
                String mobName = data.getTargetMob(traderId);
                String displayMobName = mobName.equals("bandit") ? "バンディット" : mobName;
                player.sendMessage(Component.text("[進捗] ", NamedTextColor.YELLOW)
                        .append(Component.text(displayMobName + "討伐: ", NamedTextColor.WHITE))
                        .append(Component.text(progress[0], NamedTextColor.GREEN))
                        .append(Component.text("/", NamedTextColor.GRAY))
                        .append(Component.text(progress[1], NamedTextColor.GRAY)));
            }
            dataStore.saveTaskData(data);
        }
    }

    public void startAreaProgress(Player player, String traderId, int targetSeconds) {
        UUID uuid = player.getUniqueId();
        if (activeCountdowns.containsKey(uuid)) return;

        DailyTaskData data = getTaskData(player);

        BukkitTask task = new BukkitRunnable() {
            int elapsed = data.getProgress(traderId)[0];

            @Override
            public void run() {
                if (!player.isOnline() || !isInTaskArea(player, traderId)) {
                    Title title = Title.title(
                            Component.text("× 中断", NamedTextColor.RED, TextDecoration.BOLD),
                            Component.text("エリアから離脱しました", NamedTextColor.GRAY)
                    );
                    player.showTitle(title);
                    activeCountdowns.remove(uuid);
                    this.cancel();
                    return;
                }

                elapsed++;
                data.setProgress(traderId, elapsed, targetSeconds);

                player.sendActionBar(Component.text("設置中... ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .append(Component.text(elapsed + " / " + targetSeconds + "s", NamedTextColor.WHITE)));

                if (elapsed >= targetSeconds) {
                    completeTask(player, traderId);
                    Title title = Title.title(
                            Component.text("完了", NamedTextColor.GREEN, TextDecoration.BOLD),
                            Component.text("指定地点への設置が完了しました", NamedTextColor.WHITE)
                    );
                    player.showTitle(title);
                    activeCountdowns.remove(uuid);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        activeCountdowns.put(uuid, task);
    }

    public void completeTask(Player player, String traderId) {
        DailyTaskData data = getTaskData(player);

        int goldReward = 1000 + plugin.getRandom().nextInt(3000);
        int creditReward = 100 + plugin.getRandom().nextInt(300);

        Deepwither.getEconomy().depositPlayer(player, goldReward);
        plugin.getCreditManager().addCredit(player.getUniqueId(), traderId, creditReward);

        player.sendMessage(Component.text("タスク完了！", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" " + traderId + "のタスクをクリアしました！", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("報酬: ", NamedTextColor.GOLD)
                .append(Component.text(Deepwither.getEconomy().format(goldReward), NamedTextColor.GOLD))
                .append(Component.text(" と ", NamedTextColor.WHITE))
                .append(Component.text(creditReward, NamedTextColor.AQUA))
                .append(Component.text(" 信用度を獲得！", NamedTextColor.WHITE)));

        data.incrementCompletionCount(traderId);
        data.setProgress(traderId, 0, 0);
        dataStore.saveTaskData(data);
    }

    public Set<String> getActiveTaskTraders(Player player) {
        DailyTaskData data = getTaskData(player);
        Set<String> activeTraders = new HashSet<>();
        for (Map.Entry<String, int[]> entry : data.getCurrentProgress().entrySet()) {
            if (entry.getValue()[1] > 0) activeTraders.add(entry.getKey());
        }
        return activeTraders;
    }

    public boolean isInTaskArea(Player player, String traderId) {
        DailyTaskData data = getTaskData(player);
        String targetMob = data.getTargetMob(traderId);
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
