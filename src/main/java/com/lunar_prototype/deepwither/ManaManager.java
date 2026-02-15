package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.core.CacheManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

import java.util.UUID;

@DependsOn({CacheManager.class})
public class ManaManager implements IManager {

    @Override
    public void init() {}

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
}

