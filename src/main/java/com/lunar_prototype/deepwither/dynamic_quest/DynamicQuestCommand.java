package com.lunar_prototype.deepwither.dynamic_quest;

import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestType;
import com.lunar_prototype.deepwither.dynamic_quest.obj.QuestLocation;
import com.lunar_prototype.deepwither.dynamic_quest.obj.DynamicQuest;
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

    private final DynamicQuestManager manager;

    public DynamicQuestCommand(DynamicQuestManager manager) {
        this.manager = manager;
    }

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
            manager.forceSpawnAt(player.getLocation());
            player.sendMessage(Component.text("NPC Force Spawned at your location.", NamedTextColor.GREEN));
            return true;
        }

        if (action.equals("reload")) {
            manager.reload();
            player.sendMessage(Component.text("NPC Refresh cycle manually triggered.", NamedTextColor.YELLOW));
            return true;
        }

        if (action.equals("status")) {
            player.sendMessage(Component.text("Active NPCs: " + manager.getActiveNPCCount(), NamedTextColor.AQUA));
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

            QuestLocation existing = manager.getQuestLocation(type, name);
            Location current = player.getLocation();
            int layerId = manager.getLayerId(current);
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

            manager.addQuestLocation(type, updated);
            player.sendMessage(Component.text("Location '" + name + "' (Layer " + layerId + ") updated for " + type.name() + " at Pos " + posIndex, NamedTextColor.GREEN));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /dq <accept|decline> <questId>", NamedTextColor.RED));
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
            manager.acceptQuest(player, questId);
        } else if (action.equals("decline")) {
            manager.declineQuest(player, questId);
        }

        return true;
    }
}
