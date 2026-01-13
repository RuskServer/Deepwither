package com.lunar_prototype.deepwither.dynamic_loot;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;

public class LootLevelManager implements Listener {
    private final NamespacedKey LOOT_LEVEL_KEY = new NamespacedKey(Deepwither.getInstance(), "loot_level");
    private final int MAX_LEVEL = 3500;

    public int getLootLevel(Player player) {
        return player.getPersistentDataContainer().getOrDefault(LOOT_LEVEL_KEY, PersistentDataType.INTEGER, 0);
    }

    public void addLootLevel(Player player, int amount) {
        int current = getLootLevel(player);
        player.getPersistentDataContainer().set(LOOT_LEVEL_KEY, PersistentDataType.INTEGER, Math.min(MAX_LEVEL, current + amount));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        int current = getLootLevel(player);
        // デスペナ: 10%減少など
        player.getPersistentDataContainer().set(LOOT_LEVEL_KEY, PersistentDataType.INTEGER, (int)(current * 0.9));
        player.sendMessage("§c死亡によりルートレベルが低下しました...");
    }
}
