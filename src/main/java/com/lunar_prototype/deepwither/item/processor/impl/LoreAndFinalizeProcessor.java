package com.lunar_prototype.deepwither.item.processor.impl;

import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.item.processor.ItemLoadContext;
import com.lunar_prototype.deepwither.item.processor.ItemProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class LoreAndFinalizeProcessor implements ItemProcessor {

    @Override
    public void process(ItemLoadContext context) {
        if (!context.isValid()) return;

        String key = context.getKey();
        YamlConfiguration config = context.getConfig();
        ItemStack item = context.getItem();
        ItemMeta meta = context.getMeta();

        meta.setDisplayName(config.getString(key + ".name", key));

        if (config.getBoolean(key + ".unbreaking", false)) {
            meta.setUnbreakable(true);
        }

        item.setItemMeta(meta);

        List<String> flavorText = config.getStringList(key + ".flavor");
        String artifactFullsetType = config.getString(key + ".artifact_fullset_type", null);

        ItemFactory factory = context.getFactory();
        item = factory.applyStatsToItem(
                item,
                context.getBaseStats(),
                context.getModifiers(),
                context.getItemType(),
                flavorText,
                context.getTracker(),
                context.getRarity(),
                artifactFullsetType,
                context.getGrade()
        );

        int recipeBookGrade = config.getInt(key + ".recipe_book_grade", -1);
        if (recipeBookGrade >= -1) {
            if (config.contains(key + ".recipe_book_grade")) {
                ItemMeta metaBook = item.getItemMeta();
                if (metaBook != null) {
                    String gradeName = (recipeBookGrade == 0) ? "全等級" : "等級 " + recipeBookGrade;
                    List<Component> lore = metaBook.lore();
                    if (lore == null) {
                        lore = new java.util.ArrayList<>();
                    }
                    lore.add(Component.text("右クリックで使用: ", NamedTextColor.GOLD)
                            .append(Component.text("未習得の" + gradeName + "レシピを獲得", NamedTextColor.WHITE))
                            .decoration(TextDecoration.ITALIC, false));
                    metaBook.lore(lore);
                    item.setItemMeta(metaBook);
                }
            }
        }

        context.setItem(item);
    }
}
