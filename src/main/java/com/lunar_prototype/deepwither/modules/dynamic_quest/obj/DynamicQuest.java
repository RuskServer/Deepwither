package com.lunar_prototype.deepwither.modules.dynamic_quest.obj;

import com.lunar_prototype.deepwither.modules.dynamic_quest.enums.QuestDifficulty;
import com.lunar_prototype.deepwither.modules.dynamic_quest.enums.QuestPersona;
import com.lunar_prototype.deepwither.modules.dynamic_quest.enums.QuestType;
import com.lunar_prototype.deepwither.modules.dynamic_quest.objective.IQuestObjective;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class DynamicQuest {
    private final UUID questId;
    private final QuestType type;
    private final QuestDifficulty difficulty;
    private final QuestPersona persona;
    private final String generatedDialogue;
    private final Location targetLocation;
    private final double rewardAmount;
    private final long expireTime;
    
    private IQuestObjective objective;
    private Location startLocation;
    private UUID assignee;
    private QuestStatus status;
    private boolean objectiveMet = false;
    private int progressCount = 0;
    
    // For FETCH/DELIVERY items (still needed for QuestService to handle item giving/taking)
    private ItemStack targetItem;
    private int targetAmount;

    public enum QuestStatus {
        CREATED, ACTIVE, COMPLETED
    }

    public DynamicQuest(QuestType type, QuestDifficulty difficulty, QuestPersona persona, String generatedDialogue, Location targetLocation, double rewardAmount) {
        this.questId = UUID.randomUUID();
        this.type = type;
        this.difficulty = difficulty;
        this.persona = persona;
        this.generatedDialogue = generatedDialogue;
        this.targetLocation = targetLocation;
        this.rewardAmount = rewardAmount;
        this.expireTime = System.currentTimeMillis() + (1000 * 60 * 30);
        this.status = QuestStatus.CREATED;
    }

    public void setObjective(IQuestObjective objective) {
        this.objective = objective;
    }

    public IQuestObjective getObjective() {
        return objective;
    }

    public String getObjectiveDescription() {
        return objective != null ? objective.getDescription() : "Unknown Objective";
    }

    public UUID getQuestId() { return questId; }
    public QuestType getType() { return type; }
    public QuestDifficulty getDifficulty() { return difficulty; }
    public QuestPersona getPersona() { return persona; }
    public String getGeneratedDialogue() { return generatedDialogue; }
    public Location getTargetLocation() { return targetLocation; }
    public Location getStartLocation() { return startLocation; }
    public void setStartLocation(Location startLocation) { this.startLocation = startLocation; }
    public UUID getAssignee() { return assignee; }
    public void setAssignee(UUID assignee) { this.assignee = assignee; }
    public QuestStatus getStatus() { return status; }
    public void setStatus(QuestStatus status) { this.status = status; }
    public boolean isObjectiveMet() { return objectiveMet; }
    public void setObjectiveMet(boolean objectiveMet) { this.objectiveMet = objectiveMet; }
    public int getProgressCount() { return progressCount; }
    public void setProgressCount(int progressCount) { this.progressCount = progressCount; }
    public double getRewardAmount() { return rewardAmount; }
    public boolean isExpired() { return System.currentTimeMillis() > expireTime; }

    public ItemStack getTargetItem() { return targetItem; }
    public void setTargetItem(ItemStack targetItem) { this.targetItem = targetItem; }
    public int getTargetAmount() { return targetAmount; }
    public void setTargetAmount(int targetAmount) { this.targetAmount = targetAmount; }
}
