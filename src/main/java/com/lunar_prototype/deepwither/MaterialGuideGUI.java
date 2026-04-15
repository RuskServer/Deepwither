package com.lunar_prototype.deepwither;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class MaterialGuideGUI implements Listener {

    private final Deepwither plugin;
    private final MaterialGuideManager guideManager;
    private static final int PAGE_SIZE = 45;

    public MaterialGuideGUI(Deepwither plugin, MaterialGuideManager guideManager) {
        this.plugin = plugin;
        this.guideManager = guideManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int page) {
        List<ItemStack> allMaterials = guideManager.getMaterialItems();
        int totalPages = (int) Math.ceil((double) allMaterials.size() / PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Inventory inv = Bukkit.createInventory(null, 54, Component.text("素材入手ガイド (" + (page + 1) + "/" + totalPages + ")", NamedTextColor.DARK_GRAY));

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allMaterials.size());

        for (int i = start; i < end; i++) {
            ItemStack display = allMaterials.get(i);
            inv.setItem(i - start, display);
        }

        // 下部ナビゲーション
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.displayName(Component.empty());
        glass.setItemMeta(glassMeta);
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);

        if (page > 0) {
            inv.setItem(48, createNavButton("前のページ", Material.ARROW, page - 1));
        }
        inv.setItem(49, createCloseButton());
        if (page < totalPages - 1) {
            inv.setItem(50, createNavButton("次のページ", Material.ARROW, page + 1));
        }
        inv.setItem(51, createBackButton());

        player.openInventory(inv);
    }

    private ItemStack createNavButton(String name, Material mat, int targetPage) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW, TextDecoration.BOLD));
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "gui_page"), PersistentDataType.INTEGER, targetPage);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("閉じる", NamedTextColor.RED, TextDecoration.BOLD));
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "gui_action"), PersistentDataType.STRING, "close");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("メインメニューへ", NamedTextColor.RED, TextDecoration.BOLD));
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "gui_action"), PersistentDataType.STRING, "menu");
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().title().toString().contains("素材入手ガイド")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            Player player = (Player) event.getWhoClicked();
            ItemMeta meta = clicked.getItemMeta();

            if (meta.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "gui_page"), PersistentDataType.INTEGER)) {
                int page = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "gui_page"), PersistentDataType.INTEGER);
                open(player, page);
            } else if (meta.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "gui_action"), PersistentDataType.STRING)) {
                String action = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "gui_action"), PersistentDataType.STRING);
                if ("menu".equals(action)) {
                    player.closeInventory();
                    plugin.getMenuGUI().open(player);
                } else {
                    player.closeInventory();
                }
            }
        }
    }
}
