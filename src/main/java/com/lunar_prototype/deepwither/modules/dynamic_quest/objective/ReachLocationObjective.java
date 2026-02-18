package com.lunar_prototype.deepwither.modules.dynamic_quest.objective;

import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.DynamicQuest;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerMoveEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ReachLocationObjective implements IQuestObjective {
    private final String description;
    private final Location targetLocation;
    private final double radiusSquared;

    public ReachLocationObjective(String description, Location targetLocation, double radius) {
        this.description = description;
        this.targetLocation = targetLocation;
        this.radiusSquared = radius * radius;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isMet(DynamicQuest quest) {
        return quest.isObjectiveMet();
    }

    @Override
    public void onAction(DynamicQuest quest, Event event) {
        if (!(event instanceof PlayerMoveEvent)) return;
        PlayerMoveEvent e = (PlayerMoveEvent) event;
        Player player = e.getPlayer();
        if (!player.getUniqueId().equals(quest.getAssignee())) return;

        if (player.getLocation().distanceSquared(targetLocation) < radiusSquared) {
            quest.setObjectiveMet(true);
            player.sendMessage(Component.text(">> クエスト目標地点に到達しました！NPCに報告してください。", NamedTextColor.GOLD));
        }
    }
}
