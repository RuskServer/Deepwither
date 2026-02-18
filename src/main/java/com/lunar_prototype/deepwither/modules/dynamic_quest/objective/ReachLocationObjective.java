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

    /**
     * Create an objective that completes when the assignee reaches a target location within a given radius.
     *
     * @param description   a human-readable description of the objective
     * @param targetLocation the target world location the player must reach
     * @param radius        the acceptance radius (in the same distance units as Location); the objective is satisfied when the player is within this distance of the target
     */
    public ReachLocationObjective(String description, Location targetLocation, double radius) {
        this.description = description;
        this.targetLocation = targetLocation;
        this.radiusSquared = radius * radius;
    }

    /**
     * Retrieves the objective's description.
     *
     * @return the objective description
     */
    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Indicates whether the objective has been marked as met for the given quest.
     *
     * @param quest the dynamic quest to check
     * @return `true` if the objective is marked as met for the provided quest, `false` otherwise
     */
    @Override
    public boolean isMet(DynamicQuest quest) {
        return quest.isObjectiveMet();
    }

    /**
     * Handle quest-related events and mark the objective complete when the assignee reaches the target location.
     *
     * If the provided event is a player movement event and the moving player is the quest's assignee,
     * this method checks whether the player is within the configured radius of the target location (and in the same world).
     * When those conditions are met the quest's objective is marked met and the assignee receives a gold-colored completion message.
     *
     * @param quest the DynamicQuest whose objective state may be updated
     * @param event the event to inspect; only PlayerMoveEvent events are considered
     */
    @Override
    public void onAction(DynamicQuest quest, Event event) {
        if (!(event instanceof PlayerMoveEvent)) return;
        if (quest.isObjectiveMet()) return;

        PlayerMoveEvent e = (PlayerMoveEvent) event;
        Player player = e.getPlayer();
        if (!player.getUniqueId().equals(quest.getAssignee())) return;

        Location playerLoc = player.getLocation();
        if (playerLoc.getWorld() != targetLocation.getWorld()) return;

        if (playerLoc.distanceSquared(targetLocation) < radiusSquared) {
            quest.setObjectiveMet(true);
            player.sendMessage(Component.text(">> クエスト目標地点に到達しました！NPCに報告してください。", NamedTextColor.GOLD));
        }
    }
}