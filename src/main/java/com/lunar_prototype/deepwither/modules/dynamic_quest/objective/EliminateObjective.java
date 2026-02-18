package com.lunar_prototype.deepwither.modules.dynamic_quest.objective;

import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.DynamicQuest;
import com.lunar_prototype.deepwither.modules.integration.service.IMobService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class EliminateObjective implements IQuestObjective {
    private final String targetMobId;
    private final int targetAmount;
    private final IMobService mobService;

    /**
     * Creates an EliminateObjective that tracks eliminations of a specific mob type until a target count is reached.
     *
     * @param targetMobId  the identifier of the mob type to be eliminated
     * @param targetAmount the number of eliminations required to complete the objective
     * @param mobService   service used to resolve entities to their mob identifiers
     */
    public EliminateObjective(String targetMobId, int targetAmount, IMobService mobService) {
        this.targetMobId = targetMobId;
        this.targetAmount = targetAmount;
        this.mobService = mobService;
    }

    /**
     * Provides a human-readable description of this objective in Japanese.
     *
     * @return a string in the form "<targetMobId> を <targetAmount>体討伐する" describing the target mob and required count
     */
    @Override
    public String getDescription() {
        return targetMobId + " を " + targetAmount + "体討伐する";
    }

    /**
     * Checks whether the objective's required elimination count has been reached for the given quest.
     *
     * @param quest the quest whose current progress is evaluated
     * @return `true` if the quest's progress count is greater than or equal to the target amount, `false` otherwise
     */
    @Override
    public boolean isMet(DynamicQuest quest) {
        return quest.getProgressCount() >= targetAmount;
    }

    /**
     * Handles quest-related events to update elimination progress when the assignee kills a matching mob.
     *
     * Processes entity-death events: if the quest assignee killed an entity whose mob ID matches the objective's
     * target (case-insensitive), increments the quest's progress count, marks the objective met when the target
     * amount is reached, and sends the assignee a progress or completion message.
     *
     * @param quest the quest whose progress may be updated
     * @param event the event to process (only entity-death events are relevant)
     */
    @Override
    public void onAction(DynamicQuest quest, Event event) {
        if (!(event instanceof EntityDeathEvent)) return;
        EntityDeathEvent e = (EntityDeathEvent) event;
        LivingEntity victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || !killer.getUniqueId().equals(quest.getAssignee())) return;

        String killedMobId = mobService.getMobId(victim);

        if (killedMobId.equalsIgnoreCase(targetMobId)) {
            quest.setProgressCount(quest.getProgressCount() + 1);
            if (quest.getProgressCount() >= targetAmount) {
                quest.setObjectiveMet(true);
                killer.sendMessage(Component.text(">> ターゲットの排除が完了しました！NPCに報告してください。", NamedTextColor.GOLD));
            } else {
                killer.sendMessage(Component.text(">> " + targetMobId + " を排除 (" + quest.getProgressCount() + "/" + targetAmount + ")", NamedTextColor.YELLOW));
            }
        }
    }
}