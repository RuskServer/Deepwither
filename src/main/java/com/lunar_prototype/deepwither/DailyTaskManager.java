package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.data.DailyTaskDataStore;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DailyTaskManager {

    private final Deepwither plugin;
    private final DailyTaskDataStore dataStore;
    private final Map<UUID, DailyTaskData> playerTaskData;

    public DailyTaskManager(Deepwither plugin, DailyTaskDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.playerTaskData = new ConcurrentHashMap<>();
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
        if (currentTier == 0) currentTier = 1; // Safezoneや未設定の場合はTier 1とする

        // 2. Configから mob_spawns のリストを取得
        FileConfiguration config = plugin.getConfig();
        List<String> mobList = config.getStringList("mob_spawns." + currentTier + ".regular_mobs");

        String targetMobId;

        if (mobList == null || mobList.isEmpty()) {
            // 設定がない場合はフォールバックとして "bandit"
            targetMobId = "bandit";
            plugin.getLogger().warning("No regular_mobs found for Tier " + currentTier + ". Fallback to bandit.");
        } else {
            // ランダムに選出
            targetMobId = mobList.get(plugin.getRandom().nextInt(mobList.size()));
        }

        // 3. 目標数を設定 (修正箇所)
        // 5 <= X <= 15 の範囲で乱数を生成
        int minCount = 5;
        int maxCount = 15;
        // nextInt(max - min + 1) は 0 から (max - min) までの乱数。
        // ここでは nextInt(15 - 5 + 1) = nextInt(11) -> 0〜10
        // これに minCount (5) を加算し、5〜15の乱数を生成。
        int targetCount = plugin.getRandom().nextInt(maxCount - minCount + 1) + minCount;

        // 4. データにセット
        data.setProgress(traderId, 0, targetCount);
        data.setTargetMob(traderId, targetMobId); // ★ターゲット保存

        dataStore.saveTaskData(data);

        // 表示名を分かりやすくする処理（Config等の表示名マップがあればそれを使うが、ここでは簡易的に）
        String displayName = targetMobId.equals("bandit") ? "バンディット" : targetMobId;

        player.sendMessage("§e[タスク] §a" + traderId + "§fからの新しいタスクを開始しました！");
        player.sendMessage("§7目標: " + displayName + " を §c" + targetCount + "体 §7倒せ！");
    }

    // --- ★追加: WorldGuard連携によるTier取得 ---
    public int getTierFromLocation(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        int maxTier = 0;

        for (ProtectedRegion region : set) {
            String id = region.getId().toLowerCase();

            if (id.contains("safezone")) {
                return 0; // Safezoneなら0を返す
            }

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

    // --- タスク完了 ---
    public void completeTask(Player player, String traderId) {
        DailyTaskData data = getTaskData(player);

        int goldReward = 500 + plugin.getRandom().nextInt(500);
        int creditReward = 100 + plugin.getRandom().nextInt(50);

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
}