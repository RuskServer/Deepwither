package com.lunar_prototype.deepwither.modules.mine;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineListener implements Listener {

    private static final int KNOCKBACK_RESTORE_DELAY_TICKS = 5 * 20;

    private final JavaPlugin plugin;
    private final MineService mineService;
    private final Map<UUID, BukkitTask> knockbackTasks = new ConcurrentHashMap<>();
    private final NamespacedKey kbResistBackup;

    public MineListener(JavaPlugin plugin, MineService mineService) {
        this.plugin = plugin;
        this.mineService = mineService;
        this.kbResistBackup = new NamespacedKey(plugin, "mining_kb_backup");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!mineService.isTrackedOre(event.getBlock().getType())) {
            return;
        }

        backupKnockbackResistance(event.getPlayer());
        boolean handled = mineService.handleMiningAttempt(event.getPlayer(), event.getBlock());
        if (handled) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!mineService.isTrackedOre(event.getBlock().getType())) {
            return;
        }

        mineService.releaseState(event.getBlock());
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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        restoreKnockbackResistance(event.getPlayer().getUniqueId(), event.getPlayer().getAttribute(Attribute.KNOCKBACK_RESISTANCE));
    }

    private void backupKnockbackResistance(org.bukkit.entity.Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (attribute == null) {
            return;
        }

        PersistentDataContainer container = player.getPersistentDataContainer();
        if (!knockbackTasks.containsKey(player.getUniqueId())) {
            container.set(kbResistBackup, PersistentDataType.DOUBLE, attribute.getBaseValue());
        }

        attribute.setBaseValue(1.0);

        BukkitTask previous = knockbackTasks.put(player.getUniqueId(), org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            restoreKnockbackResistance(player.getUniqueId(), player.getAttribute(Attribute.KNOCKBACK_RESISTANCE));
        }, KNOCKBACK_RESTORE_DELAY_TICKS));

        if (previous != null) {
            previous.cancel();
        }
    }

    private void restoreKnockbackResistance(UUID playerId, AttributeInstance attribute) {
        if (attribute == null) {
            BukkitTask task = knockbackTasks.remove(playerId);
            if (task != null) {
                task.cancel();
            }
            return;
        }

        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player == null) {
            BukkitTask task = knockbackTasks.remove(playerId);
            if (task != null) {
                task.cancel();
            }
            return;
        }

        PersistentDataContainer container = player.getPersistentDataContainer();
        if (container.has(kbResistBackup, PersistentDataType.DOUBLE)) {
            Double backup = container.get(kbResistBackup, PersistentDataType.DOUBLE);
            if (backup != null) {
                attribute.setBaseValue(backup);
            }
            container.remove(kbResistBackup);
        }

        BukkitTask task = knockbackTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
