package com.lunar_prototype.deepwither.modules.rune;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.StatMap;
import com.lunar_prototype.deepwither.StatType;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RuneManager implements IManager {

    private final Deepwither plugin;
    private final ItemFactory itemFactory;

    public static final NamespacedKey SOCKETS_MAX_KEY = new NamespacedKey("deepwither", "sockets_max");
    public static final NamespacedKey SOCKETS_FILLED_KEY = new NamespacedKey("deepwither", "sockets_filled");
    public static final String RUNE_KEY_PREFIX = "rune_";
    public static final NamespacedKey IS_RUNE_KEY = new NamespacedKey("deepwither", "is_rune");

    public RuneManager(Deepwither plugin, ItemFactory itemFactory) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
    }

    @Override
    public void init() {
        plugin.getLogger().info("RuneManager initialized.");
    }

    @Override
    public void shutdown() {
    }

    /**
     * Checks if an item can have runes.
     */
    public boolean isSocketable(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(SOCKETS_MAX_KEY, PersistentDataType.INTEGER);
    }

    /**
     * Gets the maximum number of sockets for an item.
     */
    public int getMaxSockets(ItemStack item) {
        if (!isSocketable(item)) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(SOCKETS_MAX_KEY, PersistentDataType.INTEGER, 0);
    }

    /**
     * Gets the number of filled sockets.
     */
    public int getFilledSockets(ItemStack item) {
        if (!isSocketable(item)) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(SOCKETS_FILLED_KEY, PersistentDataType.INTEGER, 0);
    }

    /**
     * Checks if an item is a rune.
     */
    public boolean isRune(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(IS_RUNE_KEY, PersistentDataType.BYTE);
    }

    /**
     * Attaches a rune to an item at the first available socket.
     */
    public boolean attachRune(ItemStack item, ItemStack runeItem) {
        if (!isSocketable(item) || !isRune(runeItem)) return false;

        int max = getMaxSockets(item);
        int filled = getFilledSockets(item);

        if (filled >= max) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Find first empty socket
        for (int i = 0; i < max; i++) {
            NamespacedKey runeKey = new NamespacedKey("deepwither", RUNE_KEY_PREFIX + i);
            if (!pdc.has(runeKey, PersistentDataType.STRING)) {
                String runeId = getRuneId(runeItem);
                pdc.set(runeKey, PersistentDataType.STRING, runeId);
                pdc.set(SOCKETS_FILLED_KEY, PersistentDataType.INTEGER, filled + 1);
                item.setItemMeta(meta);
                updateItemStats(item);
                return true;
            }
        }

        return false;
    }

    private String getRuneId(ItemStack runeItem) {
        // Assuming custom_id is used for identification
        return runeItem.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "custom_id"), PersistentDataType.STRING);
    }

    /**
     * Recalculates and updates the item's stats based on base stats, modifiers, and runes.
     */
    public void updateItemStats(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 1. Restore Base Stats
        StatMap totalStats = restoreBaseStats(pdc);
        
        // 2. Add Modifiers
        StatMap modifiers = restoreModifiers(pdc);
        totalStats.add(modifiers);

        // 3. Add Rune Stats
        int max = getMaxSockets(item);
        for (int i = 0; i < max; i++) {
            NamespacedKey runeKey = new NamespacedKey("deepwither", RUNE_KEY_PREFIX + i);
            String runeId = pdc.get(runeKey, PersistentDataType.STRING);
            if (runeId != null) {
                ItemStack runeSample = itemFactory.getItem(runeId);
                if (runeSample != null) {
                    StatMap runeStats = itemFactory.getStats(runeSample);
                    totalStats.add(runeStats);
                }
            }
        }

        // 4. Update PDC for StatManager to read
        for (StatType type : StatType.values()) {
            double flat = totalStats.getFlat(type);
            double percent = totalStats.getPercent(type);
            
            NamespacedKey flatKey = new NamespacedKey("rpgstats", type.name().toLowerCase() + "_flat");
            NamespacedKey percentKey = new NamespacedKey("rpgstats", type.name().toLowerCase() + "_percent");
            
            if (flat != 0) pdc.set(flatKey, PersistentDataType.DOUBLE, flat);
            else pdc.remove(flatKey);
            
            if (percent != 0) pdc.set(percentKey, PersistentDataType.DOUBLE, percent);
            else pdc.remove(percentKey);
        }

        item.setItemMeta(meta);
        
        // 5. Refresh Lore (Using ItemFactory's updateItem logic if possible, or manual refresh)
        // Since updateItem in ItemFactory is deprecated and uses internal logic, 
        // we might need a way to trigger a lore rebuild.
        itemFactory.updateGrade(item, null); // This usually triggers applyStatsToItem which rebuilds lore
    }

    private StatMap restoreBaseStats(PersistentDataContainer pdc) {
        StatMap stats = new StatMap();
        for (StatType type : StatType.values()) {
            Double flat = pdc.get(new NamespacedKey("rpgstats", "base." + type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE);
            Double percent = pdc.get(new NamespacedKey("rpgstats", "base." + type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE);
            if (flat != null) stats.setFlat(type, flat);
            if (percent != null) stats.setPercent(type, percent);
        }
        return stats;
    }

    private StatMap restoreModifiers(PersistentDataContainer pdc) {
        StatMap stats = new StatMap();
        for (StatType type : StatType.values()) {
            Double modVal = pdc.get(new NamespacedKey("rpgstats", "mod." + type.name().toLowerCase()), PersistentDataType.DOUBLE);
            if (modVal != null) {
                stats.setFlat(type, modVal);
            }
        }
        return stats;
    }
}
