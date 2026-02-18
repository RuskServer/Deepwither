package com.lunar_prototype.deepwither.modules.dynamic_quest.objective;

import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.DynamicQuest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public interface IQuestObjective {
    String getDescription();
    boolean isMet(DynamicQuest quest);
    void onAction(DynamicQuest quest, Event event);
}
