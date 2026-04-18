package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.playerdata.IPlayerDataHandler;
import com.lunar_prototype.deepwither.core.PlayerCache;
import java.util.concurrent.CompletableFuture;

import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.core.CacheManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

import java.util.UUID;

@DependsOn({CacheManager.class})
public class ManaManager implements IManager, IPlayerDataHandler {

    @Override
    public void init() {
        com.lunar_prototype.deepwither.Deepwither.getInstance().getPlayerDataManager().registerHandler(this);
}

    @Override
    public void shutdown() {}

    public ManaData get(UUID uuid) {
        ManaData data = DW.cache().getCache(uuid).get(ManaData.class);
        if (data == null) {
            data = new ManaData(100.0);
            DW.cache().getCache(uuid).set(ManaData.class, data);
        }
        return data;
    }

    public void set(UUID uuid, ManaData data) {
        DW.cache().getCache(uuid).set(ManaData.class, data);
    }

    public void remove(UUID uuid) {
        DW.cache().getCache(uuid).remove(ManaData.class);
    }

    @Override
    public CompletableFuture<Void> loadData(UUID uuid, PlayerCache cache) {
        return CompletableFuture.runAsync(() -> get(uuid), com.lunar_prototype.deepwither.Deepwither.getInstance().getAsyncExecutor());
    }

    @Override
    public CompletableFuture<Void> saveData(UUID uuid, PlayerCache cache) {
        return CompletableFuture.completedFuture(null);
    }

}