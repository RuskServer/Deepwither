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
                String lookupKey = rarity;
                if (!lookupKey.startsWith("&")) {
                    if (lookupKey.equals("コモン")) lookupKey = "&f&lコモン";
                    else if (lookupKey.equals("アンコモン")) lookupKey = "&a&lアンコモン";
                    else if (lookupKey.equals("レア")) lookupKey = "&b&lレア";
                    else if (lookupKey.equals("エピック")) lookupKey = "&d&lエピック";
                    else if (lookupKey.equals("レジェンダリー")) lookupKey = "&6&lレジェンダリー";
                }

                int maxModifiers = ItemLoader.MAX_MODIFIERS_BY_RARITY.getOrDefault(lookupKey, 1);
                int modifiersToApply = random.nextInt(maxModifiers) + 1;
                List<ItemLoader.ModifierDefinition> weightedModifiers = new ArrayList<>();
                for (ItemLoader.ModifierDefinition def : ItemLoader.MODIFIER_DEFINITIONS) {
                    for (int j = 0; j < (int) (def.weight * 10); j++) {
                        weightedModifiers.add(def);
                    }
                }
                
                Set<StatType> appliedTypes = new HashSet<>();
                for (int m = 0; m < modifiersToApply; m++) {
                    if (weightedModifiers.isEmpty()) break;
                    ItemLoader.ModifierDefinition selectedDef = weightedModifiers.get(random.nextInt(weightedModifiers.size()));
                    if (appliedTypes.contains(selectedDef.type)) {
                        weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                        m--; 
                        continue;
                    }
                    
                    double modValue = selectedDef.minFlat + random.nextDouble() * (selectedDef.maxFlat - selectedDef.minFlat);
                    modifiers.put(selectedDef.type, modValue);
                    appliedTypes.add(selectedDef.type);
                    weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                    
                    if (selectedDef.type == StatType.MAGIC_DAMAGE || selectedDef.type == StatType.MAGIC_BURST_BONUS || selectedDef.type == StatType.MAGIC_AOE_BONUS) {
                        weightedModifiers.removeIf(def -> def.type == StatType.MAGIC_DAMAGE || def.type == StatType.MAGIC_BURST_BONUS || def.type == StatType.MAGIC_AOE_BONUS);
                    }
                }
                
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
