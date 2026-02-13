package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.event.OpenSkillassignment;
import org.bukkit.Bukkit;
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
        Bukkit.getPluginManager().callEvent(new OpenSkillassignment(player));
        Deepwither.getInstance().getSkillAssignmentGUI().open(player);
        return true;
    }
}
