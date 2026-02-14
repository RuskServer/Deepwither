package com.lunar_prototype.deepwither;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            sender.sendMessage(Component.text("このコマンドはプレイヤー専用です。", NamedTextColor.RED));
            return true;
        }

        menuGUI.open(player);
        return true;
    }
}
