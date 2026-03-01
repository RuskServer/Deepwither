package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.Deepwither;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DeepwitherCommand implements CommandExecutor, TabCompleter {

    private final Deepwither plugin;

    public DeepwitherCommand(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("deepwither.admin")) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "dungeon" -> handleDungeon(sender, args);
            case "reload" -> {
                sender.sendMessage(Component.text("Deepwitherの設定をリロードしました。", NamedTextColor.GREEN));
                Deepwither.getInstance().getSkilltreeGUI().reload();
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleDungeon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 2) {
            sendDungeonHelp(player);
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "enter" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("使用法: /dw dungeon enter <ダンジョンタイプ> [難易度]", NamedTextColor.RED));
                    return;
                }
                String dungeonType = args[2];
                String difficulty = (args.length >= 4) ? args[3] : "normal";
                Deepwither.getInstance().getPvPvEDungeonManager().enterPvPvEDungeon(player, dungeonType, difficulty);
            }
            case "generate" -> {
                if (!player.hasPermission("deepwither.admin")) {
                    player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Component.text("使用法: /dw dungeon generate <ダンジョンタイプ>", NamedTextColor.RED));
                    return;
                }
                String dungeonType = args[2];
                com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager.getInstance()
                        .createDungeonInstance(player, dungeonType,"normal");
            }
            case "join" -> {
                if (!player.hasPermission("deepwither.admin")) {
                    player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Component.text("使用法: /dw dungeon join <インスタンスID>", NamedTextColor.RED));
                    return;
                }
                String instanceId = args[2];
                com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager.getInstance()
                        .joinDungeon(player, instanceId);
            }
            case "leave" -> {
                com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager.getInstance()
                        .leaveDungeon(player);
            }
            default -> sendDungeonHelp(player);
        }
    }

    private void sendDungeonHelp(Player player) {
        player.sendMessage(Component.text("[Dungeon Help]", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/dw dungeon enter <type> [diff]", NamedTextColor.AQUA).append(Component.text(" - PvPvEダンジョンに参戦", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/dw dungeon generate <type>", NamedTextColor.WHITE).append(Component.text(" - 新規インスタンス生成", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/dw dungeon leave", NamedTextColor.WHITE).append(Component.text(" - ダンジョンから退出", NamedTextColor.GRAY)));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("[Deepwither Admin Help]", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/dw dungeon ...", NamedTextColor.WHITE).append(Component.text(" - ダンジョン管理コマンド", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/dw reload", NamedTextColor.WHITE).append(Component.text(" - 設定リロード", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) return Arrays.asList("dungeon", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("dungeon")) return Arrays.asList("generate", "join", "leave","enter");
        if (args.length == 3 && args[1].equalsIgnoreCase("generate")) {
            return Arrays.asList("silent_terrarium_ruins", "ancient_city");
        }
        return new ArrayList<>();
    }
}
