package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.market.GlobalMarketManager;
import com.lunar_prototype.deepwither.market.MarketGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MarketCommand implements CommandExecutor, TabCompleter {

    private final Deepwither plugin;
    private final GlobalMarketManager marketManager;
    private final MarketGui marketGui;

    public MarketCommand(Deepwither plugin, GlobalMarketManager marketManager, MarketGui marketGui) {
        this.plugin = plugin;
        this.marketManager = marketManager;
        this.marketGui = marketGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行可能です。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            marketGui.openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "sell" -> handleSell(player, args);
            case "collect" -> handleCollect(player);
            case "help" -> sendHelp(player);
            default -> {
                player.sendMessage(Component.text("不明なサブコマンドです。 /market help で確認してください。", NamedTextColor.RED));
                marketGui.openMainMenu(player);
            }
        }

        return true;
    }

    private void handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("使用法: /market sell <価格>", NamedTextColor.RED));
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem.getType() == Material.AIR) {
            player.sendMessage(Component.text("メインハンドにアイテムを持っていません。", NamedTextColor.RED));
            return;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("価格は数値で入力してください。", NamedTextColor.RED));
            return;
        }

        if (price <= 0) {
            player.sendMessage(Component.text("価格は 0 より大きい必要があります。", NamedTextColor.RED));
            return;
        }

        marketManager.listItem(player, handItem, price);
        player.getInventory().setItemInMainHand(null);

        player.sendMessage(Component.text("[Market] ", NamedTextColor.GREEN)
                .append(Component.text("アイテムを ", NamedTextColor.WHITE))
                .append(Component.text(price + " G", NamedTextColor.GOLD))
                .append(Component.text(" で出品しました！", NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private void handleCollect(Player player) {
        marketManager.claimEarnings(player);
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("========== [ Global Market ] ==========", NamedTextColor.GOLD).decoration(TextDecoration.STRIKETHROUGH, false));
        player.sendMessage(Component.text("/market", NamedTextColor.YELLOW).append(Component.text(" - マーケットメニューを開く", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/market sell <価格>", NamedTextColor.YELLOW).append(Component.text(" - 手に持ったアイテムを出品する", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/market collect", NamedTextColor.YELLOW).append(Component.text(" - 売上金を回収する", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("=======================================", NamedTextColor.GOLD).decoration(TextDecoration.STRIKETHROUGH, false));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("sell");
            suggestions.add("collect");
            suggestions.add("help");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            suggestions.add("100");
            suggestions.add("500");
            suggestions.add("1000");
        }
        return suggestions;
    }
}
