package com.lunar_prototype.deepwither.core.playerdata;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.playerdata.IPlayerDataHandler;
import com.lunar_prototype.deepwither.core.CacheManager;
import com.lunar_prototype.deepwither.core.PlayerCache;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * プレイヤーデータの読み込み・保存・移行を一括管理するマネージャー。
 * Observerパターンを用いて、各機能マネージャーから登録されたハンドラーを並列実行します。
 */
@DependsOn({DatabaseManager.class, CacheManager.class})
public class PlayerDataManager implements IManager {

    private final Deepwither plugin;
    private final CacheManager cache;
    private final List<IPlayerDataHandler> handlers = new CopyOnWriteArrayList<>();

    public PlayerDataManager(Deepwither plugin, CacheManager cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    @Override
    public void init() {
    }

    @Override
    public void shutdown() {
    }

    /**
     * データハンドラーを登録します。
     * @param handler ロード/セーブを担当するハンドラー
     */
    public void registerHandler(IPlayerDataHandler handler) {
        handlers.add(handler);
        plugin.getLogger().info("[PlayerData] Registered handler: " + handler.getHandlerName());
    }

    /**
     * 登録された全ハンドラーを並列実行し、プレイヤーデータを読み込みます。
     */
    public CompletableFuture<Void> loadData(UUID uuid) {
        PlayerCache pc = cache.getCache(uuid);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (IPlayerDataHandler handler : handlers) {
            futures.add(handler.loadData(uuid, pc));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * 登録された全ハンドラーを並列実行し、プレイヤーデータを保存します。
     */
    public CompletableFuture<Void> saveData(UUID uuid) {
        PlayerCache pc = cache.getCache(uuid);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (IPlayerDataHandler handler : handlers) {
            futures.add(handler.saveData(uuid, pc));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * プレイヤーデータを保存し、キャッシュを破棄します（同期）。
     */
    public void unloadData(UUID uuid) {
        saveData(uuid).join();
        cache.removeCache(uuid);
    }

    /**
     * プレイヤーデータを非同期で保存し、完了後にキャッシュを破棄します。
     * @param uuid 対象プレイヤーの UUID
     * @return 処理の完了を表す CompletableFuture
     */
    public CompletableFuture<Void> unloadDataAsync(UUID uuid) {
        return saveData(uuid).thenRun(() -> cache.removeCache(uuid));
    }
}
