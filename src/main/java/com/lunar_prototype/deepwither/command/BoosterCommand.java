package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.booster.BoosterManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BoosterCommand implements CommandExecutor {
    private final BoosterManager boosterManager;

    public BoosterCommand(BoosterManager boosterManager) {
        this.boosterManager = boosterManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("deepwither.admin")) return true;

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }

            double mult = Double.parseDouble(args[2]);
            int mins = Integer.parseInt(args[3]);

            boosterManager.addBooster(target, mult, mins);
            sender.sendMessage(Component.text("Applied ", NamedTextColor.GREEN)
                    .append(Component.text(mult + "x", NamedTextColor.YELLOW))
                    .append(Component.text(" EXP Booster to ", NamedTextColor.GREEN))
                    .append(Component.text(target.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" for " + mins + "m.", NamedTextColor.GREEN)));
            
            target.sendMessage(Component.text("EXP BOOSTER! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("You gained a ", NamedTextColor.YELLOW))
                    .append(Component.text(mult + "x", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" EXP multiplier for " + mins + " minutes!", NamedTextColor.YELLOW)));
            return true;
        }
        return false;
    }
}
