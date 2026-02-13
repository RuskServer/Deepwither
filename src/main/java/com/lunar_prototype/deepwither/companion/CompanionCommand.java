package com.lunar_prototype.deepwither.companion;

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
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§c使用法: /companion <spawn|despawn|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn":
                if (args.length < 2) {
                    player.sendMessage("§cIDを指定してください。");
                    return true;
                }
                manager.spawnCompanion(player, args[1]);
                player.sendMessage("§aコンパニオン §f" + args[1] + " §aを召喚しました。");
                break;

            case "despawn":
                manager.despawnCompanion(player);
                player.sendMessage("§eコンパニオンを帰還させました。");
                break;

            case "reload":
                if (!player.hasPermission("admin")) return true;
                manager.loadConfig();
                player.sendMessage("§aCompanions config reloaded.");
                break;
            case "menu":
                new CompanionGui(manager).openGui(player);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("spawn", "despawn", "reload","menu");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            // managerのcompanionTypesのキーを補完に出す（外部公開メソッドをmanagerに追加してください）
            // return manager.getCompanionIds();
        }
        return new ArrayList<>();
    }
}