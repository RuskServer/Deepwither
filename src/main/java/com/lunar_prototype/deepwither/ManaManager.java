package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({})
public class ManaManager implements IManager {

    private final Map<UUID, ManaData> manaMap = new HashMap<>();

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    public ManaData get(UUID uuid) {
        return manaMap.computeIfAbsent(uuid, k -> new ManaData(100.0)); // デフォルト100など
    }

    public void set(UUID uuid, ManaData data) {
        manaMap.put(uuid, data);
    }

    public void remove(UUID uuid) {
        manaMap.remove(uuid);
    }
}

