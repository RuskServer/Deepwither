package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

@DependsOn({})
public class DropPreventionListener implements Listener, IManager {

    private final JavaPlugin plugin;

    public DropPreventionListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    private final HashMap<UUID, Long> lastDropTime = new HashMap<>();
    private static final long DROP_INTERVAL_MS = 500;

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (player.getOpenInventory().getTopInventory().getHolder() != null) {
            return;
        }

        long previousTime = lastDropTime.getOrDefault(playerId, 0L);
        long timeDifference = currentTime - previousTime;

        if (timeDifference < DROP_INTERVAL_MS) {
            player.sendMessage(Component.text("[アイテムドロップ]", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .append(Component.text(" アイテムをドロップしました。", NamedTextColor.WHITE)));
        } else {
            event.setCancelled(true);
            player.sendMessage(Component.text("アイテムをドロップするには、短時間で", NamedTextColor.RED)
                    .append(Component.text("Qキーを2回", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text("押してください。", NamedTextColor.RED)));
        }

        lastDropTime.put(playerId, currentTime);
    }
}
