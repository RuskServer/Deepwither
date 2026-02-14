package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.party.Party;
import com.lunar_prototype.deepwither.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PartyCommand implements CommandExecutor, TabCompleter {

    private final PartyManager partyManager;

    public PartyCommand(PartyManager partyManager) {
        this.partyManager = partyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ実行可能です。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "invite":
                handleInvite(player, args);
                break;
            case "accept":
                partyManager.acceptInvite(player);
                break;
            case "leave":
                partyManager.leaveParty(player);
                break;
            case "kick":
                handleKick(player, args);
                break;
            case "disband":
                partyManager.disbandParty(player);
                break;
            case "info":
                handleInfo(player);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("invite");
            subCommands.add("accept");
            subCommands.add("leave");
            subCommands.add("kick");
            subCommands.add("disband");
            subCommands.add("info");

            StringUtil.copyPartialMatches(args[0], subCommands, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("invite")) {
                List<String> playerNames = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getName().equals(player.getName())) {
                        playerNames.add(p.getName());
                    }
                }
                StringUtil.copyPartialMatches(args[1], playerNames, completions);

            } else if (subCommand.equals("kick")) {
                Party party = partyManager.getParty(player);
                if (party != null) {
                    List<String> memberNames = new ArrayList<>();
                    if (party.getLeaderId().equals(player.getUniqueId())) {
                        for (UUID memberId : party.getMemberIds()) {
                            if (!memberId.equals(player.getUniqueId())) {
                                Player member = Bukkit.getPlayer(memberId);
                                if (member != null) {
                                    memberNames.add(member.getName());
                                } else {
                                    String offlineName = Bukkit.getOfflinePlayer(memberId).getName();
                                    if (offlineName != null) memberNames.add(offlineName);
                                }
                            }
                        }
                    }
                    StringUtil.copyPartialMatches(args[1], memberNames, completions);
                }
            }

            Collections.sort(completions);
            return completions;
        }

        return Collections.emptyList();
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("使用法: /party invite <プレイヤー名>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("プレイヤーが見つかりません。", NamedTextColor.RED));
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(Component.text("自分自身は招待できません。", NamedTextColor.RED));
            return;
        }

        if (partyManager.getParty(target) != null) {
            player.sendMessage(Component.text("そのプレイヤーは既にパーティーに参加しています。", NamedTextColor.RED));
            return;
        }

        if (partyManager.getParty(player) == null) {
            partyManager.createParty(player);
        } else {
            Party party = partyManager.getParty(player);
            if (!party.getLeaderId().equals(player.getUniqueId())) {
                player.sendMessage(Component.text("招待権限がありません（リーダーのみ）。", NamedTextColor.RED));
                return;
            }
        }

        partyManager.invitePlayer(player, target);
        player.sendMessage(Component.text(target.getName() + " に招待を送りました。", NamedTextColor.GREEN));

        target.sendMessage(Component.text("==============================", NamedTextColor.GOLD));
        target.sendMessage(Component.text(player.getName(), NamedTextColor.AQUA).append(Component.text(" からパーティー招待が届きました！", NamedTextColor.AQUA)));

        Component message = Component.text("[ここをクリックして参加]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/party accept"));
        target.sendMessage(message);

        target.sendMessage(Component.text("(または /party accept と入力)", NamedTextColor.GRAY));
        target.sendMessage(Component.text("==============================", NamedTextColor.GOLD));
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("使用法: /party kick <プレイヤー名>", NamedTextColor.RED));
            return;
        }
        partyManager.kickMember(player, args[1]);
    }

    private void handleInfo(Player player) {
        Party party = partyManager.getParty(player);
        if (party == null) {
            player.sendMessage(Component.text("パーティーに参加していません。", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("=== パーティー情報 ===", NamedTextColor.BLUE));

        UUID leaderId = party.getLeaderId();
        Player leader = Bukkit.getPlayer(leaderId);
        String leaderName = (leader != null) ? leader.getName() : "Unknown";

        player.sendMessage(Component.text("リーダー: " + leaderName, NamedTextColor.GOLD));
        player.sendMessage(Component.text("メンバー (" + party.getMemberIds().size() + "名):", NamedTextColor.WHITE));

        for (UUID memberId : party.getMemberIds()) {
            Player p = Bukkit.getPlayer(memberId);
            Component nameComp;
            if (p != null && p.isOnline()) {
                 nameComp = Component.text(p.getName(), NamedTextColor.GREEN);
            } else {
                 nameComp = Component.text("(Offline)", NamedTextColor.GRAY);
            }
            player.sendMessage(Component.text(" - ").append(nameComp));
        }
        player.sendMessage(Component.text("==================", NamedTextColor.BLUE));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- パーティーコマンド ---", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/party invite <player>", NamedTextColor.AQUA).append(Component.text(" - プレイヤーを招待", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party accept", NamedTextColor.AQUA).append(Component.text(" - 招待を受ける", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party leave", NamedTextColor.AQUA).append(Component.text(" - パーティーから脱退", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party info", NamedTextColor.AQUA).append(Component.text(" - メンバーを表示", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party kick <player>", NamedTextColor.RED).append(Component.text(" - メンバーを追放(リーダーのみ)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party disband", NamedTextColor.RED).append(Component.text(" - パーティー解散(リーダーのみ)", NamedTextColor.GRAY)));
    }
}
