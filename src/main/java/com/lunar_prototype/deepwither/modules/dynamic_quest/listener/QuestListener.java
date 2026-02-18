package com.lunar_prototype.deepwither.modules.dynamic_quest.listener;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.modules.dynamic_quest.enums.QuestType;
import com.lunar_prototype.deepwither.modules.dynamic_quest.npc.QuestNPC;
import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.DynamicQuest;
import com.lunar_prototype.deepwither.modules.dynamic_quest.service.QuestNPCManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class QuestListener implements Listener {

    private final Deepwither plugin;
    private final QuestNPCManager npcManager;

    /**
     * Creates a QuestListener and wires it to the given plugin and QuestNPC manager.
     *
     * @param plugin   the main Deepwither plugin instance used for scheduling and messaging
     * @param npcManager the manager that provides access to active QuestNPC instances
     */
    public QuestListener(Deepwither plugin, QuestNPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    /**
     * Handles player right-clicks on entities: routes interactions to active quest NPCs or
     * removes and notifies about invalidated quest NPC entities.
     *
     * <p>If the clicked entity corresponds to an active QuestNPC, the event is cancelled
     * and the interaction is delegated to the quest-handling logic. If the entity carries
     * the plugin's "quest_npc" persistent data marker but is not an active NPC, the event
     * is cancelled, the entity is removed, and the player is notified.
     *
     * @param event the player interact-entity event containing the clicked entity and player
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        Player player = event.getPlayer();

        for (QuestNPC npc : npcManager.getActiveNPCs()) {
            if (npc.isEntity(clicked.getUniqueId())) {
                event.setCancelled(true);
                handleNPCInteraction(player, npc);
                return;
            }
        }

        NamespacedKey key = new NamespacedKey(plugin, "quest_npc");
        if (clicked.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            clicked.remove();
            player.sendMessage(Component.text("このNPCは無効化されました。もう一度お試しください。", NamedTextColor.RED));
        }
    }

    /**
     * Render quest dialogue and interactive choices to the player for the given QuestNPC.
     *
     * <p>Sends a header and the quest persona, then displays content based on the quest status:
     * for CREATED quests, shows generated dialogue and clickable accept/decline options;
     * for ACTIVE quests, if the player is the assignee and the objective is met, shows a clickable report option to complete the quest;
     * if the assignee has not met the objective, shows progress and target location; if another player is the assignee, notifies the player.</p>
     *
     * @param player the player who interacted with the NPC
     * @param npc the QuestNPC that was interacted with (provides the associated DynamicQuest)
     */
    private void handleNPCInteraction(Player player, QuestNPC npc) {
        DynamicQuest quest = npc.getQuest();
        
        player.sendMessage(Component.text("--------------------------------", NamedTextColor.GRAY));
        player.sendMessage(Component.text(quest.getPersona().getDisplayName(), NamedTextColor.YELLOW)
                .append(Component.text(": ", NamedTextColor.GRAY)));

        if (quest.getStatus() == DynamicQuest.QuestStatus.CREATED) {
            player.sendMessage(Component.text(quest.getGeneratedDialogue(), NamedTextColor.WHITE));
            
            Component accept = Component.text("[受諾する]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/dq accept " + quest.getQuestId()))
                    .hoverEvent(HoverEvent.showText(Component.text("クリックしてクエストを開始")));
            
            Component decline = Component.text("[拒否する]", NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/dq decline " + quest.getQuestId()))
                    .hoverEvent(HoverEvent.showText(Component.text("この話を忘れる")));

            player.sendMessage(Component.empty());
            player.sendMessage(accept.append(Component.text("  ")).append(decline));
        } else if (quest.getStatus() == DynamicQuest.QuestStatus.ACTIVE) {
            if (quest.getAssignee().equals(player.getUniqueId())) {
                if (quest.getObjective().isMet(quest)) {
                    player.sendMessage(Component.text("よくやってくれた！これが約束の報酬だ。", NamedTextColor.WHITE));
                    Component report = Component.text("[報告する]", NamedTextColor.GOLD)
                            .clickEvent(ClickEvent.runCommand("/dq report " + quest.getQuestId()))
                            .hoverEvent(HoverEvent.showText(Component.text("クリックして報酬を受け取り、クエストを完了する")));
                    player.sendMessage(Component.empty());
                    player.sendMessage(report);
                } else {
                    player.sendMessage(Component.text("まだ終わっていないようだな。急いでくれ。", NamedTextColor.WHITE));
                    player.sendMessage(Component.text("目標地点: " + quest.getTargetLocation().getBlockX() + ", " + quest.getTargetLocation().getBlockZ(), NamedTextColor.GRAY));
                    player.sendMessage(Component.text("現在の目標: " + quest.getObjectiveDescription(), NamedTextColor.YELLOW));
                }
            } else {
                player.sendMessage(Component.text("他を当たってくれ。先客がいるんでな。", NamedTextColor.GRAY));
            }
        }
        player.sendMessage(Component.text("--------------------------------", NamedTextColor.GRAY));
    }

    /**
     * Forwards player movement events (when the player changes block) to active quest objectives.
     *
     * <p>Ignores movements that remain within the same block. For each active QuestNPC whose quest
     * status is ACTIVE and that has a non-null objective, invokes the objective's onAction with the
     * quest and the movement event.
     *
     * @param event the player movement event
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        for (QuestNPC npc : npcManager.getActiveNPCs()) {
            DynamicQuest quest = npc.getQuest();
            if (quest.getStatus() == DynamicQuest.QuestStatus.ACTIVE && quest.getObjective() != null) {
                quest.getObjective().onAction(quest, event);
            }
        }
    }

    /**
     * Forwards an entity death event to each active quest's objective for potential handling.
     *
     * @param event the entity death event to dispatch to active quest objectives
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        for (QuestNPC npc : npcManager.getActiveNPCs()) {
            DynamicQuest quest = npc.getQuest();
            if (quest.getStatus() == DynamicQuest.QuestStatus.ACTIVE && quest.getObjective() != null) {
                quest.getObjective().onAction(quest, event);
            }
        }
    }
}