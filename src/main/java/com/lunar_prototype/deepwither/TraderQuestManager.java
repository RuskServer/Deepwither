package com.lunar_prototype.deepwither;

import com.google.gson.Gson;
import com.lunar_prototype.deepwither.data.PlayerQuestData;
import com.lunar_prototype.deepwither.TraderManager.QuestData;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@DependsOn({DatabaseManager.class})
public class TraderQuestManager implements IManager, Listener {

    private final Deepwither plugin;
    private final DatabaseManager db;
    private final Map<UUID, PlayerQuestData> playerDataMap = new HashMap<>();
    private final Gson gson;

    public TraderQuestManager(Deepwither plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        this.gson = db.getGson();
    }

    @Override
    public void init() throws Exception {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerData(player.getUniqueId());
        }
    }

    @Override
    public void shutdown() {
        for (UUID uuid : playerDataMap.keySet()) {
            savePlayerData(uuid);
        }
        playerDataMap.clear();
    }

    // --- クエスト進行ロジック ---

    /**
     * プレイヤーがそのクエストを受領可能か判定する（前提クエストのチェック）
     */
    public boolean canAcceptQuest(Player player, String traderId, TraderManager.QuestData quest) {
        // 既に完了している場合は受領不可
        if (isQuestCompleted(player, traderId, quest.getId())) return false;

        // 前提クエストがある場合
        if (quest.getRequiredQuestId() != null && !quest.getRequiredQuestId().isEmpty()) {
            return isQuestCompleted(player, traderId, quest.getRequiredQuestId());
        }
        return true;
    }

    /**
     * プレイヤーのクエスト進行データを取得します。
     * ログイン時に必ずデータが生成されるため、通常は null にはなりませんが、
     * 安全のために computeIfAbsent 等でハンドリングします。
     */
    public PlayerQuestData getPlayerData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(), k -> new PlayerQuestData());
    }

    /**
     * クエストを受領状態（進行中）にする
     */
    public void acceptQuest(Player player, String traderId, String questId) {
        PlayerQuestData data = playerDataMap.get(player.getUniqueId());
        if (data == null) return;

        String progressKey = traderId + ":" + questId;
        if (!data.getCurrentProgress().containsKey(progressKey)) {
            data.getCurrentProgress().put(progressKey, 0);
            player.sendMessage("§e[Quest] §fクエスト 「" + questId + "」 を受領しました。");
            // 非同期保存
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> savePlayerData(player.getUniqueId()));
        }
    }

    /**
     * MythicMobsの死亡イベントを監視し、進行中のキルタスクを処理する
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicMobKill(MythicMobDeathEvent e) {
        // プレイヤーによる殺害かチェック
        if (!(e.getKiller() instanceof Player killer)) return;

        // MythicMobの内部名（YAMLのtargetと比較するID）を取得
        String mobId = e.getMobType().getInternalName();
        UUID uuid = killer.getUniqueId();
        PlayerQuestData data = playerDataMap.get(uuid);

        if (data == null || data.getCurrentProgress().isEmpty()) return;

        TraderManager tm = plugin.getTraderManager();

        // 進行中のクエストのみをチェック（負荷対策）
        for (String progressKey : new HashSet<>(data.getCurrentProgress().keySet())) {
            String[] split = progressKey.split(":");
            if (split.length < 2) continue;

            String traderId = split[0];
            String questId = split[1];

            TraderManager.QuestData quest = tm.getQuestsForTrader(traderId).get(questId);
            if (quest == null || !"KILL".equalsIgnoreCase(quest.getType())) continue;

            // --- 1. MythicMob IDが一致するか ---
            if (!mobId.equalsIgnoreCase(quest.getTarget())) continue;

            // --- 2. 拡張条件: 距離チェック ---
            if (quest.getMinDistance() > 0 || quest.getMaxDistance() > 0) {
                // e.getEntity() は死んだMobのエンティティ
                double dist = killer.getLocation().distance(e.getEntity().getLocation());
                if (quest.getMinDistance() > 0 && dist < quest.getMinDistance()) continue;
                if (quest.getMaxDistance() > 0 && dist > quest.getMaxDistance()) continue;
            }

            // --- 3. 拡張条件: 装備チェック ---
            if (quest.getRequiredWeapon() != null) {
                ItemStack hand = killer.getInventory().getItemInMainHand();
                if (!isMatchingItem(hand, quest.getRequiredWeapon())) continue;
            }

            if (quest.getRequiredArmor() != null) {
                // 必要に応じてチェストプレートなども判定可能ですが、一旦装備指定全般をチェック
                if (!hasRequiredArmor(killer, quest.getRequiredArmor())) continue;
            }

            // 全条件クリア -> 進捗加算
            incrementProgress(killer, traderId, quest);
        }
    }

    /**
     * プレイヤーがいずれかの部位に指定されたIDの防具を装備しているか判定
     */
    private boolean hasRequiredArmor(Player player, String armorId) {
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (isMatchingItem(armor, armorId)) return true;
        }
        return false;
    }

    /**
     * 納品タスクの処理 (GUIなどから呼び出す想定)
     */
    public void handleDelivery(Player player, String traderId, String questId) {
        PlayerQuestData data = playerDataMap.get(player.getUniqueId());
        QuestData quest = plugin.getTraderManager().getQuestsForTrader(traderId).get(questId);

        if (data == null || quest == null || !"FETCH".equalsIgnoreCase(quest.getType())) return;
        if (data.isCompleted(traderId, questId)) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isMatchingItem(hand, quest.getTarget())) {
            int current = data.getCurrentProgress().getOrDefault(traderId + ":" + questId, 0);
            int need = quest.getAmount() - current;
            int has = hand.getAmount();

            int take = Math.min(need, has);
            hand.setAmount(has - take);

            for (int i = 0; i < take; i++) {
                incrementProgress(player, traderId, quest);
            }
        }
    }

    private void incrementProgress(Player player, String traderId, QuestData quest) {
        PlayerQuestData data = playerDataMap.get(player.getUniqueId());
        String progressKey = traderId + ":" + quest.getId();
        int current = data.getCurrentProgress().getOrDefault(progressKey, 0) + 1;

        if (current >= quest.getAmount()) {
            completeQuest(player, traderId, quest.getId());
            player.sendMessage("§a[Quest] §f" + quest.getDisplayName() + " を完了しました！");
            data.getCurrentProgress().remove(progressKey);
        } else {
            data.getCurrentProgress().put(progressKey, current);
            // 進行状況の通知（必要に応じて）
        }
    }

    /**
     * アイテムが指定されたターゲット（Material名 or CustomID）と一致するか判定
     */
    private boolean isMatchingItem(ItemStack item, String target) {
        if (item == null || item.getType() == Material.AIR) return false;

        // 1. バニラ Material 判定
        if (item.getType().name().equalsIgnoreCase(target)) return true;

        // 2. CustomID 判定 (PersistentDataContainer を使用)
        // ※TraderGUI で使っている CUSTOM_ID_KEY と合わせる
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "custom_id");
        if (item.hasItemMeta()) {
            String customId = item.getItemMeta().getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
            return target.equalsIgnoreCase(customId);
        }
        return false;
    }

    // --- データ管理・保存 ---

    public boolean isQuestCompleted(Player player, String traderId, String questId) {
        PlayerQuestData data = playerDataMap.get(player.getUniqueId());
        return data != null && data.isCompleted(traderId, questId);
    }

    public void completeQuest(Player player, String traderId, String questId) {
        PlayerQuestData data = playerDataMap.get(player.getUniqueId());
        if (data != null) {
            data.completeQuest(traderId, questId);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> savePlayerData(player.getUniqueId()));
        }
    }

    private void loadPlayerData(UUID uuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT data_json FROM player_quests WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("data_json");
                    playerDataMap.put(uuid, gson.fromJson(json, PlayerQuestData.class));
                } else {
                    playerDataMap.put(uuid, new PlayerQuestData());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("PlayerQuestDataのロードに失敗: " + uuid);
        }
    }

    public void savePlayerData(UUID uuid) {
        PlayerQuestData data = playerDataMap.get(uuid);
        if (data == null) return;

        String json = gson.toJson(data);
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO player_quests (uuid, data_json) VALUES (?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET data_json = excluded.data_json")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, json);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("PlayerQuestDataの保存に失敗: " + uuid);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        loadPlayerData(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        savePlayerData(uuid);
        playerDataMap.remove(uuid);
    }
}