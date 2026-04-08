package com.lunar_prototype.deepwither.modules.infrastructure;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

public class LegacyNPCCleanupListener implements Listener {

    private final Deepwither plugin;
    private final NamespacedKey questNpcKey;

    public LegacyNPCCleanupListener(Deepwither plugin) {
        this.plugin = plugin;
        this.questNpcKey = new NamespacedKey(plugin, "quest_npc");
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (entity.getPersistentDataContainer().has(questNpcKey, PersistentDataType.BYTE)) {
                entity.remove();
                plugin.getLogger().info("[Cleanup] Removed legacy quest NPC at " + entity.getLocation());
            }
        }
    }
}
