package com.lunar_prototype.deepwither.companion;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class CompanionGui {

    private final CompanionManager manager;
    public static final String GUI_TITLE = "Companion Settings";
    public static final int SLOT_ITEM = 13; // 真ん中 (9x3の場合)
    public static final int SLOT_ACTION = 22; // 下段真ん中

    public CompanionGui(CompanionManager manager) {
        this.manager = manager;
    }

    public void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE).decoration(TextDecoration.ITALIC, false));

        // 1. 背景の装飾 (板ガラス)
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        fMeta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        filler.setItemMeta(fMeta);

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        // 2. 保存されているコンパニオンアイテムを配置
        ItemStack storedItem = manager.getStoredItem(player.getUniqueId());
        inv.setItem(SLOT_ITEM, storedItem); // nullなら空気(何もなし)になる

        // 3. アクションボタン (召喚/帰還)
        updateActionButton(inv, player);

        player.openInventory(inv);
    }

    public void updateActionButton(Inventory inv, Player player) {
        boolean isSpawned = manager.isSpawned(player);
        ItemStack button;

        if (isSpawned) {
            button = new ItemStack(Material.RED_DYE);
            ItemMeta meta = button.getItemMeta();
            meta.displayName(Component.text("[帰還させる]", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("クリックしてコンパニオンを戻す", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            button.setItemMeta(meta);
        } else {
            button = new ItemStack(Material.LIME_DYE);
            ItemMeta meta = button.getItemMeta();
            meta.displayName(Component.text("[召喚する]", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("クリックしてコンパニオンを呼ぶ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            button.setItemMeta(meta);
        }

        inv.setItem(SLOT_ACTION, button);
    }
}