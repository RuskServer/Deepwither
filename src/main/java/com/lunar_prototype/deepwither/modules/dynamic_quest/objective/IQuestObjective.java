package com.lunar_prototype.deepwither.modules.dynamic_quest.objective;

import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.DynamicQuest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public interface IQuestObjective {
    /**
 * Provides a human-readable description of this quest objective.
 *
 * @return the textual description of the objective
 */
String getDescription();
    /**
 * Determines whether this objective has been satisfied for the provided quest.
 *
 * @param quest the DynamicQuest instance whose state is used to evaluate completion
 * @return true if the objective is met for the given quest, false otherwise
 */
boolean isMet(DynamicQuest quest);
    /**
 * Handle an action or event that may affect this quest objective.
 *
 * @param quest the DynamicQuest instance containing this objective
 * @param event the Bukkit event that triggered the action or change to the objective
 */
void onAction(DynamicQuest quest, Event event);
}