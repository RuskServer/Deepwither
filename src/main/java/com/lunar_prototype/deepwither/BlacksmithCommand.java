package com.lunar_prototype.deepwither;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BlacksmithCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        String targetName = args[0];

        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("ターゲットプレイヤーが見つかりません: " + targetName, NamedTextColor.RED));
            return true;
        }

        new BlacksmithGUI().openGUI(targetPlayer);
        return true;
    }
}