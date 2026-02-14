package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.FabricationGrade;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
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

    public static final Component DETAIL_PREFIX = Component.text("Confirm - ", NamedTextColor.DARK_GRAY);

    public void openDetail(Player player, CraftingRecipe recipe) {
        String itemName = recipe.getResultItemId();
        Inventory gui = Bukkit.createInventory(null, 27, DETAIL_PREFIX.append(Component.text(itemName, NamedTextColor.WHITE)));

        int slot = 10;
        for (Map.Entry<String, Integer> entry : recipe.getIngredients().entrySet()) {
            if (slot > 16) break;

            ItemStack ingredient = Deepwither.getInstance().getItemFactory().getCustomItemStack(entry.getKey(), FabricationGrade.STANDARD);
            if (ingredient == null) ingredient = new ItemStack(Material.BARRIER);

            ingredient.setAmount(entry.getValue());

            ItemMeta meta = ingredient.getItemMeta();
            List<Component> lore = meta.lore() != null ? meta.lore() : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("必要数: " + entry.getValue() + "個", NamedTextColor.YELLOW));
            meta.lore(lore);
            ingredient.setItemMeta(meta);

            gui.setItem(slot++, ingredient);
        }

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        gMeta.displayName(Component.text(" "));
        glass.setItemMeta(gMeta);
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, glass);
        }

        ItemStack confirm = new ItemStack(Material.ANVIL);
        ItemMeta cMeta = confirm.getItemMeta();
        cMeta.displayName(Component.text("製作を開始する", NamedTextColor.GREEN, TextDecoration.BOLD));
        cMeta.lore(List.of(Component.text("所要時間: " + recipe.getTimeSeconds() + "秒", NamedTextColor.GRAY)));
        cMeta.getPersistentDataContainer().set(CraftingGUI.RECIPE_KEY, PersistentDataType.STRING, recipe.getId());
        confirm.setItemMeta(cMeta);
        gui.setItem(22, confirm);

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.displayName(Component.text("戻る", NamedTextColor.RED));
        bMeta.getPersistentDataContainer().set(CraftingGUI.NAV_ACTION_KEY, PersistentDataType.STRING, "to_recipe");
        back.setItemMeta(bMeta);
        gui.setItem(18, back);

        player.openInventory(gui);
    }
}
