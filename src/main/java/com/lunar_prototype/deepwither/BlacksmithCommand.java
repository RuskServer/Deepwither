package com.lunar_prototype.deepwither;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BlacksmithCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String targetName = args[0];

        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "ターゲットプレイヤーが見つかりません: " + targetName);
            return true;
        }

        new BlacksmithGUI().openGUI(targetPlayer);
        return true;
    }
}
