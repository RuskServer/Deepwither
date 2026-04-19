package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.item.ISpecialItemEffect;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

@DependsOn({ItemFactory.class})
public class SpecialItemEffectManager implements IManager {
    private final Deepwither plugin;
    private final Map<String, ISpecialItemEffect> effects = new HashMap<>();
    private final NamespacedKey customIdKey;

    public SpecialItemEffectManager(Deepwither plugin) {
        this.plugin = plugin;
        this.customIdKey = new NamespacedKey(plugin, "custom_id");
    }

    @Override
    public void init() {
        registerEffects();
    }

    @Override
    public void shutdown() {
        effects.clear();
    }

    private void registerEffects() {
        // ここにハードコードで特殊効果を追加していく
        // 例: effects.put("legendary_fire_sword", new SomeFireSwordEffect());
    }

    public ISpecialItemEffect getEffect(String customId) {
        return effects.get(customId);
    }

    public ISpecialItemEffect getEffect(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(customIdKey, PersistentDataType.STRING);
        if (id == null) return null;
        return getEffect(id);
    }
}
