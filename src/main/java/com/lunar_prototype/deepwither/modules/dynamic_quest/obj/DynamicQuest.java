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

    /**
     * Create a new DynamicQuest with the given metadata, target location, and reward.
     *
     * The constructor generates a new quest UUID, sets the quest expiration to 30 minutes
     * from creation, and initializes the quest status to CREATED.
     *
     * @param type             the quest's type
     * @param difficulty       the quest's difficulty level
     * @param persona          the persona (quest giver) associated with the quest
     * @param generatedDialogue the dialogue text generated for the quest
     * @param targetLocation   the destination or target location for the quest
     * @param rewardAmount     the reward amount awarded for completing the quest
     */
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

    /**
     * Assigns the objective for this quest.
     *
     * @param objective the objective to attach to the quest, or {@code null} to clear it
     */
    public void setObjective(IQuestObjective objective) {
        this.objective = objective;
    }

    /**
     * Retrieve the quest's current objective.
     *
     * @return the assigned {@code IQuestObjective}, or {@code null} if no objective is set
     */
    public IQuestObjective getObjective() {
        return objective;
    }

    /**
     * Get a human-readable description of the quest objective.
     *
     * @return the objective's description if set, otherwise "Unknown Objective"
     */
    public String getObjectiveDescription() {
        return objective != null ? objective.getDescription() : "Unknown Objective";
    }

    /**
 * Gets the quest's unique identifier.
 *
 * @return the `UUID` that uniquely identifies this quest
 */
public UUID getQuestId() { return questId; }
    /**
 * Gets the quest's type.
 *
 * @return the quest's type as a {@link QuestType}
 */
public QuestType getType() { return type; }
    /**
 * Gets the quest difficulty level.
 *
 * @return the quest difficulty level
 */
public QuestDifficulty getDifficulty() { return difficulty; }
    /**
 * Get the persona associated with this quest.
 *
 * @return the `QuestPersona` that represents the quest giver or source
 */
public QuestPersona getPersona() { return persona; }
    /**
 * The dialogue text generated for this quest.
 *
 * @return the generated dialogue text for the quest
 */
public String getGeneratedDialogue() { return generatedDialogue; }
    /**
 * Gets the quest's target location.
 *
 * @return the Location that represents the destination relevant to this quest
 */
public Location getTargetLocation() { return targetLocation; }
    /**
 * Gets the quest's start location, if one has been assigned.
 *
 * @return the start Location, or null if none has been set
 */
public Location getStartLocation() { return startLocation; }
    /**
 * Sets the optional starting location for the quest.
 *
 * @param startLocation the location where the quest begins; may be {@code null} to indicate no specific start
 */
public void setStartLocation(Location startLocation) { this.startLocation = startLocation; }
    /**
 * Gets the UUID of the entity assigned to this quest.
 *
 * @return the assignee's UUID, or {@code null} if no assignee is set
 */
public UUID getAssignee() { return assignee; }
    /**
 * Assigns the quest to the specified assignee.
 *
 * @param assignee UUID of the player or entity assigned to the quest, or {@code null} to unassign
 */
public void setAssignee(UUID assignee) { this.assignee = assignee; }
    /**
 * Gets the current lifecycle status of the quest.
 *
 * @return the current quest status
 */
public QuestStatus getStatus() { return status; }
    /**
 * Sets the quest's lifecycle status.
 *
 * @param status the new lifecycle status for the quest
 */
public void setStatus(QuestStatus status) { this.status = status; }
    /**
 * Indicates whether the quest's objective has been met.
 *
 * @return `true` if the quest's objective has been met, `false` otherwise.
 */
public boolean isObjectiveMet() { return objectiveMet; }
    /**
 * Mark whether the quest's objective has been met.
 *
 * @param objectiveMet `true` if the objective is met, `false` otherwise
 */
public void setObjectiveMet(boolean objectiveMet) { this.objectiveMet = objectiveMet; }
    /**
 * Gets the current progress count toward the quest objective.
 *
 * @return the number of progress steps completed for this quest's objective
 */
public int getProgressCount() { return progressCount; }
    /**
 * Set the current progress counter for this quest's objective.
 *
 * @param progressCount the new progress value representing completed units toward the objective (e.g., steps, items, or milestones)
 */
public void setProgressCount(int progressCount) { this.progressCount = progressCount; }
    /**
 * Get the reward amount associated with this quest.
 *
 * @return the reward amount for completing the quest
 */
public double getRewardAmount() { return rewardAmount; }
    /**
 * Indicates whether the quest has expired.
 *
 * @return {@code true} if the current system time is greater than the quest's expiration timestamp, {@code false} otherwise.
 */
public boolean isExpired() { return System.currentTimeMillis() > expireTime; }

    /**
 * Gets the item required for item-based quest objectives.
 *
 * @return the ItemStack representing the quest's target item, or `null` if no item target is set
 */
public ItemStack getTargetItem() { return targetItem; }
    /**
 * Sets the item that must be delivered or fetched to satisfy the quest's item objective.
 *
 * @param targetItem the ItemStack representing the required target item, or null to clear the item target
 */
public void setTargetItem(ItemStack targetItem) { this.targetItem = targetItem; }
    /**
 * Gets the required quantity of the quest's target item.
 *
 * @return the quantity required to satisfy the quest's target
 */
public int getTargetAmount() { return targetAmount; }
    /**
 * Sets the required quantity of the quest's target item for fetch or delivery objectives.
 *
 * @param targetAmount the number of items required to complete the target objective
 */
public void setTargetAmount(int targetAmount) { this.targetAmount = targetAmount; }
}