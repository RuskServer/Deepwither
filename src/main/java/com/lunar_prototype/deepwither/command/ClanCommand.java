package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.clan.Clan;
import com.lunar_prototype.deepwither.clan.ClanManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final ClanManager clanManager;
    private final List<String> subCommands = Arrays.asList("create", "invite", "join", "info", "leave", "disband");

    public ClanCommand(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage("§c使用法: /clan create <名前>");
                    return true;
                }
                clanManager.createClan(player, args[1]);
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("§c使用法: /clan invite <プレイヤー名>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§cプレイヤーが見つかりません。");
                    return true;
                }
                clanManager.invitePlayer(player, target);
            }
            case "join" -> clanManager.joinClan(player);
            case "info" -> {
                Clan clan = clanManager.getClanByPlayer(player.getUniqueId());
                if (clan == null) {
                    player.sendMessage("§cクランに所属していません。");
                } else {
                    player.sendMessage("§6=== " + clan.getName() + " ===");
                    player.sendMessage("§7リーダー: " + Bukkit.getOfflinePlayer(clan.getOwner()).getName());
                    player.sendMessage("§7メンバー数: " + clan.getMembers().size());
                }
            }
            case "leave" -> clanManager.leaveClan(player);
            case "disband" -> clanManager.disbandClan(player);
            default -> sendHelp(player);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            // 第1引数: サブコマンドのリストをフィルタリングして返す
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("invite")) {
                // 第2引数が invite の場合: オンラインプレイヤー名を返す
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("create")) {
                return Arrays.asList("<クラン名>");
            }
        }

        return new ArrayList<>();
    }

    private void sendHelp(Player player) {
        player.sendMessage("§b=== Clan System Help ===");
        player.sendMessage("§f/clan create <name> §7- クランを作成");
        player.sendMessage("§f/clan invite <player> §7- プレイヤーを招待");
        player.sendMessage("§f/clan join §7- 招待を受ける");
        player.sendMessage("§f/clan info §7- 所属クランの情報");
        player.sendMessage("§f/clan leave §7- クランを脱退");
        player.sendMessage("§f/clan disband §7- クランを解散（リーダー用）");
    }
}