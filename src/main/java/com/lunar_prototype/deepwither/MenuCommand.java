package com.lunar_prototype.deepwither;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class MenuCommand implements CommandExecutor {

    private final MenuGUI menuGUI;

    public MenuCommand(MenuGUI menuGUI) {
        this.menuGUI = menuGUI;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤー専用です。");
            return true;
        }

        // メニューを開く
        menuGUI.open(player);
        return true;
    }
}