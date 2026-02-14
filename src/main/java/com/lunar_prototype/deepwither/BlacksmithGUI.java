package com.lunar_prototype.deepwither;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BlacksmithGUI {

    public static final Component GUI_TITLE = Component.text("鍛冶屋メニュー", NamedTextColor.DARK_GRAY);

    /**
     * 鍛冶屋GUIをプレイヤーに開く
     */
    public void openGUI(Player player) {
        // 9スロット * 3列 = 27スロットのチェストインベントリ
        Inventory gui = Bukkit.createInventory(player, 27, GUI_TITLE);

        // --- GUI アイテムの定義 ---

        // 1. 修理ボタン (スロット13: 中央)
        ItemStack repairItem = createGuiItem(Material.ANVIL, Component.text("武器修理", NamedTextColor.GREEN),
                Component.text("メインハンドの装備を修理します。", NamedTextColor.GRAY),
                Component.text("費用は損耗率に応じて変動します。", NamedTextColor.YELLOW)
        );
        gui.setItem(13, repairItem);

        // 2. 強化ボタン (スロット11)
        ItemStack upgradeItem = createGuiItem(Material.DIAMOND_PICKAXE, Component.text("装備強化", NamedTextColor.AQUA),
                Component.text("未実装: 装備のレベルを上げます。", NamedTextColor.GRAY)
        );
        gui.setItem(11, upgradeItem);

        // 3. クラフトボタン (スロット15)
        ItemStack craftItem = createGuiItem(Material.CRAFTING_TABLE, Component.text("アイテムクラフト", NamedTextColor.AQUA),
                Component.text("新しいアイテムを作成します。", NamedTextColor.GRAY)
        );
        gui.setItem(15, craftItem);

        // 4. 背景のガラス板（装飾）
        ItemStack background = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, background);
            }
        }

        player.openInventory(gui);
    }

    // アイテム作成ヘルパー
    private ItemStack createGuiItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));

            if (lore != null && lore.length > 0) {
                List<Component> nonItalicLore = new ArrayList<>();
                for (Component l : lore) {
                    nonItalicLore.add(l.decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(nonItalicLore);
            } else {
                meta.lore(new ArrayList<>());
            }

            item.setItemMeta(meta);
        }
        return item;
    }
}