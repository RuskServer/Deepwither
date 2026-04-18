package com.lunar_prototype.deepwither.profession;

import com.lunar_prototype.deepwither.api.playerdata.IPlayerComponent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProfessionData implements IPlayerComponent {

    private final UUID playerId;
    private final Map<ProfessionType, Long> experienceMap;

    public PlayerProfessionData(UUID playerId) {
        this.playerId = playerId;
        this.experienceMap = new HashMap<>();
    }

    public void addExp(ProfessionType type, long amount) {
        experienceMap.put(type, getExp(type) + amount);
    }

    public long getExp(ProfessionType type) {
        return experienceMap.getOrDefault(type, 0L);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    // DB保存用に全データ取得用メソッドを追加
    public Map<ProfessionType, Long> getAllExperience() {
        return Collections.unmodifiableMap(experienceMap);
    }
}