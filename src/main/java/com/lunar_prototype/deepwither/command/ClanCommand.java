package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.clan.Clan;
import com.lunar_prototype.deepwither.clan.ClanManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
                    player.sendMessage(Component.text("使用法: /clan create <名前>", NamedTextColor.RED));
                    return true;
                }
                clanManager.createClan(player, args[1]);
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("使用法: /clan invite <プレイヤー名>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("プレイヤーが見つかりません。", NamedTextColor.RED));
                    return true;
                }
                clanManager.invitePlayer(player, target);
            }
            case "join" -> clanManager.joinClan(player);
            case "info" -> {
                Clan clan = clanManager.getClanByPlayer(player.getUniqueId());
                if (clan == null) {
                    player.sendMessage(Component.text("クランに所属していません。", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("=== " + clan.getName() + " ===", NamedTextColor.GOLD, TextDecoration.BOLD));
                    player.sendMessage(Component.text("リーダー: ", NamedTextColor.GRAY).append(Component.text(Bukkit.getOfflinePlayer(clan.getOwner()).getName(), NamedTextColor.WHITE)));
                    player.sendMessage(Component.text("メンバー数: ", NamedTextColor.GRAY).append(Component.text(clan.getMembers().size(), NamedTextColor.WHITE)));
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
            return subCommands.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("invite")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("create")) return Arrays.asList("<クラン名>");
        }
        return new ArrayList<>();
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== Clan System Help ===", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(Component.text("/clan create <name>", NamedTextColor.WHITE).append(Component.text(" - クランを作成", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan invite <player>", NamedTextColor.WHITE).append(Component.text(" - プレイヤーを招待", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan join", NamedTextColor.WHITE).append(Component.text(" - 招待を受ける", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan info", NamedTextColor.WHITE).append(Component.text(" - 所属クランの情報", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan leave", NamedTextColor.WHITE).append(Component.text(" - クランを脱退", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan disband", NamedTextColor.WHITE).append(Component.text(" - クランを解散（リーダー用）", NamedTextColor.GRAY)));
    }
}
