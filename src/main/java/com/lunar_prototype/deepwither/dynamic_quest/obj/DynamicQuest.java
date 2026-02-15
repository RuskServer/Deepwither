package com.lunar_prototype.deepwither.dynamic_quest.obj;

import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestDifficulty;
import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestPersona;
import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestType;
import org.bukkit.Location;

import java.util.UUID;

public class DynamicQuest {
    private final UUID questId;
    private final QuestType type;
    private final QuestDifficulty difficulty;
    private final QuestPersona persona;
    private final String generatedDialogue;
    private final Location targetLocation;

    public void setObjectiveDescription(String objectiveDescription) {
        this.objectiveDescription = objectiveDescription;
    }

    private String objectiveDescription;
    private final double rewardAmount; // Assuming credit/money
    private final long expireTime;
    private Location startLocation;
    private UUID assignee;
    private QuestStatus status;
    private boolean objectiveMet = false;

    // Objective details
    private org.bukkit.inventory.ItemStack targetItem;
    private int targetAmount;
    private String targetMobId;
    private int progressCount = 0;

    public enum QuestStatus {
        CREATED, ACTIVE, COMPLETED
    }

    public DynamicQuest(QuestType type, QuestDifficulty difficulty, QuestPersona persona, String generatedDialogue, Location targetLocation, String objectiveDescription, double rewardAmount) {
        this.questId = UUID.randomUUID();
        this.type = type;
        this.difficulty = difficulty;
        this.persona = persona;
        this.generatedDialogue = generatedDialogue;
        this.targetLocation = targetLocation;
        this.objectiveDescription = objectiveDescription;
        this.rewardAmount = rewardAmount;
        this.expireTime = System.currentTimeMillis() + (1000 * 60 * 30); // 30 minutes to accept? Or complete?
        this.status = QuestStatus.CREATED;
    }

    public UUID getQuestId() {
        return questId;
    }

    public QuestType getType() {
        return type;
    }

    public QuestDifficulty getDifficulty() {
        return difficulty;
    }

    public QuestPersona getPersona() {
        return persona;
    }

    public String getGeneratedDialogue() {
        return generatedDialogue;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(Location startLocation) {
        this.startLocation = startLocation;
    }

    public UUID getAssignee() {
        return assignee;
    }

    public void setAssignee(UUID assignee) {
        this.assignee = assignee;
    }

    public QuestStatus getStatus() {
        return status;
    }

    public void setStatus(QuestStatus status) {
        this.status = status;
    }

    public boolean isObjectiveMet() {
        return objectiveMet;
    }

    public void setObjectiveMet(boolean objectiveMet) {
        this.objectiveMet = objectiveMet;
    }

    public org.bukkit.inventory.ItemStack getTargetItem() {
        return targetItem;
    }

    public void setTargetItem(org.bukkit.inventory.ItemStack targetItem) {
        this.targetItem = targetItem;
    }

    public int getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(int targetAmount) {
        this.targetAmount = targetAmount;
    }

    public String getTargetMobId() {
        return targetMobId;
    }

    public void setTargetMobId(String targetMobId) {
        this.targetMobId = targetMobId;
    }

    public int getProgressCount() {
        return progressCount;
    }

    public void setProgressCount(int progressCount) {
        this.progressCount = progressCount;
    }

    public String getObjectiveDescription() {
        return objectiveDescription;
    }

    public double getRewardAmount() {
        return rewardAmount;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }
}
