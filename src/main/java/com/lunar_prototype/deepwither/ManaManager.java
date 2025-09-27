package com.lunar_prototype.deepwither;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ManaManager {

    private final Map<UUID, ManaData> manaMap = new HashMap<>();

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

