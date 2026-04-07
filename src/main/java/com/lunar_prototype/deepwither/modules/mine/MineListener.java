package com.lunar_prototype.deepwither.modules.mine;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class MineListener implements Listener {

    private final MineService mineService;

    public MineListener(MineService mineService) {
        this.mineService = mineService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!mineService.isTrackedOre(event.getBlock().getType())) {
            return;
        }

        event.setCancelled(true);
        mineService.handleMiningAttempt(event.getPlayer(), event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (mineService.isTrackedOre(block.getType())) {
                mineService.releaseState(block);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (mineService.isTrackedOre(block.getType())) {
                mineService.releaseState(block);
            }
        }
    }
}
