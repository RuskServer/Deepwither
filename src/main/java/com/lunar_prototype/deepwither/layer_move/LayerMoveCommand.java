package com.lunar_prototype.deepwither.layer_move;

import com.lunar_prototype.deepwither.Deepwither;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter; // 追加
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// TabCompleterを実装に追加
public class LayerMoveCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // ...既存のonCommand処理...
        if (!(sender instanceof Player player)) return false;
        if (!player.hasPermission("deepwither.admin")) return false;

        if (args.length == 0) {
            player.sendMessage(Component.text("使用法: /layermove <create|setloc|link>", NamedTextColor.RED));
            return true;
        }

        LayerMoveManager manager = Deepwither.getInstance().getLayerMoveManager();

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 4) {
                    player.sendMessage(Component.text("使用法: /layermove create <ID> <階層名> <サブタイトル>", NamedTextColor.RED));
                    return true;
                }
                manager.createWarp(args[1], args[2], args[3]);
                player.sendMessage(Component.text("ワープを作成しました: " + args[1], NamedTextColor.GREEN));
                break;

            case "setloc":
                if (args.length < 2) {
                    player.sendMessage(Component.text("使用法: /layermove setloc <ID>", NamedTextColor.RED));
                    return true;
                }
                manager.setWarpOrigin(args[1], player.getLocation());
                player.sendMessage(Component.text("座標を設定しました: " + args[1], NamedTextColor.GREEN));
                break;

            case "link":
                if (args.length < 3) {
                    player.sendMessage(Component.text("使用法: /layermove link <ID1> <ID2>", NamedTextColor.RED));
                    return true;
                }
                manager.linkWarps(args[1], args[2]);
                player.sendMessage(Component.text("リンクを設定しました: " + args[1] + " <-> " + args[2], NamedTextColor.GREEN));
                break;

            default:
                player.sendMessage(Component.text("不明なサブコマンドです。", NamedTextColor.RED));
                break;
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("deepwither.admin")) return new ArrayList<>();

        // 第1引数: サブコマンドの補完
        if (args.length == 1) {
            return Arrays.asList("create", "setloc", "link").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // 第2引数以降: 既存のワープIDを補完
        if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            LayerMoveManager manager = Deepwither.getInstance().getLayerMoveManager();

            // setloc <ID> や link <ID1> <ID2> の時、登録済みのIDを出す
            if (subCommand.equals("setloc") || subCommand.equals("link")) {
                return manager.getAllWarpData().stream()
                        .map(data -> data.id) // WarpDataからIDを抽出
                        .filter(id -> id.startsWith(args[args.length - 1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}