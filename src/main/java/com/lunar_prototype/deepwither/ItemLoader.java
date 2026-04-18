package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.item.processor.ItemLoadContext;
import com.lunar_prototype.deepwither.item.processor.ItemProcessor;
import com.lunar_prototype.deepwither.item.processor.impl.*;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class ItemLoader {
    private static final Random random = new Random();
    public static final NamespacedKey RECOVERY_AMOUNT_KEY = new NamespacedKey(Deepwither.getInstance(), "recovery_amount");
    public static final NamespacedKey COOLDOWN_KEY = new NamespacedKey(Deepwither.getInstance(), "cooldown_seconds");
    public static final NamespacedKey SKILL_CHANCE_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_chance");
    public static final NamespacedKey SKILL_COOLDOWN_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_cooldown");
    public static final NamespacedKey SKILL_ID_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_skillid");
    public static final NamespacedKey CHARGE_ATTACK_KEY = new NamespacedKey(Deepwither.getInstance(), "charge_attack");
    public static final NamespacedKey SPECIAL_ACTION_KEY = new NamespacedKey(Deepwither.getInstance(), "special_action_type");
    public static final NamespacedKey IS_WAND = new NamespacedKey(Deepwither.getInstance(), "is_wand");
    public static final NamespacedKey WEAPON_EFFECT_KEY = new NamespacedKey(Deepwither.getInstance(), "weapon_effect");

    public enum QualityRank {
        COMMON("普通の"), UNCOMMON("§a良質な"), RARE("§b希少な"), LEGENDARY("§6伝説の");

        private final String displayName;

        QualityRank(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static QualityRank fromRatio(double ratio) {
            if (ratio >= 0.9) return LEGENDARY;
            if (ratio >= 0.6) return RARE;
            if (ratio >= 0.3) return UNCOMMON;
            return COMMON;
        }
    }

    public static Map<StatType, Double> generateRandomModifiers(String rarity) {
        Map<StatType, Double> modifiers = new HashMap<>();

        int maxModifiers = MAX_MODIFIERS_BY_RARITY.getOrDefault(rarity, 1);
        int modifiersToApply = random.nextInt(maxModifiers) + 1;

        List<ModifierDefinition> weightedModifiers = new ArrayList<>();
        for (ModifierDefinition def : MODIFIER_DEFINITIONS) {
            for (int j = 0; j < (int) (def.weight * 10); j++) {
                weightedModifiers.add(def);
            }
        }

        Set<StatType> appliedTypes = new HashSet<>();

        for (int m = 0; m < modifiersToApply; m++) {
            if (weightedModifiers.isEmpty()) break;

            ModifierDefinition selectedDef = weightedModifiers.get(random.nextInt(weightedModifiers.size()));

            if (appliedTypes.contains(selectedDef.type)) {
                weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                m--;
                continue;
            }

            double modifierValue = selectedDef.minFlat + random.nextDouble() * (selectedDef.maxFlat - selectedDef.minFlat);
            modifiers.put(selectedDef.type, modifierValue);

            appliedTypes.add(selectedDef.type);
            weightedModifiers.removeIf(def -> def.type == selectedDef.type);
            
            if (selectedDef.type == StatType.MAGIC_DAMAGE || selectedDef.type == StatType.MAGIC_BURST_BONUS || selectedDef.type == StatType.MAGIC_AOE_BONUS) {
                weightedModifiers.removeIf(def -> def.type == StatType.MAGIC_DAMAGE || def.type == StatType.MAGIC_BURST_BONUS || def.type == StatType.MAGIC_AOE_BONUS);
            }
        }

        return modifiers;
    }

    public static int generateRandomSocketCount(String rarity) {
        double chance = 0.2; 
        int max = 1;

        if (rarity.contains("アンコモン")) {
            chance = 0.3;
            max = 1;
        } else if (rarity.contains("レア")) {
            chance = 0.4;
            max = 2;
        } else if (rarity.contains("エピック")) {
            chance = 0.5;
            max = 2;
        } else if (rarity.contains("レジェンダリー")) {
            chance = 0.6;
            max = 3;
        }

        if (random.nextDouble() < chance) {
            return 1 + random.nextInt(max);
        }
        return 0;
    }

    public static class RandomStatTracker {
        private double maxTotal = 0;
        private double actualTotal = 0;

        Map<StatType, Double> weightMap = Map.of(
                StatType.CRIT_CHANCE, 0.5,  
                StatType.CRIT_DAMAGE, 0.3
        );

        public void add(StatType type, double base, double spread, double actual) {
            double weight = weightMap.getOrDefault(type, 1.0); 
            maxTotal += (base + spread) * weight;
            actualTotal += actual * weight;
        }

        public double getRatio() {
            if (maxTotal == 0) return 0;
            return actualTotal / maxTotal;
        }
    }

    public static final Map<String, Integer> MAX_MODIFIERS_BY_RARITY = Map.of(
            "&f&lコモン", 1,
            "&a&lアンコモン", 2,
            "&b&lレア", 3,
            "&d&lエピック", 4,
            "&6&lレジェンダリー", 6
    );

    public static final List<ModifierDefinition> MODIFIER_DEFINITIONS = List.of(
            new ModifierDefinition(StatType.ATTACK_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.DEFENSE, 1.0, 2.0, 15.0),
            new ModifierDefinition(StatType.CRIT_CHANCE, 0.5, 0.5, 2.5),
            new ModifierDefinition(StatType.CRIT_DAMAGE, 0.3, 5.0, 10.0),
            new ModifierDefinition(StatType.MAX_HEALTH, 1.0, 10.0, 20.0),
            new ModifierDefinition(StatType.MAGIC_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.MAGIC_RESIST, 1.0, 2.0, 5.0),
            new ModifierDefinition(StatType.PROJECTILE_DAMAGE, 1.0, 2.0, 10.0),
            new ModifierDefinition(StatType.MAGIC_BURST_BONUS, 1.0, 3.0, 10.0),
            new ModifierDefinition(StatType.MAGIC_AOE_BONUS, 1.0, 3.0, 10.0),
            new ModifierDefinition(StatType.ATTACK_SPEED,0.1,0.1,0.2),
            new ModifierDefinition(StatType.REACH,0.1,0.1,0.3),
            new ModifierDefinition(StatType.MAX_MANA,0.5,10,40),
            new ModifierDefinition(StatType.MOVE_SPEED,0.1,0.001,0.005),
            new ModifierDefinition(StatType.COOLDOWN_REDUCTION,0.2,2,5),
            new ModifierDefinition(StatType.HP_REGEN,0.1,1,3),
            new ModifierDefinition(StatType.MANA_REGEN,0.3,1,3)
    );

    public static class ModifierDefinition {
        public final StatType type;
        public final double weight;
        public final double minFlat;
        public final double maxFlat;

        public ModifierDefinition(StatType type, double weight, double minFlat, double maxFlat) {
            this.type = type;
            this.weight = weight;
            this.minFlat = minFlat;
            this.maxFlat = maxFlat;
        }
    }

    private static final List<ItemProcessor> PROCESSORS = List.of(
            new BaseMaterialProcessor(),
            new StatProcessor(),
            new ModifierProcessor(),
            new PdcProcessor(),
            new ComponentProcessor(),
            new LoreAndFinalizeProcessor()
    );

    public static ItemStack loadSingleItem(String id, ItemFactory factory, File itemFolder, @Nullable FabricationGrade grade) {
        File[] files = itemFolder.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (!file.getName().endsWith(".yml")) continue;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.contains(id)) {
                Map<String, ItemStack> map = loadItems(config, factory, grade);
                return map.get(id);
            }
        }
        return null;
    }

    public static ItemStack loadSingleItem(String id, ItemFactory factory, File itemFolder) {
        return loadSingleItem(id, factory, itemFolder, FabricationGrade.STANDARD);
    }

    public static Map<String, ItemStack> loadItems(YamlConfiguration config, ItemFactory factory, @Nullable FabricationGrade forceGrade) {
        Map<String, ItemStack> result = new HashMap<>();

        for (String key : config.getKeys(false)) {
            try {
                ItemLoadContext context = new ItemLoadContext(key, config, factory, forceGrade);

                for (ItemProcessor processor : PROCESSORS) {
                    if (!context.isValid()) break;
                    processor.process(context);
                }

                if (context.isValid() && context.getItem() != null) {
                    result.put(key, context.getItem());
                }

            } catch (Exception e) {
                System.err.println("Error loading item '" + key + "': " + e.getMessage());
                e.printStackTrace();
            }
        }
        return result;
    }

    public static Map<String, ItemStack> loadItems(YamlConfiguration config, ItemFactory factory) {
        return loadItems(config, factory, null);
    }
}
