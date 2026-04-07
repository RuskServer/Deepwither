package com.lunar_prototype.deepwither.modules.mine;

import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class MineMobSpawnListener implements Listener {

    private final MineService mineService;

    public MineMobSpawnListener(MineService mineService) {
        this.mineService = mineService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }

        switch (event.getSpawnReason()) {
            case NATURAL, PATROL, CHUNK_GEN, REINFORCEMENTS -> {
                if (mineService.shouldSuppressMobSpawns(event.getLocation())) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }
}
