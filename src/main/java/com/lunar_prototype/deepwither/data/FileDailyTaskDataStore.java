package com.lunar_prototype.deepwither.data;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FileDailyTaskDataStore implements DailyTaskDataStore {

    private final Deepwither plugin;
    private final File dataFolder;

    static {
        ConfigurationSerialization.registerClass(DailyTaskData.class);
    }

    public FileDailyTaskDataStore(Deepwither plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "daily_tasks");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    private File getPlayerFile(UUID playerId) {
        return new File(dataFolder, playerId.toString() + ".yml");
    }

    // --- データロード (非同期: 起動/ログイン時はOK) ---
    @Override
    public CompletableFuture<DailyTaskData> loadTaskData(UUID playerId) {
        CompletableFuture<DailyTaskData> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File playerFile = getPlayerFile(playerId);
            if (!playerFile.exists()) {
                future.complete(null);
                return;
            }
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
                Object serializedData = config.get("data");

                DailyTaskData loadedData = (serializedData instanceof DailyTaskData) ? (DailyTaskData) serializedData : null;
                future.complete(loadedData);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load daily task data for " + playerId + ": " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // ----------------------------------------------------
    // ★修正された データ保存 (saveTaskData) ★
    // ----------------------------------------------------
    @Override
    public void saveTaskData(DailyTaskData data) {

        // onDisable中に呼ばれた場合 (プラグインが無効化されている場合) は、メインスレッドで同期的に実行。
        // それ以外の場合 (ゲームプレイ中のタスク完了など) は非同期で実行。
        if (plugin.isEnabled()) {
            // ★プラグインが有効な場合: 非同期で実行
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                performSave(data);
            });
        } else {
            // ★プラグインが無効な場合 (onDisable中): メインスレッドで同期的に実行
            performSave(data);
        }
    }

    /**
     * 実際のファイル書き込み処理。同期/非同期のどちらからも呼ばれる。
     */
    private void performSave(DailyTaskData data) {
        File playerFile = getPlayerFile(data.getPlayerId());

        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("data", data);

            // ファイルI/O処理
            config.save(playerFile);

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save daily task data for " + data.getPlayerId() + " due to IO error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save daily task data for " + data.getPlayerId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}