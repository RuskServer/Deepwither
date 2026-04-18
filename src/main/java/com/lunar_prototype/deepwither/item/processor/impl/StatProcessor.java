package com.lunar_prototype.deepwither.item.processor.impl;

import com.lunar_prototype.deepwither.ItemLoader;
import com.lunar_prototype.deepwither.StatMap;
import com.lunar_prototype.deepwither.StatType;
import com.lunar_prototype.deepwither.item.processor.ItemLoadContext;
import com.lunar_prototype.deepwither.item.processor.ItemProcessor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Random;

public class StatProcessor implements ItemProcessor {
    private static final Random random = new Random();

    @Override
    public void process(ItemLoadContext context) {
        if (!context.isValid()) return;

        String key = context.getKey();
        YamlConfiguration config = context.getConfig();
        ItemLoader.RandomStatTracker tracker = context.getTracker();
        StatMap baseStats = context.getBaseStats();

        if (config.isConfigurationSection(key + ".stats")) {
            for (String statKey : config.getConfigurationSection(key + ".stats").getKeys(false)) {
                StatType type = StatType.valueOf(statKey.toUpperCase());
                
                // flat値
                double flat;
                if (config.isConfigurationSection(key + ".stats." + statKey + ".flat")) {
                    double base = config.getDouble(key + ".stats." + statKey + ".flat.base", 0);
                    double spread = config.getDouble(key + ".stats." + statKey + ".flat.spread", 0);
                    flat = base + (spread > 0 ? random.nextDouble() * spread : 0);
                    tracker.add(type, base, spread, flat);
                } else {
                    flat = config.getDouble(key + ".stats." + statKey + ".flat", 0);
                    tracker.add(type, flat, 0, flat);
                }
                baseStats.setFlat(type, flat);

                // percent値
                double percent;
                if (config.isConfigurationSection(key + ".stats." + statKey + ".percent")) {
                    double base = config.getDouble(key + ".stats." + statKey + ".percent.base", 0);
                    double spread = config.getDouble(key + ".stats." + statKey + ".percent.spread", 0);
                    percent = base + (spread > 0 ? random.nextDouble() * spread : 0);
                    tracker.add(type, base, spread, percent);
                } else {
                    percent = config.getDouble(key + ".stats." + statKey + ".percent", 0);
                    tracker.add(type, percent, 0, percent);
                }
                baseStats.setPercent(type, percent);
            }
        }

        String itemType = config.getString(key + ".type", null);
        String normalizedType = (itemType != null) ? itemType.trim().toLowerCase() : "";
        boolean isGear = itemType != null
                && !normalizedType.contains("素材")
                && !normalizedType.contains("アーティファクト")
                && !normalizedType.contains("スクロール")
                && !normalizedType.contains("消費アイテム")
                && !normalizedType.contains("コンパニオン");

        context.setItemType(itemType);
        context.setGear(isGear);
    }
}
