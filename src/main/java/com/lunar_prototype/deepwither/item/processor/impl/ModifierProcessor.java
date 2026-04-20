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
        int socketsMax = config.getInt(key + ".sockets_max", 0);

        if (!disableModifiers && context.isGear()) {
            if (random.nextDouble() < 0.03) {
                rarity = "&7&l刻印";
                socketsMax = 3 + random.nextInt(3); // 3-5個
            } else {
                Map<StatType, Double> generated = ItemLoader.generateRandomModifiers(rarity, context.getBaseStats());
                modifiers.putAll(generated);
                
                if (socketsMax == 0) {
                    socketsMax = ItemLoader.generateRandomSocketCount(rarity);
                }
            }
        }

        context.setRarity(rarity);
        context.setSocketsMax(socketsMax);

        boolean droppable = config.getBoolean(key + ".droppable", false);
        if (droppable) {
            context.getFactory().rarityPools.computeIfAbsent(rarity, k -> new ArrayList<>()).add(key);
        }
    }
}
