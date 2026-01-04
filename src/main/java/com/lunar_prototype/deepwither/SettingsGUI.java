package com.lunar_prototype.deepwither;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsGUI implements Listener {

    private final Deepwither plugin;
    private final PlayerSettingsManager settingsManager;
    private static final String GUI_TITLE = "§8System Settings";

    public SettingsGUI(Deepwither plugin, PlayerSettingsManager settingsManager) {
        this.plugin = plugin;
        this.settingsManager = settingsManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // 背景
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // 各設定ボタンの配置
        inv.setItem(10, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, Material.IRON_SWORD));
        inv.setItem(12, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, Material.LEATHER_CHESTPLATE));
        inv.setItem(14, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION, Material.SHIELD));
        inv.setItem(16, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Material.ENCHANTED_BOOK));
        inv.setItem(18, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_PICKUP_LOG, Material.HOPPER));

        // 戻るボタン
        ItemStack back = createItem(Material.ARROW, "§c戻る", "§7メインメニューへ");
        inv.setItem(26, back);

        player.openInventory(inv);
    }

    private ItemStack createToggleItem(Player player, PlayerSettingsManager.SettingType type, Material mat) {
        boolean enabled = settingsManager.isEnabled(player, type);
        String status = enabled ? "§a[ON]" : "§c[OFF]";

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e" + type.getDisplayName() + " " + status);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7クリックして切り替え");
        lore.add("§7現在の状態: " + status);
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = e.getSlot();

        // 戻るボタン
        if (slot == 26) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            // MainMenuへ戻る (Mainクラスから取得するか、簡易的にコマンド実行)
            player.performCommand("menu");
            return;
        }

        PlayerSettingsManager.SettingType type = null;
        if (slot == 10) type = PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE;
        else if (slot == 12) type = PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE;
        else if (slot == 14) type = PlayerSettingsManager.SettingType.SHOW_MITIGATION;
        else if (slot == 16) type = PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG;
        else if (slot == 18) type = PlayerSettingsManager.SettingType.SHOW_PICKUP_LOG; // 追加

        if (type != null) {
            settingsManager.toggle(player, type);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            // GUI更新
            open(player);
        }
    }
}