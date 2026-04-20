package com.lunar_prototype.deepwither.item.processor.impl;

import com.lunar_prototype.deepwither.ItemLoader;
import com.lunar_prototype.deepwither.StatType;
import com.lunar_prototype.deepwither.item.processor.ItemLoadContext;
import com.lunar_prototype.deepwither.item.processor.ItemProcessor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ModifierProcessor implements ItemProcessor {
    private static final Random random = new Random();

    @Override
    public void process(ItemLoadContext context) {
        if (!context.isValid()) return;

        String key = context.getKey();
        YamlConfiguration config = context.getConfig();

        boolean disableModifiers = config.getBoolean(key + ".disable_modifiers", false);
        Map<StatType, Double> modifiers = context.getModifiers();
        String rarity = config.getString(key + ".rarity", "コモン");

        if (!disableModifiers && context.isGear()) {
            Map<StatType, Double> generated = ItemLoader.generateRandomModifiers(rarity, context.getBaseStats());
            modifiers.putAll(generated);
        }

        context.setRarity(rarity);

        boolean droppable = config.getBoolean(key + ".droppable", false);
        if (droppable) {
            context.getFactory().rarityPools.computeIfAbsent(rarity, k -> new ArrayList<>()).add(key);
        }
    }
}
