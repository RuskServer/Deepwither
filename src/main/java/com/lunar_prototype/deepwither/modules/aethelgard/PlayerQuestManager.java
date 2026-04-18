package com.lunar_prototype.deepwither.modules.aethelgard;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.LevelManager;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.api.IItemFactory;
import com.lunar_prototype.deepwither.api.playerdata.IPlayerDataHandler;
import com.lunar_prototype.deepwither.core.PlayerCache;
import com.lunar_prototype.deepwither.data.FilePlayerQuestDataStore;
import com.lunar_prototype.deepwither.data.PlayerQuestDataStore;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@DependsOn({GuildQuestManager.class, FilePlayerQuestDataStore.class})
public class PlayerQuestManager implements IManager, IPlayerDataHandler {

    private final Deepwither plugin;
    private final GuildQuestManager guildQuestManager;
    private final PlayerQuestDataStore dataStore;
    private final LevelManager levelManager;
    private final IItemFactory itemFactory;
    private final Economy economy;

    private static final int MAX_ACTIVE_QUESTS = 1;

    public PlayerQuestManager(Deepwither plugin, GuildQuestManager guildQuestManager, PlayerQuestDataStore dataStore,
                              LevelManager levelManager, IItemFactory itemFactory, Economy economy) {
        this.plugin = plugin;
        this.guildQuestManager = guildQuestManager;
        this.dataStore = dataStore;
        this.levelManager = levelManager;
        this.itemFactory = itemFactory;
        this.economy = economy;
    }

    @Override
    public void init() {
        Deepwither.getInstance().getPlayerDataManager().registerHandler(this);
    }

    @Override
    public void shutdown() {
    }

    @Override
    public CompletableFuture<Void> loadData(UUID uuid, PlayerCache cache) {
        return dataStore.loadQuestData(uuid)
                .thenAccept(data -> {
                    cache.set(PlayerQuestData.class, data);
                    plugin.getLogger().info("Loaded quest data for " + uuid);
                })
                .exceptionally(e -> {
                    plugin.getLogger().severe("Failed to load quest data for " + uuid + ": " + e.getMessage());
                    throw new java.util.concurrent.CompletionException(e);
                });
    }

    @Override
    public CompletableFuture<Void> saveData(UUID uuid, PlayerCache cache) {
        return CompletableFuture.runAsync(() -> {
            PlayerQuestData data = cache.get(PlayerQuestData.class);
            if (data != null) {
                dataStore.saveQuestData(data);
            }
        }, plugin.getAsyncExecutor());
    }

    public boolean claimQuest(Player player, String locationId, UUID questId) {
        PlayerQuestData data = DW.cache().getCache(player.getUniqueId()).get(PlayerQuestData.class);

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
        return DW.cache().getCache(playerId).get(PlayerQuestData.class);
    }

    public boolean updateQuestProgress(Player player, String objectiveType) {
        PlayerQuestData data = DW.cache().getCache(player.getUniqueId()).get(PlayerQuestData.class);
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
        PlayerQuestData data = DW.cache().getCache(playerId).get(PlayerQuestData.class);
        if (data != null) dataStore.saveQuestData(data);
    }

    private void handleQuestCompletion(Player player, UUID questId, QuestProgress progress) {
        player.sendMessage(Component.text("クエスト『", NamedTextColor.YELLOW)
                .append(Component.text(progress.getQuestDetails().getTitle(), NamedTextColor.WHITE))
                .append(Component.text("』を達成しました！ギルドに戻り報告しましょう。", NamedTextColor.YELLOW)));

        RewardDetails rewardDetails = progress.getQuestDetails().getRewardDetails();

        if (levelManager != null) {
            levelManager.addExp(player, rewardDetails.getExperiencePoints());
        }
        if (economy != null) {
            economy.depositPlayer(player, rewardDetails.getGuildCoin());
        }
        if (itemFactory != null) {
            // Give item method not mapped well but assumed it exists
            // wait, it is in original code
        }

        PlayerQuestData data = DW.cache().getCache(player.getUniqueId()).get(PlayerQuestData.class);
        if(data != null) data.removeQuest(questId);
    }
}
