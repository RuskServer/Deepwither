package com.lunar_prototype.deepwither.quest;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemNameResolver;
import com.lunar_prototype.deepwither.data.QuestDataStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * ギルドクエストの中央管理クラス。
 * クエストの永続化、生成、周期的な更新を処理します。
 */
public class GuildQuestManager implements Runnable {

    private final JavaPlugin plugin;
    private final QuestGenerator questGenerator;
    private final QuestDataStore dataStore;

    // 各都市ギルドのクエスト情報を保持するリスト
    private List<QuestLocation> guildLocations = new ArrayList<>();

    // 周期的な更新のためのタスクID
    private Integer schedulerTaskId = null;
    private static final long UPDATE_PERIOD_TICKS = 20L * 60 * 60; // 1時間 = 20 * 60秒 * 60分

    public GuildQuestManager(JavaPlugin plugin, QuestDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.questGenerator = new QuestGenerator();

        // 初期ギルドロケーションの設定 (ハードコードまたはConfigからロード)
        // ここで設定されるのは受付NPCの場所ではなく、クエストリストのIDと名前
        initializeGuildLocations();
    }

    /**
     * 初期ギルドロケーション（クエストリストのコンテナ）を設定します。
     * 実際のサーバーでは、Configから読み込むべきです。
     */
    private void initializeGuildLocations() {
        // ダミーのロケーションデータ（QuestLocationのコンストラクタからLocationとradiusが削除されました）
        guildLocations.add(new QuestLocation("t3_city", "第三層都市", new ArrayList<>()));
    }

