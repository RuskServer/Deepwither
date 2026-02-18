package com.lunar_prototype.deepwither.modules.dynamic_quest.objective;

import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.DynamicQuest;
import org.bukkit.event.Event;

public class RaidObjective implements IQuestObjective {
    @Override
    public String getDescription() {
        return "移動中の補給部隊を襲撃し、輸送車を破壊する";
    }

    @Override
    public boolean isMet(DynamicQuest quest) {
        return quest.isObjectiveMet();
    }

    @Override
    public void onAction(DynamicQuest quest, Event event) {
        // Raid logic is handled by SupplyConvoyEvent which sets quest.setObjectiveMet(true)
    }
}
