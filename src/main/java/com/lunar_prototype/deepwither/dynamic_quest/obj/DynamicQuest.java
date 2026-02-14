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
    private final String objectiveDescription;
    private final double rewardAmount; // Assuming credit/money
    private final long expireTime;

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
