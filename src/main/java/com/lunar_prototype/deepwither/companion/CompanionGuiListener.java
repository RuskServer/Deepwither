package com.lunar_prototype.deepwither.companion;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

@DependsOn({CompanionManager.class})
public class CompanionGuiListener implements Listener, IManager {

    private final CompanionManager manager;
    private final CompanionGui gui;

    public CompanionGuiListener(CompanionManager manager) {
        this.manager = manager;
        this.gui = new CompanionGui(manager);
    }

    @Override
    public void init() throws Exception {
        Bukkit.getPluginManager().registerEvents(this, Deepwither.getInstance());
    }

    public void open(Player p) {
        gui.openGui(p);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());
        if (!title.equals(CompanionGui.GUI_TITLE)) return;

        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        Inventory inv = e.getInventory();

        if (slot >= inv.getSize()) {
            e.setCancelled(false);
            return;
        }

        if (slot == CompanionGui.SLOT_ITEM) {
            if (manager.isSpawned(p)) {
                p.sendMessage(Component.text("コンパニオン召喚中は装備を変更できません。", NamedTextColor.RED));
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1, 1);
                return;
            }
            e.setCancelled(false);
        }

        if (slot == CompanionGui.SLOT_ACTION) {
            ItemStack itemInSlot = inv.getItem(CompanionGui.SLOT_ITEM);

            if (manager.isSpawned(p)) {
                manager.despawnCompanion(p);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 0.5f);
                gui.updateActionButton(inv, p);
            } else {
                String companionId = manager.getCompanionIdFromItem(itemInSlot);
                if (companionId != null) {
                    manager.spawnCompanion(p, companionId);
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                    gui.updateActionButton(inv, p);
                } else {
                    p.sendMessage(Component.text("有効なコンパニオンアイテムがセットされていません。", NamedTextColor.RED));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());
        if (!title.equals(CompanionGui.GUI_TITLE)) return;

        Player p = (Player) e.getWhoClicked();
        if (e.getRawSlots().contains(CompanionGui.SLOT_ITEM)) {
            if (manager.isSpawned(p)) e.setCancelled(true);
        } else {
            boolean involvesGui = e.getRawSlots().stream().anyMatch(s -> s < e.getInventory().getSize());
            if (involvesGui) {
                if (!e.getRawSlots().contains(CompanionGui.SLOT_ITEM)) e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());
        if (!title.equals(CompanionGui.GUI_TITLE)) return;

        Player p = (Player) e.getPlayer();
        Inventory inv = e.getInventory();
        ItemStack item = inv.getItem(CompanionGui.SLOT_ITEM);
        manager.saveStoredItem(p.getUniqueId(), item);
    }
}
