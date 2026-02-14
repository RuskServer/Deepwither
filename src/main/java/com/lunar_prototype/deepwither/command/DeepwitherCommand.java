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
            case "train" -> handleTrain(sender, args);
            case "reload" -> {
                sender.sendMessage(Component.text("Deepwitherの設定をリロードしました。", NamedTextColor.GREEN));
                Deepwither.getInstance().getSkilltreeGUI().reload();
            }
            case "talk" -> handleTalk(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleTrain(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("使用法: /dw train <ファイル名.csv>", NamedTextColor.RED));
            return;
        }

        String fileName = args[1];
        if (!fileName.endsWith(".csv")) fileName += ".csv";

        File trainingDir = new File(plugin.getDataFolder(), "training");
        if (!trainingDir.exists()) trainingDir.mkdirs();

        File csvFile = new File(trainingDir, fileName);

        if (!csvFile.exists()) {
            sender.sendMessage(Component.text("ファイルが見つかりません: " + csvFile.getPath(), NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("[EMDA-AI] ", NamedTextColor.LIGHT_PURPLE).append(Component.text("学習を開始します。進捗はコンソールを確認してください...", NamedTextColor.WHITE)));
        plugin.getAi().trainFromCSVAsync(csvFile);
    }

    private void handleTalk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;

        if (args.length < 4) {
            player.sendMessage(Component.text("使用法: /dw talk <ColorID> <Urgency(0-1)> <メッセージ...>", NamedTextColor.RED));
            return;
        }

        try {
            double colorId = Double.parseDouble(args[1]);
            double urgency = Double.parseDouble(args[2]);
            String message = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            var ai = plugin.getAi();

            long startTime = System.nanoTime();
            String response = ai.generateResponse(message,colorId,urgency);
            long endTime = System.nanoTime();
            double microSeconds = (endTime - startTime) / 1000.0;

            player.sendMessage(Component.text("[EMDA-AI] ", NamedTextColor.LIGHT_PURPLE).append(Component.text(response, NamedTextColor.WHITE)));

            if (microSeconds > 1000) {
                player.sendMessage(Component.text(String.format("[Debug] DSR再編発生: %.2f ms", microSeconds / 1000.0), NamedTextColor.DARK_GRAY));
            } else {
                player.sendMessage(Component.text(String.format("[Debug] 推論時間: %.2f μs", microSeconds), NamedTextColor.DARK_GRAY));
            }

        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("数値の指定が不正です。", NamedTextColor.RED));
        }
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
        if (args.length == 1) return Arrays.asList("dungeon", "reload", "talk");
        if (args.length == 2 && args[0].equalsIgnoreCase("dungeon")) return Arrays.asList("generate", "join", "leave","enter");
        if (args.length == 3 && args[1].equalsIgnoreCase("generate")) {
            return Arrays.asList("silent_terrarium_ruins", "ancient_city");
        }
        return new ArrayList<>();
    }
}
