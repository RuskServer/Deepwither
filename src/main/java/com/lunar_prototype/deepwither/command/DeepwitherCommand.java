package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.dungeon.DungeonGenerator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
            sender.sendMessage("§c権限がありません。");
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
                // リロード処理など
                sender.sendMessage("§aDeepwitherの設定をリロードしました。");
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleDungeon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player))
            return;

        if (args.length < 2) {
            sendDungeonHelp(player);
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "generate" -> {
                // /dw dungeon generate <name> (maxLengthはconfig参照とするか固定にする)
                if (args.length < 3) {
                    player.sendMessage("§c使用法: /dw dungeon generate <ダンジョンタイプ>");
                    return;
                }
                String dungeonType = args[2];
                com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager.getInstance()
                        .createInstance(player, dungeonType);
            }
            case "join" -> {
                // /dw dungeon join <instanceId> (デバッグ用: 本来はGUIや看板から)
                if (args.length < 3) {
                    player.sendMessage("§c使用法: /dw dungeon join <インスタンスID>");
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
        player.sendMessage("§e[Dungeon Help]");
        player.sendMessage("§f/dw dungeon generate <type> §7- 新規インスタンス生成");
        player.sendMessage("§f/dw dungeon leave §7- ダンジョンから退出");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§d§l[Deepwither Admin Help]");
        sender.sendMessage("§f/dw dungeon ... §7- ダンジョン管理コマンド");
        sender.sendMessage("§f/dw reload §7- 設定リロード");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1)
            return Arrays.asList("dungeon", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("dungeon"))
            return Arrays.asList("generate", "join", "leave");
        if (args.length == 3 && args[1].equalsIgnoreCase("generate")) {
            // dungeonsフォルダ内のymlファイル名を取得してリスト化するのが理想
            return Arrays.asList("silent_terrarium_ruins", "ancient_city");
        }
        return new ArrayList<>();
    }
}