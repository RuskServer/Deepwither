package com.lunar_prototype.deepwither.modules.dynamic_quest.objective;

import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.DynamicQuest;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

public class FetchObjective implements IQuestObjective {
    private final Material material;
    private final int amount;

    /**
     * Creates a fetch objective for the specified material and quantity.
     *
     * @param material the Material that must be collected
     * @param amount the required quantity of the material (number of items)
     */
    public FetchObjective(Material material, int amount) {
        this.material = material;
        this.amount = amount;
    }

    /**
     * Constructs a localized description of the fetch objective.
     *
     * @return A string describing the required material and quantity in the format
     *         "MATERIAL_NAME を <amount>個持ってくる".
     */
    @Override
    public String getDescription() {
        return material.name() + " を " + amount + "個持ってくる";
    }

    /**
     * Checks whether this objective is currently satisfied for the given quest.
     *
     * @param quest the quest whose objective status to check
     * @return `true` if the objective is satisfied for the quest, `false` otherwise
     */
    @Override
    public boolean isMet(DynamicQuest quest) {
        // Fetch is typically checked at the NPC interaction (reporting)
        // so we don't necessarily track it via events in real-time if we check inventory at report.
        // But we can mark it met if we want.
        return quest.isObjectiveMet();
    }

    /**
     * No-op action handler; fetch objectives are evaluated when the quest is reported rather than on events.
     *
     * @param quest the quest whose objective context this action would affect
     * @param event the triggering event (ignored for fetch objectives)
     */
    @Override
    public void onAction(DynamicQuest quest, Event event) {
        // Inventory checking is done at reportQuest
    }
}