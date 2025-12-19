package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.booster.BoosterManager;
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

        // /expbooster give <player> <multiplier> <minutes>
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }

            double mult = Double.parseDouble(args[2]);
            int mins = Integer.parseInt(args[3]);

            boosterManager.addBooster(target, mult, mins);
            sender.sendMessage("§aApplied " + mult + "x EXP Booster to " + target.getName() + " for " + mins + "m.");
            target.sendMessage("§6§lEXP BOOSTER! §eYou gained a " + mult + "x EXP multiplier for " + mins + " minutes!");
            return true;
        }
        return false;
    }
}