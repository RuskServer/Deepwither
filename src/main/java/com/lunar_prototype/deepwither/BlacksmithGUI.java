package com.lunar_prototype.deepwither;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class BlacksmithGUI {

    private static final String GUI_TITLE = ChatColor.DARK_GRAY + "鍛冶屋メニュー";

    /**
     * 鍛冶屋GUIをプレイヤーに開く
     */
    public void openGUI(Player player) {
        // 9スロット * 3列 = 27スロットのチェストインベントリ
        Inventory gui = Bukkit.createInventory(player, 27, GUI_TITLE);

        // --- GUI アイテムの定義 ---

        // 1. 修理ボタン (スロット13: 中央)
        ItemStack repairItem = createGuiItem(Material.ANVIL, ChatColor.GREEN + "武器修理",
                ChatColor.GRAY + "メインハンドの装備を修理します。",
                ChatColor.YELLOW + "費用は損耗率に応じて変動します。"
        );
        gui.setItem(13, repairItem);

        // 2. 強化ボタン (スロット11)
        ItemStack upgradeItem = createGuiItem(Material.DIAMOND_PICKAXE, ChatColor.AQUA + "装備強化",
                ChatColor.GRAY + "未実装: 装備のレベルを上げます。"
        );
        gui.setItem(11, upgradeItem);

        // 3. クラフトボタン (スロット15)
        ItemStack craftItem = createGuiItem(Material.CRAFTING_TABLE, ChatColor.AQUA + "アイテムクラフト",
                ChatColor.GRAY + "未実装: 新しいアイテムを作成します。"
        );
        gui.setItem(15, craftItem);

        // 4. 背景のガラス板（装飾）
        // BlacksmithGUI.java:43
        ItemStack background = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, background);
            }
        }

        player.openInventory(gui);
    }

    // アイテム作成ヘルパー
    // BlacksmithGUI.java:56
    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);

            // --- SAFE FIX: Check for null before using Arrays.asList ---
            if (lore != null) {
                meta.setLore(java.util.Arrays.asList(lore)); // <--- LINE 60
            } else {
                // Optionally set it to an empty list if null was passed
                meta.setLore(new java.util.ArrayList<>());
            }
            // ----------------------------------------------------------

            item.setItemMeta(meta);
        }
        return item;
    }
}