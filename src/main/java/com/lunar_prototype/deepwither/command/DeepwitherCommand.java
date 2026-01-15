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
                Deepwither.getInstance().getSkilltreeGUI().reload();
            }
            case "talk" -> handleTalk(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    /**
     * /dw talk <colorID> <urgency> <message...>
     * 例: /dw talk 1 0.8 こんにちは！
     * 1: 友好(Blue), 2: 威圧(Red) 等のColorフラグを指定
     */
    private void handleTalk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;

        if (args.length < 4) {
            player.sendMessage("§c使用法: /dw talk <ColorID> <Urgency(0-1)> <メッセージ...>");
            return;
        }

        try {
            long colorId = Long.parseLong(args[1]);
            double urgency = Double.parseDouble(args[2]);

            // メッセージの結合
            String message = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

            // EMDA_LanguageAI インスタンスの取得 (Deepwitherクラスに保持させている前提)
            // もしくはデバッグ用にその場で生成
            var ai = plugin.getAi();

            long startTime = System.nanoTime();

            // AIによる推論実行 (70μsの神速体験)
            String response = ai.generateResponse(colorId, urgency);

            long endTime = System.nanoTime();
            double microSeconds = (endTime - startTime) / 1000.0;

            // 結果表示
            player.sendMessage("§d[EMDA-AI] §f" + response);

            // パフォーマンスデバッグ表示 (Lunar_prototype仕様)
            if (microSeconds > 1000) {
                player.sendMessage(String.format("§8[Debug] DSR再編発生: %.2f ms", microSeconds / 1000.0));
            } else {
                player.sendMessage(String.format("§8[Debug] 推論時間: %.2f μs", microSeconds));
            }

        } catch (NumberFormatException e) {
            player.sendMessage("§c数値の指定が不正です。");
        }
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
            case "enter" -> {
                // /dw dungeon enter <dungeonType> <difficulty>
                if (args.length < 3) {
                    player.sendMessage("§c使用法: /dw dungeon enter <ダンジョンタイプ> [難易度]");
                    return;
                }
                String dungeonType = args[2];
                String difficulty = (args.length >= 4) ? args[3] : "normal";

                // PvPvEDungeonManagerを呼び出す (シングルトンまたはDeepwither経由)
                Deepwither.getInstance().getPvPvEDungeonManager().enterPvPvEDungeon(player, dungeonType, difficulty);
            }
            case "generate" -> {
                if (!player.hasPermission("deepwither.admin")) {
                    player.sendMessage("§c権限がありません。");
                    return;
                }
                // /dw dungeon generate <name> (maxLengthはconfig参照とするか固定にする)
                if (args.length < 3) {
                    player.sendMessage("§c使用法: /dw dungeon generate <ダンジョンタイプ>");
                    return;
                }
                String dungeonType = args[2];
                com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager.getInstance()
                        .createDungeonInstance(player, dungeonType,"normal");
            }
            case "join" -> {
                if (!player.hasPermission("deepwither.admin")) {
                    player.sendMessage("§c権限がありません。");
                    return;
                }
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
        player.sendMessage("§b/dw dungeon enter <type> [diff] §7- PvPvEダンジョンに参戦");
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
            return Arrays.asList("dungeon", "reload", "talk");
        if (args.length == 2 && args[0].equalsIgnoreCase("dungeon"))
            return Arrays.asList("generate", "join", "leave","enter");
        if (args.length == 3 && args[1].equalsIgnoreCase("generate")) {
            // dungeonsフォルダ内のymlファイル名を取得してリスト化するのが理想
            return Arrays.asList("silent_terrarium_ruins", "ancient_city");
        }
        return new ArrayList<>();
    }
}