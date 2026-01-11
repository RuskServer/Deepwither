package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.FabricationGrade;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RecipeDetailGUI {

    public static final String DETAIL_PREFIX = ChatColor.DARK_GRAY + "Confirm - ";

    public void openDetail(Player player, CraftingRecipe recipe) {
        // レシピの結果アイテム名を取得してタイトルに
        String itemName = recipe.getResultItemId();
        Inventory gui = Bukkit.createInventory(null, 27, DETAIL_PREFIX + itemName);

        // --- 中央に必要素材を並べる (スロット 10-16あたり) ---
        int slot = 10;
        for (Map.Entry<String, Integer> entry : recipe.getIngredients().entrySet()) {
            if (slot > 16) break; // 簡易的に最大7種類まで

            // 素材IDからItemStackを生成 (ItemFactory経由)
            ItemStack ingredient = Deepwither.getInstance().getItemFactory().getCustomItemStack(entry.getKey(), FabricationGrade.STANDARD);
            if (ingredient == null) ingredient = new ItemStack(Material.BARRIER);

            ingredient.setAmount(entry.getValue()); // 必要個数に設定

            ItemMeta meta = ingredient.getItemMeta();
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.YELLOW + "必要数: " + entry.getValue() + "個");
            meta.setLore(lore);
            ingredient.setItemMeta(meta);

            gui.setItem(slot++, ingredient);
        }

        // --- 装飾用ガラス ---
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        gMeta.setDisplayName(" ");
        glass.setItemMeta(gMeta);
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, glass);
        }

        // --- 製作開始ボタン (スロット 22) ---
        ItemStack confirm = new ItemStack(Material.ANVIL);
        ItemMeta cMeta = confirm.getItemMeta();
        cMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "製作を開始する");
        List<String> cLore = new ArrayList<>();
        cLore.add(ChatColor.GRAY + "所要時間: " + recipe.getTimeSeconds() + "秒");
        cMeta.setLore(cLore);
        // クリック時に判定するためレシピIDを保存
        cMeta.getPersistentDataContainer().set(CraftingGUI.RECIPE_KEY, PersistentDataType.STRING, recipe.getId());
        confirm.setItemMeta(cMeta);
        gui.setItem(22, confirm);

        // --- 戻るボタン (スロット 18) ---
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.setDisplayName(ChatColor.RED + "戻る");
        bMeta.getPersistentDataContainer().set(CraftingGUI.NAV_ACTION_KEY, PersistentDataType.STRING, "to_recipe");
        back.setItemMeta(bMeta);
        gui.setItem(18, back);

        player.openInventory(gui);
    }
}