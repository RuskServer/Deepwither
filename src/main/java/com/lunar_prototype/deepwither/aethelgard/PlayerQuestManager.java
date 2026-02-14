package com.lunar_prototype.deepwither.aethelgard;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.data.FilePlayerQuestDataStore;
import com.lunar_prototype.deepwither.data.PlayerQuestDataStore;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@DependsOn({GuildQuestManager.class, FilePlayerQuestDataStore.class})
public class PlayerQuestManager implements IManager {

    private final JavaPlugin plugin;
    private final GuildQuestManager guildQuestManager;
    private final PlayerQuestDataStore dataStore;

    private static final int MAX_ACTIVE_QUESTS = 1;
    private final Map<UUID, PlayerQuestData> playerQuestCache;

    public PlayerQuestManager(JavaPlugin plugin, GuildQuestManager guildQuestManager, PlayerQuestDataStore dataStore) {
        this.plugin = plugin;
        this.guildQuestManager = guildQuestManager;
        this.dataStore = dataStore;
        this.playerQuestCache = new ConcurrentHashMap<>();
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        for (UUID uuid : new HashSet<>(playerQuestCache.keySet())) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) unloadPlayer(p);
        }
    }

    public void loadPlayer(Player player) {
        dataStore.loadQuestData(player.getUniqueId())
                .thenAccept(data -> {
                    playerQuestCache.put(player.getUniqueId(), data);
                    plugin.getLogger().info("Loaded quest data for " + player.getName());
                })
                .exceptionally(e -> {
                    plugin.getLogger().severe("Failed to load quest data for " + player.getName() + ": " + e.getMessage());
                    playerQuestCache.put(player.getUniqueId(), new PlayerQuestData(player.getUniqueId()));
                    return null;
                });
    }

    public void unloadPlayer(Player player) {
        PlayerQuestData data = playerQuestCache.remove(player.getUniqueId());
        if (data != null) {
            dataStore.saveQuestData(data);
            plugin.getLogger().info("Saved and unloaded quest data for " + player.getName());
        }
    }

    public boolean claimQuest(Player player, String locationId, UUID questId) {
        PlayerQuestData data = playerQuestCache.get(player.getUniqueId());

        if (data == null) {
            player.sendMessage(Component.text("エラー: プレイヤーデータがロードされていません。再ログインしてください。", NamedTextColor.RED));
            return false;
        }

        if (data.getActiveQuests().size() >= MAX_ACTIVE_QUESTS) {
            player.sendMessage(Component.text("あなたは既にクエストを受注しています。現在のクエストを完了してから新しいクエストを受けてください。", NamedTextColor.RED));
            return false;
        }

        GeneratedQuest claimedQuest = guildQuestManager.claimQuest(locationId, questId);

        if (claimedQuest == null) {
            player.sendMessage(Component.text("このクエストは既に他のプレイヤーに受注されたか、存在しません。", NamedTextColor.RED));
            return false;
        }

        data.addQuest(questId, claimedQuest);
        player.sendMessage(Component.text("クエスト『", NamedTextColor.GREEN)
                .append(Component.text(claimedQuest.getTitle(), NamedTextColor.WHITE))
                .append(Component.text("』を受注しました！", NamedTextColor.GREEN)));

        dataStore.saveQuestData(data);
        return true;
    }

    public PlayerQuestData getPlayerData(UUID playerId) {
        return playerQuestCache.get(playerId);
    }

    public boolean updateQuestProgress(Player player, String objectiveType) {
        PlayerQuestData data = playerQuestCache.get(player.getUniqueId());
        if (data == null) return false;

        boolean questCompleted = false;
        for (UUID questId : data.getActiveQuests().keySet()) {
            QuestProgress progress = data.getProgress(questId);
            GeneratedQuest details = progress.getQuestDetails();

            if (details.getTargetMobId().equalsIgnoreCase(objectiveType)) {
                progress.incrementProgress();
                if (progress.isComplete()) {
                    handleQuestCompletion(player, questId, progress);
                    questCompleted = true;
                }
            }
        }

        if (questCompleted) dataStore.saveQuestData(data);
        return questCompleted;
    }

    public void savePlayerQuestData(UUID playerId) {
        PlayerQuestData data = playerQuestCache.get(playerId);
        if (data != null) dataStore.saveQuestData(data);
    }

    private void handleQuestCompletion(Player player, UUID questId, QuestProgress progress) {
        player.sendMessage(Component.text("クエスト『", NamedTextColor.YELLOW)
                .append(Component.text(progress.getQuestDetails().getTitle(), NamedTextColor.WHITE))
                .append(Component.text("』を達成しました！ギルドに戻り報告しましょう。", NamedTextColor.YELLOW)));

        RewardDetails rewardDetails = progress.getQuestDetails().getRewardDetails();

        if (Deepwither.getInstance() != null) {
            Deepwither.getInstance().getLevelManager().addExp(player, rewardDetails.getExperiencePoints());
            Deepwither.getEconomy().depositPlayer(player, rewardDetails.getGuildCoin());
            Deepwither.getInstance().getItemFactory().getCustomItem(player, rewardDetails.getItemRewardId());
        } else {
            plugin.getLogger().warning("Deepwither instance is null. Cannot grant rewards.");
        }

        playerQuestCache.get(player.getUniqueId()).removeQuest(questId);
    }
}
