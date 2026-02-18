package com.lunar_prototype.deepwither.modules.dynamic_quest.objective;

import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.DynamicQuest;
import org.bukkit.event.Event;

public class RaidObjective implements IQuestObjective {
    /**
     * Objective text shown to players describing the raid objective.
     *
     * @return the objective description: "移動中の補給部隊を襲撃し、輸送車を破壊する"
     */
    @Override
    public String getDescription() {
        return "移動中の補給部隊を襲撃し、輸送車を破壊する";
    }

    /**
     * Checks whether the given quest's objective has been met.
     *
     * @param quest the quest whose objective status to check
     * @return `true` if the quest's objective is met, `false` otherwise
     */
    @Override
    public boolean isMet(DynamicQuest quest) {
        return quest.isObjectiveMet();
    }

    /**
     * Processes an incoming action event for this objective; this implementation intentionally performs no work because objective completion is managed by the event system.
     *
     * @param quest the quest instance this objective belongs to
     * @param event the Bukkit event that triggered the action
     */
    @Override
    public void onAction(DynamicQuest quest, Event event) {
        // Raid logic is handled by SupplyConvoyEvent which sets quest.setObjectiveMet(true)
    }
}