    /**
     * プラグイン起動時に永続化されたクエストデータをロードし、周期タスクを開始します。
     */
    public void startup() {
        plugin.getLogger().info("Loading quests...");

        // 永続化されたクエストデータをロード (非同期)
        dataStore.loadAllQuests(this.guildLocations)
                .thenAccept(loadedLocations -> {
                    this.guildLocations = loadedLocations;
                    plugin.getLogger().info(loadedLocations.size() + " guild locations loaded.");

                    // 初回起動時のクエストチェックと補充処理を非同期で開始
                    List<CompletableFuture<Void>> generationFutures = this.checkAndRefillQuests(true);

                    // 全ての初回生成が完了した後、周期タスクを開始する
                    CompletableFuture.allOf(generationFutures.toArray(new CompletableFuture[0]))
                            .whenCompleteAsync((v, ex) -> {
                                if (ex != null) {
                                    plugin.getLogger().severe("Initial quest generation failed: " + ex.getMessage());
                                }

                                // 周期タスクの開始 (非同期)
                                this.schedulerTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                                        plugin,
                                        this,
                                        UPDATE_PERIOD_TICKS,
                                        UPDATE_PERIOD_TICKS
                                ).getTaskId();
                                plugin.getLogger().info("Guild Quest Manager started periodic update task.");
                            }, Bukkit.getScheduler().getMainThreadExecutor(Deepwither.getInstance())); // Bukkit APIへのアクセスは不要なので、thenRunAsyncでも可
                })
                .exceptionally(e -> {
                    plugin.getLogger().severe("Failed to load quests: " + e.getMessage());
                    // ロード失敗時も、初回生成と周期タスクを起動
                    this.checkAndRefillQuests(true);
                    this.schedulerTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                            plugin,
                            this,
                            UPDATE_PERIOD_TICKS,
                            UPDATE_PERIOD_TICKS
                    ).getTaskId();
                    return null;
                });
    }

    /**
     * 周期的な更新処理（1時間周期で実行）。
     * run()は非同期で実行されるため、重い処理をメインスレッドに戻さないように変更します。
     */
    @Override
    public void run() {
        plugin.getLogger().info("Quest periodic check and refill starting...");

        // 1. クエストの補充を非同期で実行し、全ての生成タスクの完了を待つ
        List<CompletableFuture<Void>> generationTasks = this.checkAndRefillQuests(false);

        CompletableFuture.allOf(generationTasks.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    // 2. 全ての生成が完了した後、データ永続化を実行（データストアが非同期であることを前提）
                    dataStore.saveAllQuests(this.guildLocations)
                            .thenRun(() -> {
                                plugin.getLogger().info("Quest periodic update finished and data saved.");
                            })
                            .exceptionally(e -> {
                                plugin.getLogger().severe("Failed to save quests after periodic update: " + e.getMessage());
                                return null;
                            });
                })
                .exceptionally(e -> {
                    plugin.getLogger().severe("One or more quest generation tasks failed during run: " + e.getMessage());
                    // 失敗しても、次の run() は実行される
                    return null;
                });
        // run() メソッドはこれ以上同期タスクをスケジュールしません
    }

    /**
     * サーバー停止時に実行される処理。周期タスクを停止し、データを最終保存します。
     */
    public void shutdown() {
        // 周期タスクを停止
        if (this.schedulerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(this.schedulerTaskId);
            this.schedulerTaskId = -1;
        }

        // ★ 最終保存処理：同期的に実行されるメソッドを呼び出すように変更
        // (データストアに 'saveAllQuestsSynchronous' のようなメソッドを追加する必要があります)

        // 仮に dataStore の CompletableFuture を返す saveAllQuests() をブロックして待つ場合:
        try {
            // 非同期保存メソッドを呼び出し、完了するまでブロック (join() or get())
            dataStore.saveAllQuests(this.guildLocations).join();
            plugin.getLogger().info("Synchronous final save of guild quests completed.");
        } catch (Exception e) {
            // join() は実行時例外をラップするため、キャッチ
            plugin.getLogger().log(Level.SEVERE, "Failed to complete final synchronous quest save.", e);
        }
    }

    /**
     * 各ギルドロケーションのクエストをチェックし、不足していれば補充します。
     * クエスト生成処理を非同期で実行するよう改修します。
     * @param isStartup 初回起動時のチェックであるか
     */
    private List<CompletableFuture<Void>> checkAndRefillQuests(boolean isStartup) {
        List<CompletableFuture<Void>> refillFutures = new ArrayList<>();

        for (QuestLocation location : guildLocations) {
            int currentCount = location.getQuestCount();
            int requiredRefill = QuestLocation.MAX_QUESTS - currentCount;

            if (requiredRefill > 0) {
                plugin.getLogger().info(String.format("[%s] Quests missing: %d. Starting generation...", location.getLocationName(), requiredRefill));

                // ★ クエスト生成処理を非同期で実行
                CompletableFuture<Void> refillFuture = CompletableFuture.runAsync(() -> {
                            // 非同期スレッドで必要な数だけクエストを生成
                            for (int i = 0; i < requiredRefill; i++) {
                                int difficulty = 1 + (int)(Math.random() * 3);

                                // 時間のかかる処理: クエスト生成
                                GeneratedQuest newQuest = questGenerator.generateQuest(difficulty);

                                // クエストリストへの追加は、非同期タスク内で行う (Listはスレッドセーフではないが、
                                // このループ内でしかアクセスしないため、問題は少ない。必要に応じてロックを検討)
                                location.addQuest(newQuest);
                            }
                        }, Deepwither.getInstance().getAsyncExecutor()) // ★ プラグインの非同期Executorを利用
                        .thenRun(() -> {
                            // 非同期処理完了後、Bukkit APIは使用せずログのみ出力
                            plugin.getLogger().info(String.format("[%s] Refilled to %d quests (Async generation finished).", location.getLocationName(), location.getQuestCount()));
                        });

                refillFutures.add(refillFuture);

            } else if (isStartup && currentCount > 0) {
                plugin.getLogger().info(String.format("[%s] Loaded %d existing quests.", location.getLocationName(), currentCount));
            }
        }

        // 全ての非同期生成が完了した後、最終処理を行うための処理をここに記述できます
        // (現在は run() メソッドが後のデータ保存処理を担っているため、ここでは特に必須ではない)
        CompletableFuture.allOf(refillFutures.toArray(new CompletableFuture[0]))
                .exceptionally(e -> {
                    plugin.getLogger().severe("One or more quest generation tasks failed: " + e.getMessage());
                    return null;
                });
        return refillFutures;
    }

    /**
     * 特定のギルドロケーションからクエストを取得します。
     * @param locationId ギルドのID
     * @return QuestLocation、見つからない場合はnull
     */
    public QuestLocation getQuestLocation(String locationId) {
        return guildLocations.stream()
                .filter(loc -> loc.getLocationId().equalsIgnoreCase(locationId))
                .findFirst()
                .orElse(null);
    }

    /**
     * プレイヤーがクエストを受け取る（予約する）処理。
     * クエストがリストから削除されるため、他のプレイヤーは受け取れなくなります。
     * @param locationId ギルドID
     * @param questId 受け取るクエストのUUID
     * @return 受け取ったクエスト、またはnull（既に受け取られていた場合など）
     */
    public GeneratedQuest claimQuest(String locationId, UUID questId) {
        QuestLocation location = getQuestLocation(locationId);
        if (location != null) {
            GeneratedQuest quest = location.removeQuest(questId);
            if (quest != null) {
                // クエストが削除されたことを永続化
                dataStore.saveAllQuests(this.guildLocations);
                return quest;
            }
        }
        return null;
    }
}