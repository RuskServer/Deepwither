package com.lunar_prototype.deepwither.modules.rune;

import com.lunar_prototype.deepwither.Deepwither;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class RuneCommand implements CommandExecutor {

    private final RuneSocketGUI runeSocketGUI;
    private final RuneManager runeManager;

    public RuneCommand(RuneSocketGUI runeSocketGUI, RuneManager runeManager) {
        this.runeSocketGUI = runeSocketGUI;
        this.runeManager = runeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ実行可能です。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            runeSocketGUI.open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("set_sockets") && player.hasPermission("deepwither.admin")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("使用法: /rune set_sockets <個数>", NamedTextColor.RED));
                return true;
            }

            int count;
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("無効な数値です。", NamedTextColor.RED));
                return true;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                player.sendMessage(Component.text("アイテムを手に持ってください。", NamedTextColor.RED));
                return true;
            }

            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(RuneManager.SOCKETS_MAX_KEY, PersistentDataType.INTEGER, count);
            item.setItemMeta(meta);
            runeManager.updateItemStats(item);
            
            player.sendMessage(Component.text("アイテムに " + count + " 個のソケットを設定しました。", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("set_rune") && player.hasPermission("deepwither.admin")) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                player.sendMessage(Component.text("アイテムを手に持ってください。", NamedTextColor.RED));
                return true;
            }

            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(RuneManager.IS_RUNE_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
            
            player.sendMessage(Component.text("このアイテムをルーンとして設定しました。", NamedTextColor.GREEN));
            return true;
        }

        return false;
    }
}
