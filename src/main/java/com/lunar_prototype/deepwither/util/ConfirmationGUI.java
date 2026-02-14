package com.lunar_prototype.deepwither.util;

import com.lunar_prototype.deepwither.Deepwither;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 汎用的な確認GUIクラス。
 */
public class ConfirmationGUI implements Listener {

    private final JavaPlugin plugin;
    private final Player player;
    private final Component title;
    private final Consumer<Player> onConfirm;
    private final Consumer<Player> onCancel;
    private final Inventory inventory;

    private boolean responded = false;

    public ConfirmationGUI(JavaPlugin plugin, Player player, Component title, Consumer<Player> onConfirm, Consumer<Player> onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.title = title.decoration(TextDecoration.ITALIC, false);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.inventory = Bukkit.createInventory(null, 27, this.title);
        
        setupItems();
    }

    private void setupItems() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);

        ItemStack confirmItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.displayName(Component.text("はい (確定)", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        confirmItem.setItemMeta(confirmMeta);
        inventory.setItem(11, confirmItem);

        ItemStack cancelItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.displayName(Component.text("いいえ (キャンセル)", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        cancelItem.setItemMeta(cancelMeta);
        inventory.setItem(15, cancelItem);
    }

    public void open() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getInventory().equals(inventory)) return;
        e.setCancelled(true);

        if (responded) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.LIME_STAINED_GLASS_PANE) {
            responded = true;
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            if (onConfirm != null) onConfirm.accept(player);
        } else if (clicked.getType() == Material.RED_STAINED_GLASS_PANE) {
            responded = true;
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
            if (onCancel != null) onCancel.accept(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
            if (!responded && onCancel != null) {
                onCancel.accept(player);
            }
        }
    }
}
