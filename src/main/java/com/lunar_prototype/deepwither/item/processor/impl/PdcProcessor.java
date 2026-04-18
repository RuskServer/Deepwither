package com.lunar_prototype.deepwither.item.processor.impl;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.ItemLoader;
import com.lunar_prototype.deepwither.item.processor.ItemLoadContext;
import com.lunar_prototype.deepwither.item.processor.ItemProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PdcProcessor implements ItemProcessor {
    private static final NamespacedKey CUSTOM_ID_KEY = new NamespacedKey(Deepwither.getInstance(), "custom_id");
    private static final NamespacedKey SOCKETS_MAX_KEY = new NamespacedKey("deepwither", "sockets_max");
    private static final NamespacedKey IS_RUNE_KEY = new NamespacedKey("deepwither", "is_rune");
    private static final NamespacedKey RAID_BOSS_ID_KEY = new NamespacedKey(Deepwither.getInstance(), "raid_boss_id");

    @Override
    public void process(ItemLoadContext context) {
        if (!context.isValid()) return;

        String key = context.getKey();
        YamlConfiguration config = context.getConfig();
        ItemMeta meta = context.getMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // custom_id
        container.set(CUSTOM_ID_KEY, PersistentDataType.STRING, key);

        if (context.getSocketsMax() > 0) {
            container.set(SOCKETS_MAX_KEY, PersistentDataType.INTEGER, context.getSocketsMax());
        }
        if (config.getBoolean(key + ".is_rune", false)) {
            container.set(IS_RUNE_KEY, PersistentDataType.BYTE, (byte) 1);
        }

        String chargeType = config.getString(key + ".charge_type", null);
        if (chargeType != null) {
            container.set(ItemLoader.CHARGE_ATTACK_KEY, PersistentDataType.STRING, chargeType);
        }
        String companionType = config.getString(key + ".companion_type", null);
        if (companionType != null) {
            container.set(Deepwither.getInstance().getCompanionManager().COMPANION_ID_KEY, PersistentDataType.STRING, companionType);
        }
        String raidBossId = config.getString(key + ".raid_boss_id", null);
        if (raidBossId != null) {
            container.set(RAID_BOSS_ID_KEY, PersistentDataType.STRING, raidBossId);
        }
        String specialAction = config.getString(key + ".special_action");
        if (specialAction != null) {
            container.set(ItemLoader.SPECIAL_ACTION_KEY, PersistentDataType.STRING, specialAction.toUpperCase());
        }
        
        String itemType = context.getItemType();
        if ((itemType != null && itemType.contains("杖")) || config.getBoolean(key + ".is_wand")) {
            container.set(ItemLoader.IS_WAND, PersistentDataType.BOOLEAN, true);
        }
        
        String weaponEffect = config.getString(key + ".weapon_effect");
        if (weaponEffect != null) {
            container.set(ItemLoader.WEAPON_EFFECT_KEY, PersistentDataType.STRING, weaponEffect.toLowerCase());
        }
        String setPartner = config.getString(key + ".set_partner");
        if (setPartner != null) {
            container.set(ItemFactory.SET_PARTNER_KEY, PersistentDataType.STRING, setPartner);
        }

        double recoveryAmount = config.getDouble(key + ".recovery-amount", 0.0);
        int cooldownSeconds = config.getInt(key + ".cooldown-seconds", 0);
        if (recoveryAmount > 0.0) {
            container.set(ItemLoader.RECOVERY_AMOUNT_KEY, PersistentDataType.DOUBLE, recoveryAmount);
            if (cooldownSeconds > 0) {
                container.set(ItemLoader.COOLDOWN_KEY, PersistentDataType.INTEGER, cooldownSeconds);
            }
        }

        int recipeBookGrade = config.getInt(key + ".recipe_book_grade", -1);
        if (recipeBookGrade >= -1) {
            if (config.contains(key + ".recipe_book_grade")) {
                container.set(ItemFactory.RECIPE_BOOK_KEY, PersistentDataType.INTEGER, recipeBookGrade);
                String gradeName = (recipeBookGrade == 0) ? "全等級" : "等級 " + recipeBookGrade;
                
                // Add lore for recipe book right here as it doesn't need applyStatsToItem
                // Wait, it gets overwritten by applyStatsToItem? applyStatsToItem might replace lore, 
                // but actually applyStatsToItem appends or replaces specific lines.
                // We'll add this to the item lore now, but applyStatsToItem actually recreates lore.
                // Let's check if applyStatsToItem removes existing lore.
                // It's safer to just set it, as the original code set it.
                // Wait, the original code called `item = factory.applyStatsToItem(...)`
                // and then later did `metaBook = item.getItemMeta(); metaBook.lore().add(...)`
                // Oh! The original code did `item = factory.applyStatsToItem(...)` and THEN applied these PDCs.
                // Wait! Original code order:
                // 1. applyStatsToItem -> returns new ItemStack.
                // 2. then recoveryAmount, recipeBookGrade, on_hit, durability, customArmorAssetId, equipable, armortrim, can_destroy.
                // BUT in original code `item = factory.applyStatsToItem(...)` was called AFTER `item.setItemMeta(meta)`.
                // applyStatsToItem returns a new ItemStack or modifies the existing one.
                // Let's just do it here on the current meta. It is safe to add PDCs. Lore might need to be added later if `applyStatsToItem` clears it.
                // Actually `applyStatsToItem` reconstructs Lore. So adding Lore here might be lost if `applyStatsToItem` sets it entirely.
                // BUT original code did this AFTER applyStatsToItem.
                // Let's defer Lore additions to FinalizeProcessor or let PdcProcessor do it if we are sure it's kept.
                // Wait, I'll store recipeBookGrade to context so FinalizeProcessor can add the lore, or I just do it here. 
                // Actually I can just do PDC here, and let FinalizeProcessor do the book lore. 
            }
        }

        if (config.isConfigurationSection(key + ".on_hit")) {
            double chance = config.getDouble(key + ".on_hit.chance", 0.0);
            int cooldown = config.getInt(key + ".on_hit.cooldown", 0);
            String skillId = config.getString(key + ".on_hit.mythic_skill_id", null);

            if (skillId != null) {
                container.set(ItemLoader.SKILL_CHANCE_KEY, PersistentDataType.DOUBLE, chance);
                container.set(ItemLoader.SKILL_COOLDOWN_KEY, PersistentDataType.INTEGER, cooldown);
                container.set(ItemLoader.SKILL_ID_KEY, PersistentDataType.STRING, skillId);
            }
        }
    }
}
