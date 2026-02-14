package com.lunar_prototype.deepwither.companion;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CompanionCommand implements CommandExecutor, TabCompleter {

    private final CompanionManager manager;

    public CompanionCommand(CompanionManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行可能です。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("使用法: /companion <spawn|despawn|reload>", NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("IDを指定してください。", NamedTextColor.RED));
                    return true;
                }
                manager.spawnCompanion(player, args[1]);
                player.sendMessage(Component.text("コンパニオン ", NamedTextColor.GREEN)
                        .append(Component.text(args[1], NamedTextColor.WHITE))
                        .append(Component.text(" を召喚しました。", NamedTextColor.GREEN)));
            }
            case "despawn" -> {
                manager.despawnCompanion(player);
                player.sendMessage(Component.text("コンパニオンを帰還させました。", NamedTextColor.YELLOW));
            }
            case "reload" -> {
                if (!player.hasPermission("admin")) return true;
                manager.loadConfig();
                player.sendMessage(Component.text("Companions config reloaded.", NamedTextColor.GREEN));
            }
            case "menu" -> new CompanionGui(manager).openGui(player);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("spawn", "despawn", "reload", "menu");
        }
        return new ArrayList<>();
    }
}
