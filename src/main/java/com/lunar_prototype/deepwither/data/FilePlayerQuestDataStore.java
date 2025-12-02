package com.lunar_prototype.deepwither.data;

import com.lunar_prototype.deepwither.quest.PlayerQuestData;
import com.lunar_prototype.deepwither.util.QuestDataSerializer; // 新たに定義するシリアライザー
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * YAMLファイルを使用してPlayerQuestDataを永続化する実装。
 */
public class FilePlayerQuestDataStore implements PlayerQuestDataStore {

    private final JavaPlugin plugin;
    private final File dataFolder;

    public FilePlayerQuestDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!this.dataFolder.exists()) {
            this.dataFolder.mkdirs();
        }
    }

    private File getPlayerFile(UUID playerId) {
        return new File(dataFolder, playerId.toString() + ".yml");
    }

    /**
     * プレイヤーのクエストデータを非同期でロードします。
     */
    @Override
    public CompletableFuture<PlayerQuestData> loadQuestData(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            File playerFile = getPlayerFile(playerId);

            if (!playerFile.exists()) {
                // ファイルが存在しない場合は新規データを作成
                return new PlayerQuestData(playerId);
            }

            try {
                // YamlConfigurationをロード
                YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

                // シリアライザーを使ってYAMLの内容からPlayerQuestDataオブジェクトに変換
                return QuestDataSerializer.deserializePlayerQuestData(playerId, config);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load quest data for " + playerId, e);
                // ロード失敗時も、エラーをログに出し、新しいデータを作成して返す
                return new PlayerQuestData(playerId);
            }
        });
    }

    /**
     * プレイヤーのクエストデータを非同期で保存します。
     */
    @Override
    public CompletableFuture<Void> saveQuestData(PlayerQuestData data) {
        return CompletableFuture.runAsync(() -> {
            File playerFile = getPlayerFile(data.getPlayerId());
            YamlConfiguration config = new YamlConfiguration();

            try {
                // シリアライザーを使ってPlayerQuestDataオブジェクトをYAMLの形式に変換
                QuestDataSerializer.serializePlayerQuestData(data, config);

                // ファイルに保存
                config.save(playerFile);

            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save quest data for " + data.getPlayerId(), e);
                // 保存失敗時は例外をCompletableFutureにセットして通知
                throw new RuntimeException("Save failed", e);
            }
        });
    }
}