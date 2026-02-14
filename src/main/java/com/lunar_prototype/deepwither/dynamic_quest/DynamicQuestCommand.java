package com.lunar_prototype.deepwither.dynamic_quest;

import com.lunar_prototype.deepwither.dynamic_quest.obj.DynamicQuest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

        if (args.length < 2) {
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

        if (args.length < 2) {
            return false;
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
