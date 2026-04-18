package com.lunar_prototype.deepwither.item.processor.impl;

import com.lunar_prototype.deepwither.item.processor.ItemLoadContext;
import com.lunar_prototype.deepwither.item.processor.ItemProcessor;
import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.datacomponent.item.ItemArmorTrim;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ComponentProcessor implements ItemProcessor {

    @Override
    public void process(ItemLoadContext context) {
        if (!context.isValid()) return;

        String key = context.getKey();
        YamlConfiguration config = context.getConfig();
        ItemStack item = context.getItem();

        int durability = config.getInt(key + ".durability", 0);
        if (durability > 0) {
            item.setData(DataComponentTypes.MAX_DAMAGE, durability);
        }

        String customArmorAssetId = config.getString(key + ".custom_armor");
        if (customArmorAssetId != null) {
            EquipmentSlot slot = getSlotFromMaterial(context.getMaterial());
            if (slot != null) {
                NamespacedKey customArmorId = NamespacedKey.minecraft(customArmorAssetId);
                item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).assetId(customArmorId).build());
            } else {
                System.err.println("Custom Armor Asset IDが設定されていますが、Material (" + context.getMaterial().name() + ") は認識可能な防具/武器ではありません。");
            }
        }

        if (config.contains(key + ".equipable")) {
            ConfigurationSection equipSection = config.getConfigurationSection(key + ".equipable");
            if (equipSection != null) {
                String slotName = equipSection.getString("slot", "HEAD");
                EquipmentSlot slot = EquipmentSlot.valueOf(slotName.toUpperCase());

                Equippable equippable = Equippable.equippable(slot)
                        .assetId(equipSection.contains("model") ? NamespacedKey.fromString(equipSection.getString("model")) : null)
                        .dispensable(equipSection.getBoolean("dispensable", true))
                        .swappable(equipSection.getBoolean("swappable", true))
                        .cameraOverlay(equipSection.contains("overlay") ? NamespacedKey.fromString(equipSection.getString("overlay")) : null)
                        .build();

                item.setData(DataComponentTypes.EQUIPPABLE, equippable);
            }
        }

        String armortrim = config.getString(key + ".armortrim");
        String armortrimmaterial = config.getString(key + ".armortrimmaterial");
        if (armortrim != null && armortrimmaterial != null) {
            NamespacedKey trimKey = NamespacedKey.minecraft(armortrim);
            NamespacedKey materialKey = NamespacedKey.minecraft(armortrimmaterial);

            TrimPattern trimPattern = Bukkit.getRegistry(TrimPattern.class).get(trimKey);
            TrimMaterial trimMaterial = Bukkit.getRegistry(TrimMaterial.class).get(materialKey);

            if (trimPattern != null && trimMaterial != null) {
                ArmorTrim armorTrim = new ArmorTrim(trimMaterial, trimPattern);
                item.setData(DataComponentTypes.TRIM, ItemArmorTrim.itemArmorTrim(armorTrim));
            } else {
                System.err.println("Failed to get trim pattern or material for item: " + key);
            }
        }

        List<String> canBreakBlocks = config.getStringList(key + ".can_destroy");
        if (!canBreakBlocks.isEmpty()) {
            RegistryKey<BlockType> blockRegistryKey = RegistryKey.BLOCK;
            Registry<BlockType> blockRegistry = Bukkit.getRegistry(BlockType.class);
            List<TypedKey<BlockType>> typedBlockKeys = new ArrayList<>();

            for (String blockId : canBreakBlocks) {
                NamespacedKey blockKey;
                if (!blockId.contains(":")) {
                    blockKey = NamespacedKey.minecraft(blockId.toLowerCase());
                } else {
                    blockKey = NamespacedKey.fromString(blockId);
                }

                if (blockKey != null && blockRegistry.get(blockKey) != null) {
                    typedBlockKeys.add(TypedKey.create(blockRegistryKey, blockKey));
                } else {
                    System.err.println("無効なブロックID: " + blockId);
                }
            }

            if (!typedBlockKeys.isEmpty()) {
                RegistryKeySet<BlockType> blockKeySet = RegistrySet.keySet(blockRegistryKey, typedBlockKeys);
                BlockPredicate blockPredicate = BlockPredicate.predicate()
                        .blocks(blockKeySet)
                        .build();
                ItemAdventurePredicate canBreakPredicate = ItemAdventurePredicate.itemAdventurePredicate(List.of(blockPredicate));

                item.setData(DataComponentTypes.CAN_BREAK, canBreakPredicate);
            }
        }
    }

    private EquipmentSlot getSlotFromMaterial(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT);

        if (name.contains("helmet") || name.contains("skull") || name.contains("head")) return EquipmentSlot.HEAD;
        if (name.contains("chestplate")) return EquipmentSlot.CHEST;
        if (name.contains("leggings")) return EquipmentSlot.LEGS;
        if (name.contains("boots")) return EquipmentSlot.FEET;
        if (name.contains("sword") || name.contains("axe") || name.contains("pickaxe") || name.contains("hoe") || name.contains("shovel")) return EquipmentSlot.HAND;
        if (material == Material.SHIELD) return EquipmentSlot.OFF_HAND;

        return null;
    }
}
