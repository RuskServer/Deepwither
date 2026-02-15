package com.lunar_prototype.deepwither.core;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーデータキャッシュを一元管理するマネージャー。
 * 各マネージャーが個別に持っていたデータマップを統合し、DBとの中間レイヤーとして機能します。
 */
@DependsOn({})
public class CacheManager implements IManager {

    private final Map<UUID, PlayerCache> cacheMap = new ConcurrentHashMap<>();

    @Override
    public void init() throws Exception {
        // 初期化が必要な場合はここに記述
    }

    @Override
    public void shutdown() {
        cacheMap.clear();
    }

    /**
     * 指定されたプレイヤーのキャッシュを取得します。
     * キャッシュが存在しない場合は新しく作成されます。
     */
    public PlayerCache getCache(UUID uuid) {
        return cacheMap.computeIfAbsent(uuid, PlayerCache::new);
    }

    /**
     * 指定されたプレイヤーのキャッシュを削除します。
     */
    public void removeCache(UUID uuid) {
        cacheMap.remove(uuid);
    }
}
