package com.lunar_prototype.deepwither;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SkillAssignmentCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (!(commandSender instanceof Player player)) {
            commandSender.sendMessage("このコマンドはプレイヤー専用です。");
            return true;
        }
        Deepwither.getInstance().getSkillAssignmentGUI().open(player);
        return true;
    }
}
