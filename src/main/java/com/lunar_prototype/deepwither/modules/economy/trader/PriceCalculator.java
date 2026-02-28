package com.lunar_prototype.deepwither.modules.economy.trader;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.StatMap;
import com.lunar_prototype.deepwither.StatType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;

/**
 * アイテムの売却価格を計算するサービス
 */
public class PriceCalculator {

    private static final String CUSTOM_ID_KEY = "custom_id";
    private static final Map<StatType, Double> FLAT_PRICE_MULTIPLIERS = new EnumMap<>(StatType.class);
    private static final Map<StatType, Double> PERCENT_PRICE_MULTIPLIERS = new EnumMap<>(StatType.class);

    static {
        FLAT_PRICE_MULTIPLIERS.put(StatType.ATTACK_DAMAGE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.DEFENSE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAX_HEALTH, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.PROJECTILE_DAMAGE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAGIC_DAMAGE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAGIC_RESIST, 40.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAGIC_PENETRATION, 50.0);

        PERCENT_PRICE_MULTIPLIERS.put(StatType.MAX_HEALTH, 100.0);
        PERCENT_PRICE_MULTIPLIERS.put(StatType.CRIT_DAMAGE, 75.0);
    }

    /**
     * Determines the sell price for the given item using the provided trader manager.
     *
     * @param item    the item to price; if the item has a custom identifier or stats, those are used in valuation
     * @param manager the trader manager used to resolve a configured fixed sell price for the item
     * @return the sell price for the item (uses the manager's configured fixed price when positive, otherwise a price computed from the item's stats); always zero or greater
     */
    public int calculateSellPrice(ItemStack item, TraderManager manager) {
        String itemId = getItemId(item);
        int fixedPrice = manager.getSellPrice(itemId);

        if (fixedPrice > 0) {
            return fixedPrice;
        }

        return calculatePriceByStats(item);
    }

    /**
     * Resolve an item's custom identifier stored under the PersistentDataContainer or fall back to the item's material name.
     *
     * @param item the ItemStack to inspect; may be null or AIR
     * @return the stored custom id if present, the item's material name if metadata or key is absent, or an empty string when `item` is null or AIR
     */
    public String getItemId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item.getType().name();
        
        NamespacedKey key = new NamespacedKey(Deepwither.getInstance(), CUSTOM_ID_KEY);
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        }
        return item.getType().name();
    }

    /**
     * Computes an item's sell price by aggregating its stat modifiers and applying configured multipliers.
     *
     * @param item the ItemStack whose stats are used to compute the price
     * @return the computed sell price as a non-negative integer (rounded to the nearest whole number)
     */
    private int calculatePriceByStats(ItemStack item) {
        final StatMap stats = StatManager.readStatsFromItem(item);
        double totalValue = 0;
        for (StatType type : StatType.values()) {
            double flatValue = stats.getFlat(type);
            double percentValue = stats.getPercent(type);
            if (flatValue > 0) totalValue += flatValue * FLAT_PRICE_MULTIPLIERS.getOrDefault(type, 0.0);
            if (percentValue > 0) totalValue += percentValue * PERCENT_PRICE_MULTIPLIERS.getOrDefault(type, 0.0);
        }
        return Math.max(0, (int) Math.round(totalValue));
    }
}
