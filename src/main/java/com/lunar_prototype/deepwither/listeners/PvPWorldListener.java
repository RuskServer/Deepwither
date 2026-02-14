package com.lunar_prototype.deepwither.listeners;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({})
public class PvPWorldListener implements Listener, IManager {

    private final String PVP_WORLD_NAME = "pvp";
    private final JavaPlugin plugin;

    public PvPWorldListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (isPvPWorldProtectionActive(player)) {
            e.setCancelled(true);
            player.sendMessage(Component.text("このワールドではブロックの破壊は禁止されています。", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (isPvPWorldProtectionActive(player)) {
            e.setCancelled(true);
            player.sendMessage(Component.text("このワールドではブロックの設置は禁止されています。", NamedTextColor.RED));
        }
    }

    private boolean isPvPWorldProtectionActive(Player player) {
        return player.getWorld().getName().equals(PVP_WORLD_NAME)
                && player.getGameMode() != GameMode.CREATIVE;
    }
}
