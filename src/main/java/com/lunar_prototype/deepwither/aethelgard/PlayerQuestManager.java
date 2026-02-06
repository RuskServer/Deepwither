package com.lunar_prototype.deepwither.aethelgard;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.data.FilePlayerQuestDataStore;
import com.lunar_prototype.deepwither.data.PlayerQuestDataStore;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤー個人の進行中のクエストを一元管理するクラス。
 * オンラインプレイヤーのデータをメモリにキャッシュします。
 */
@DependsOn({GuildQuestManager.class, FilePlayerQuestDataStore.class})
public class PlayerQuestManager implements IManager {

    private final JavaPlugin plugin;
    private final GuildQuestManager guildQuestManager;
    private final PlayerQuestDataStore dataStore; // プレイヤーごとのデータ永続化クラス（別途実装が必要）

    private static final int MAX_ACTIVE_QUESTS = 1;

    // オンラインプレイヤーのUUIDとクエストデータ
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
            if (p != null) {
                unloadPlayer(p);
            }
        }
    }

    /**
     * プレイヤーがログインした際にデータをロードします。
     * @param player ログインしたプレイヤー
     */
    public void loadPlayer(Player player) {
        dataStore.loadQuestData(player.getUniqueId())
                .thenAccept(data -> {
                    playerQuestCache.put(player.getUniqueId(), data);
                    plugin.getLogger().info("Loaded quest data for " + player.getName());
                })
                .exceptionally(e -> {
                    plugin.getLogger().severe("Failed to load quest data for " + player.getName() + ": " + e.getMessage());
                    // ロード失敗時は新規データを作成
                    playerQuestCache.put(player.getUniqueId(), new PlayerQuestData(player.getUniqueId()));
                    return null;
                });
    }

    /**
     * プレイヤーがログアウトした際にデータを保存し、キャッシュから削除します。
     * @param player ログアウトしたプレイヤー
     */
    public void unloadPlayer(Player player) {
        PlayerQuestData data = playerQuestCache.remove(player.getUniqueId());
        if (data != null) {
            dataStore.saveQuestData(data);
            plugin.getLogger().info("Saved and unloaded quest data for " + player.getName());
        }
    }

    /**
     * プレイヤーがギルドからクエストを受け取る（Claimする）処理。
     * @param player クエストを受け取るプレイヤー
     * @param locationId ギルドのID
     * @param questId 受け取るクエストのUUID
     * @return 成功した場合はtrue
     */
    /**
     * プレイヤーがギルドからクエストを受け取る（Claimする）処理。
     * ★ 同時に受けられるクエストを1個に制限します。
     * @param player クエストを受け取るプレイヤー
     * @param locationId ギルドのID
     * @param questId 受け取るクエストのUUID
     * @return 成功した場合はtrue
     */
    public boolean claimQuest(Player player, String locationId, UUID questId) {

        // 1. プレイヤーの個人データを取得
        PlayerQuestData data = playerQuestCache.get(player.getUniqueId());

        if (data == null) {
            player.sendMessage("§cエラー: プレイヤーデータがロードされていません。再ログインしてください。");
            return false;
        }

        // ★ 2. 同時受注制限のチェック
        if (data.getActiveQuests().size() >= MAX_ACTIVE_QUESTS) {
            // クエスト数が制限を超えている場合、受注を拒否する
            player.sendMessage("§cあなたは既にクエストを受注しています。現在のクエストを完了してから新しいクエストを受けてください。");
            return false;
        }

        // 3. GuildQuestManagerからクエストを削除し、取得する
        GeneratedQuest claimedQuest = guildQuestManager.claimQuest(locationId, questId);

        if (claimedQuest == null) {
            player.sendMessage("§cこのクエストは既に他のプレイヤーに受注されたか、存在しません。");
            return false;
        }

        // 4. プレイヤーの個人データにクエストを追加
        data.addQuest(questId, claimedQuest);
        player.sendMessage(String.format("§aクエスト『%s』を受注しました！", claimedQuest.getTitle()));

        // プレイヤーデータを保存（即座に永続化）
        dataStore.saveQuestData(data);
        return true;
    }

    /**
     * プレイヤーの現在のクエストデータにアクセスします。
     */
    public PlayerQuestData getPlayerData(UUID playerId) {
        return playerQuestCache.get(playerId);
    }

    // --- 進捗更新処理のメソッド (後続のイベントリスナーから呼ばれる) ---

    /**
     * プレイヤーのクエスト進捗を更新します。（例：Mob討伐時）
     * @param player 進捗を更新するプレイヤー
     * @param objectiveType 達成目標のタイプ（例: "KILL_ZOMBIE"）
     * @return 完了したクエストがあればtrue
     */
    public boolean updateQuestProgress(Player player, String objectiveType) {
        PlayerQuestData data = playerQuestCache.get(player.getUniqueId());
        if (data == null) return false;

        boolean questCompleted = false;

        // ConcurrentModificationExceptionを防ぐため、コピーしたキーリストを反復
        for (UUID questId : data.getActiveQuests().keySet()) {
            QuestProgress progress = data.getProgress(questId);
            GeneratedQuest details = progress.getQuestDetails();

            // 目標タイプが一致するかチェック (GeneratedQuestにgetObjectiveType()がある前提)
            if (details.getTargetMobId().equalsIgnoreCase(objectiveType)) {
                progress.incrementProgress();

                // 完了チェック
                if (progress.isComplete()) {
                    // クエスト完了時の処理（報酬付与など）を別途実行
                    handleQuestCompletion(player, questId, progress);
                    questCompleted = true;
                }
            }
        }

        if (questCompleted) {
            dataStore.saveQuestData(data); // 完了時はデータを保存
        }
        return questCompleted;
    }

    // データの保存を外部から呼び出すためのメソッド
    public void savePlayerQuestData(UUID playerId) {
        PlayerQuestData data = playerQuestCache.get(playerId);
        if (data != null) {
            dataStore.saveQuestData(data); // FilePlayerQuestDataStoreの非同期保存を呼び出し
        }
    }
    /**
     * クエスト完了時の報酬付与などの処理（ここをさらに実装する必要があります）。
     */
    private void handleQuestCompletion(Player player, UUID questId, QuestProgress progress) {
        player.sendMessage(String.format("§eクエスト『%s』を達成しました！ギルドに戻り報告しましょう。", progress.getQuestDetails().getTitle()));

        // 報酬付与
        RewardDetails rewardDetails = progress.getQuestDetails().getRewardDetails();

        // Deepwitherクラスが存在する前提で実装
        if (Deepwither.getInstance() != null) {
            Deepwither.getInstance().getLevelManager().addExp(player, rewardDetails.getExperiencePoints());
            Deepwither.getEconomy().depositPlayer(player, rewardDetails.getGuildCoin());
            Deepwither.getInstance().getItemFactory().getCustomItem(player, rewardDetails.getItemRewardId());
        } else {
            plugin.getLogger().warning("Deepwither instance is null. Cannot grant rewards.");
        }

        // プレイヤーデータから完了したクエストを削除
        playerQuestCache.get(player.getUniqueId()).removeQuest(questId);
    }
}