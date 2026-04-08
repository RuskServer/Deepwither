package com.lunar_prototype.deepwither.core.listener;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;

public class EnvironmentListener implements Listener {

    public EnvironmentListener(Deepwither plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onVineSpread(BlockSpreadEvent event) {
        Material type = event.getSource().getType();
        // VINE系ブロックの拡散を防ぐ
        if (type == Material.VINE || type.name().contains("VINES")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onVineGrow(BlockGrowEvent event) {
        Material newType = event.getNewState().getType();
        // VINE系ブロックの成長(下垂など)を防ぐ
        if (newType == Material.VINE || newType.name().contains("VINES")) {
            event.setCancelled(true);
        }
    }
}
