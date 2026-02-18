package com.lunar_prototype.deepwither.modules.dynamic_quest;

import com.lunar_prototype.deepwither.modules.dynamic_quest.enums.QuestType;
import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.QuestLocation;
import com.lunar_prototype.deepwither.modules.dynamic_quest.repository.QuestLocationRepository;
import com.lunar_prototype.deepwither.modules.dynamic_quest.service.QuestNPCManager;
import com.lunar_prototype.deepwither.modules.dynamic_quest.service.QuestService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DynamicQuestCommand implements CommandExecutor {

    private final QuestNPCManager npcManager;
    private final QuestService questService;
    private final QuestLocationRepository repository;

    /**
     * Create a DynamicQuestCommand with its required collaborators.
     *
     * @param npcManager   manager responsible for spawning and refreshing quest NPCs and querying NPC layers
     * @param questService service that handles quest actions (accepting and reporting)
     * @param repository   repository for loading, retrieving, and storing quest locations
     */
    public DynamicQuestCommand(QuestNPCManager npcManager, QuestService questService, QuestLocationRepository repository) {
        this.npcManager = npcManager;
        this.questService = questService;
        this.repository = repository;
    }

    /**
     * Handles the "/dq" dynamic quest command for players, dispatching subcommands for NPC management,
     * location configuration, and quest interactions.
     *
     * Supported subcommands:
     * - spawn: force-spawn an NPC at the player's current location
     * - reload: reload location data and refresh NPCs
     * - status: show the number of active NPCs
     * - addloc <type> <name> [1|2]: add or update a quest location position (pos 1 or 2)
     * - accept <questId>: accept the specified quest
     * - decline <questId>: decline the specified quest
     * - report <questId>: report the specified quest
     *
     * @param sender the source of the command; only Player senders are accepted (non-player senders receive a "Players only." message)
     * @param command the command being executed
     * @param label the alias of the command used
     * @param args the command arguments where args[0] is the subcommand and subsequent elements are subcommand parameters
     * @return `true` if the command was handled (including when usage or error messages were sent), `false` to indicate that the default usage message should be displayed
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            return false;
        }

        String action = args[0].toLowerCase();
        
        if (action.equals("spawn")) {
            npcManager.spawnNPC(player.getLocation());
            player.sendMessage(Component.text("NPC Force Spawned at your location.", NamedTextColor.GREEN));
            return true;
        }

        if (action.equals("reload")) {
            repository.load();
            npcManager.refreshNPCs();
            player.sendMessage(Component.text("NPC Refresh cycle manually triggered.", NamedTextColor.YELLOW));
            return true;
        }

        if (action.equals("status")) {
            player.sendMessage(Component.text("Active NPCs: " + npcManager.getActiveNPCs().size(), NamedTextColor.AQUA));
            return true;
        }

        if (action.equals("addloc")) {
            if (args.length < 3) {
                player.sendMessage(Component.text("Usage: /dq addloc <type> <name> [1|2]", NamedTextColor.RED));
                return true;
            }

            QuestType type;
            try {
                type = QuestType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text("Invalid Quest Type.", NamedTextColor.RED));
                return true;
            }

            String name = args[2];
            int posIndex = (args.length >= 4) ? Integer.parseInt(args[3]) : 1;

            QuestLocation existing = repository.getLocation(type, name);
            Location current = player.getLocation();
            int layerId = npcManager.getLayerId(current);
            QuestLocation updated;

            if (existing == null) {
                if (posIndex == 1) {
                    updated = new QuestLocation(name, current, layerId);
                } else {
                    updated = new QuestLocation(name, null, current, layerId);
                }
            } else {
                if (posIndex == 1) {
                    updated = new QuestLocation(name, current, existing.getPos2(), layerId);
                } else {
                    updated = new QuestLocation(name, existing.getPos(), current, layerId);
                }
            }

            repository.addLocation(type, updated);
            player.sendMessage(Component.text("Location '" + name + "' (Layer " + layerId + ") updated for " + type.name() + " at Pos " + posIndex, NamedTextColor.GREEN));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /dq <accept|decline|report> <questId>", NamedTextColor.RED));
            return true;
        }

        String questIdStr = args[1];
        UUID questId;
        try {
            questId = UUID.fromString(questIdStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid Quest ID.", NamedTextColor.RED));
            return true;
        }

        if (action.equals("accept")) {
            questService.acceptQuest(player, questId);
        } else if (action.equals("decline")) {
            player.sendMessage(Component.text("クエストを拒否しました。", NamedTextColor.GRAY));
        } else if (action.equals("report")) {
            questService.reportQuest(player, questId);
        }

        return true;
    }
}