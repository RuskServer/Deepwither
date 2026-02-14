package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.ResetGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /resetstatusgui <player>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("プレイヤーが見つかりません: " + args[0], NamedTextColor.RED));
            return true;
        }

        resetGUI.open(target);
        sender.sendMessage(Component.text(target.getName(), NamedTextColor.GREEN).append(Component.text(" にリセットメニューを開きました。", NamedTextColor.GREEN)));
        return true;
    }
}
