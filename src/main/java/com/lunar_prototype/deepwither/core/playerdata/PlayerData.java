package com.lunar_prototype.deepwither.core.playerdata;

import com.lunar_prototype.deepwither.*;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.crafting.CraftingData;
import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.profession.PlayerProfessionData;

import java.util.UUID;

/**
 * プレイヤーの全データを統合するクラス。
 */
public class PlayerData {
    private final UUID uuid;
    
    private PlayerAttributeData attributes;
    private PlayerLevelData level;
    private ManaData mana;
    private SkillData skilltree;
    private DailyTaskData dailyTasks;
    private CraftingData crafting;
    private PlayerProfessionData profession;
    private PlayerQuestData quests;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() { return uuid; }

    public PlayerAttributeData getAttributes() { return attributes; }
    public void setAttributes(PlayerAttributeData attributes) { this.attributes = attributes; }

    public PlayerLevelData getLevel() { return level; }
    public void setLevel(PlayerLevelData level) { this.level = level; }

    public ManaData getMana() { return mana; }
    public void setMana(ManaData mana) { this.mana = mana; }

    public SkillData getSkilltree() { return skilltree; }
    public void setSkilltree(SkillData skilltree) { this.skilltree = skilltree; }

    public DailyTaskData getDailyTasks() { return dailyTasks; }
    public void setDailyTasks(DailyTaskData dailyTasks) { this.dailyTasks = dailyTasks; }

    public CraftingData getCrafting() { return crafting; }
    public void setCrafting(CraftingData crafting) { this.crafting = crafting; }

    public PlayerProfessionData getProfession() { return profession; }
    public void setProfession(PlayerProfessionData profession) { this.profession = profession; }

    public PlayerQuestData getQuests() { return quests; }
    public void setQuests(PlayerQuestData quests) { this.quests = quests; }
}
