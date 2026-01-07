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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
        if (!(sender instanceof Player player)) return;

        // /dw dungeon generate <name> <count>
        if (args.length < 4 || !args[1].equalsIgnoreCase("generate")) {
            player.sendMessage("§c使用法: /dw dungeon generate <ダンジョン名> <通路数>");
            return;
        }

        String dungeonName = args[2];
        int count;
        try {
            count = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c通路数は数値で指定してください。");
            return;
        }

        player.sendMessage("§e[Dungeon] §f" + dungeonName + " を生成中...");

        // 生成実行
        // ※実際にはここで「個別ワールド生成」と紐付けますが、まずは現在のワールドでテスト
        DungeonGenerator gen = new DungeonGenerator(dungeonName);
        gen.generateStraight(player.getWorld(), count);

        player.sendMessage("§a[Dungeon] 生成が完了しました！");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§d§l[Deepwither Admin Help]");
        sender.sendMessage("§f/dw dungeon generate <name> <count> §7- ダンジョン生成");
        sender.sendMessage("§f/dw reload §7- 設定リロード");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return Arrays.asList("dungeon", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("dungeon")) return Arrays.asList("generate");
        if (args.length == 3 && args[1].equalsIgnoreCase("generate")) {
            // dungeonsフォルダ内のymlファイル名を取得してリスト化するのが理想
            return Arrays.asList("straight_path", "slime_cave");
        }
        return new ArrayList<>();
    }
}