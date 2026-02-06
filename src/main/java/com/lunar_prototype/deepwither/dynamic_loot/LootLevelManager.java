package com.lunar_prototype.deepwither.dynamic_loot;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({})
public class LootLevelManager implements Listener, IManager {
    private final NamespacedKey LOOT_LEVEL_KEY;
    private final int MAX_LEVEL = 3500;
    private final JavaPlugin plugin;

    public LootLevelManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.LOOT_LEVEL_KEY = new NamespacedKey(plugin, "loot_level");
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void addLootLevel(Player player, int amount) {
        //int current = Deepwither.getInstance().getLootLevelManager(player);
        //player.getPersistentDataContainer().set(LOOT_LEVEL_KEY, PersistentDataType.INTEGER, Math.min(MAX_LEVEL, current + amount));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        //int current = getLootLevel(player);
        // デスペナ: 10%減少など
        //player.getPersistentDataContainer().set(LOOT_LEVEL_KEY, PersistentDataType.INTEGER, (int)(current * 0.9));
        //player.sendMessage("§c死亡によりルートレベルが低下しました...");
    }
}
