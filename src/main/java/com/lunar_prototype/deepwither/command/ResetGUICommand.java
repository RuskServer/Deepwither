package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.ResetGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ResetGUICommand implements CommandExecutor {

    private final ResetGUI resetGUI;

    public ResetGUICommand(ResetGUI resetGUI) {
        this.resetGUI = resetGUI;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 使用法: /resetstatusgui <player>
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /resetstatusgui <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cプレイヤーが見つかりません: " + args[0]);
            return true;
        }

        // GUIを開く
        resetGUI.open(target);
        sender.sendMessage("§a" + target.getName() + " にリセットメニューを開きました。");
        return true;
    }
}