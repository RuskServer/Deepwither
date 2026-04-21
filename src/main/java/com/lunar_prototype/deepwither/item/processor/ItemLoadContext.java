package com.lunar_prototype.deepwither.item.processor;

import com.lunar_prototype.deepwither.FabricationGrade;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.ItemLoader.RandomStatTracker;
import com.lunar_prototype.deepwither.StatMap;
import com.lunar_prototype.deepwither.StatType;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemLoadContext {
    private final String key;
    private final YamlConfiguration config;
    private final ItemFactory factory;
    /** 等級システム廃止のため常に STANDARD。 */
    private final FabricationGrade grade = FabricationGrade.STANDARD;

    private Material material;
    private ItemStack item;
    private ItemMeta meta;

    private RandomStatTracker tracker;
    private StatMap baseStats;
    private Map<StatType, Double> modifiers;
    
    private String rarity;
    private String itemType;
    private boolean isGear;
    private boolean valid = true;

    /** @deprecated grade 引数は等級システム廃止のため無視され、常に STANDARD を使用します。 */
    @Deprecated
    public ItemLoadContext(String key, YamlConfiguration config, ItemFactory factory, FabricationGrade grade) {
        this.key = key;
        this.config = config;
        this.factory = factory;
        this.tracker = new RandomStatTracker();
        this.baseStats = new StatMap();
        this.modifiers = new HashMap<>();
    }

    public String getKey() { return key; }
    public YamlConfiguration getConfig() { return config; }
    public ItemFactory getFactory() { return factory; }
    public FabricationGrade getGrade() { return grade; }

    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }

    public ItemStack getItem() { return item; }
    public void setItem(ItemStack item) { this.item = item; }

    public ItemMeta getMeta() { return meta; }
    public void setMeta(ItemMeta meta) { this.meta = meta; }

    public RandomStatTracker getTracker() { return tracker; }
    public StatMap getBaseStats() { return baseStats; }
    public Map<StatType, Double> getModifiers() { return modifiers; }

    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public boolean isGear() { return isGear; }
    public void setGear(boolean gear) { isGear = gear; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
}
