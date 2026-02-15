package com.lunar_prototype.deepwither.core;

import com.lunar_prototype.deepwither.*;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.core.playerdata.PlayerData;
import com.lunar_prototype.deepwither.crafting.CraftingData;
import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.profession.PlayerProfessionData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーごとのデータを一括管理するキャッシュコンテナ。
 */
public class PlayerCache {
    private final UUID uuid;
    private final Map<Class<?>, Object> dataMap = new ConcurrentHashMap<>();
    private PlayerData playerData;

    public PlayerCache(UUID uuid) {
        this.uuid = uuid;
        this.playerData = new PlayerData(uuid);
    }

    public PlayerData getData() {
        return playerData;
    }

    public void setData(PlayerData playerData) {
        this.playerData = playerData;
    }

    /**
     * 指定されたクラスのデータを取得します。
     * PlayerData内の該当フィールドを優先し、なければdataMapから取得します。
     */
    public <T> T get(Class<T> clazz) {
        T data = getFromPlayerData(clazz);
        if (data != null) return data;
        
        Object obj = dataMap.get(clazz);
        return obj == null ? null : clazz.cast(obj);
    }

    /**
     * 指定されたクラスのデータをキャッシュにセットします。
     * PlayerDataに該当フィールドがあればそこにセットし、なければdataMapにセットします。
     */
    public <T> void set(Class<T> clazz, T data) {
        if (setToPlayerData(clazz, data)) return;
        dataMap.put(clazz, data);
    }

    /**
     * 指定されたクラスのデータをキャッシュから削除します。
     */
    public void remove(Class<?> clazz) {
        if (removeFromPlayerData(clazz)) return;
        dataMap.remove(clazz);
    }

    @SuppressWarnings("unchecked")
    private <T> T getFromPlayerData(Class<T> clazz) {
        if (clazz == PlayerAttributeData.class) return (T) playerData.getAttributes();
        if (clazz == PlayerLevelData.class) return (T) playerData.getLevel();
        if (clazz == ManaData.class) return (T) playerData.getMana();
        if (clazz == SkillData.class) return (T) playerData.getSkilltree();
        if (clazz == DailyTaskData.class) return (T) playerData.getDailyTasks();
        if (clazz == CraftingData.class) return (T) playerData.getCrafting();
        if (clazz == PlayerProfessionData.class) return (T) playerData.getProfession();
        if (clazz == PlayerQuestData.class) return (T) playerData.getQuests();
        return null;
    }

    private <T> boolean setToPlayerData(Class<T> clazz, T data) {
        if (clazz == PlayerAttributeData.class) { playerData.setAttributes((PlayerAttributeData) data); return true; }
        if (clazz == PlayerLevelData.class) { playerData.setLevel((PlayerLevelData) data); return true; }
        if (clazz == ManaData.class) { playerData.setMana((ManaData) data); return true; }
        if (clazz == SkillData.class) { playerData.setSkilltree((SkillData) data); return true; }
        if (clazz == DailyTaskData.class) { playerData.setDailyTasks((DailyTaskData) data); return true; }
        if (clazz == CraftingData.class) { playerData.setCrafting((CraftingData) data); return true; }
        if (clazz == PlayerProfessionData.class) { playerData.setProfession((PlayerProfessionData) data); return true; }
        if (clazz == PlayerQuestData.class) { playerData.setQuests((PlayerQuestData) data); return true; }
        return false;
    }

    private boolean removeFromPlayerData(Class<?> clazz) {
        if (clazz == PlayerAttributeData.class) { playerData.setAttributes(null); return true; }
        if (clazz == PlayerLevelData.class) { playerData.setLevel(null); return true; }
        if (clazz == ManaData.class) { playerData.setMana(null); return true; }
        if (clazz == SkillData.class) { playerData.setSkilltree(null); return true; }
        if (clazz == DailyTaskData.class) { playerData.setDailyTasks(null); return true; }
        if (clazz == CraftingData.class) { playerData.setCrafting(null); return true; }
        if (clazz == PlayerProfessionData.class) { playerData.setProfession(null); return true; }
        if (clazz == PlayerQuestData.class) { playerData.setQuests(null); return true; }
        return false;
    }

    public UUID getUuid() {
        return uuid;
    }
}
