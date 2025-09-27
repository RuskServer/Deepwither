package com.lunar_prototype.deepwither;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.inventory.InventoryType;

public class PlayerStatListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            StatManager.updatePlayerStats(e.getPlayer());
            double maxMana = StatManager.getTotalStatsFromEquipment(e.getPlayer()).getFlat(StatType.MAX_MANA);
            Deepwither.getInstance().getManaManager().get(e.getPlayer().getUniqueId()).setMaxMana(maxMana);
        }, 5L); // 少し遅延して実行（インベントリが安定してから）
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            StatManager.updatePlayerStats(e.getPlayer());
            double maxMana = StatManager.getTotalStatsFromEquipment(e.getPlayer()).getFlat(StatType.MAX_MANA);
            Deepwither.getInstance().getManaManager().get(e.getPlayer().getUniqueId()).setMaxMana(maxMana);
        }, 5L);
    }

    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            StatManager.updatePlayerStats(e.getPlayer());
            double maxMana = StatManager.getTotalStatsFromEquipment(e.getPlayer()).getFlat(StatType.MAX_MANA);
            Deepwither.getInstance().getManaManager().get(e.getPlayer().getUniqueId()).setMaxMana(maxMana);
        }, 3L);
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            StatManager.updatePlayerStats(e.getPlayer());
            double maxMana = StatManager.getTotalStatsFromEquipment(e.getPlayer()).getFlat(StatType.MAX_MANA);
            Deepwither.getInstance().getManaManager().get(e.getPlayer().getUniqueId()).setMaxMana(maxMana);
        }, 3L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        // 装備スロット or プレイヤーインベントリ操作
        if (e.getSlotType() == InventoryType.SlotType.ARMOR ||
                e.getClickedInventory() != null && e.getClickedInventory().getType() == InventoryType.PLAYER) {

            Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
                StatManager.updatePlayerStats(player);
                double maxMana = StatManager.getTotalStatsFromEquipment(player).getFlat(StatType.MAX_MANA);
                Deepwither.getInstance().getManaManager().get(player.getUniqueId()).setMaxMana(maxMana);
            }, 3L);
        }
    }
}
