package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

@DependsOn({ArtifactGUI.class, StatManager.class})
public class ArtifactGUIListener implements Listener, IManager {

    private ArtifactGUI artifactGUI;
    private IStatManager statManager;
    private final JavaPlugin plugin;

    public ArtifactGUIListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.artifactGUI = Deepwither.getInstance().getArtifactGUI();
        this.statManager = Deepwither.getInstance().getStatManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!PlainTextComponentSerializer.plainText().serialize(event.getView().title()).contains("アーティファクト")) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        int slot = event.getSlot();
        ItemStack cursorItem = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        if (clickedInventory.equals(event.getView().getTopInventory())) {
            boolean isArtifact = isArtifactSlot(slot);
            boolean isBackpack = (slot == ArtifactGUI.BACKPACK_SLOT);

            if (isArtifact || isBackpack) {
                if (cursorItem.getType() == Material.AIR && currentItem != null && currentItem.getType() != Material.AIR) {
                    if (currentItem.getType() == Material.CYAN_STAINED_GLASS_PANE ||
                            currentItem.getType() == Material.PURPLE_STAINED_GLASS_PANE) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("このプレースホルダーは持ち出せません。", NamedTextColor.RED));
                        return;
                    }
                    return;
                }

                if (cursorItem.getType() != Material.AIR) {
                    if (isArtifact && !isArtifact(cursorItem)) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("ここにはアーティファクトのみ装備できます。", NamedTextColor.RED));
                    } else if (isBackpack && !isBackpackItem(cursorItem)) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("ここには背中装備のみ装備できます。", NamedTextColor.RED));
                    }

                    if (currentItem != null && (currentItem.getType() == Material.CYAN_STAINED_GLASS_PANE ||
                            currentItem.getType() == Material.PURPLE_STAINED_GLASS_PANE)) {
                        event.setCancelled(true);
                        clickedInventory.setItem(slot, cursorItem);
                        player.setItemOnCursor(new ItemStack(Material.AIR));
                        player.updateInventory();
                    }
                }
            } else {
                event.setCancelled(true);
            }
        }

        if (clickedInventory.equals(player.getInventory())) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                player.sendMessage(Component.text("このGUIではシフトクリックは無効です。ドラッグして配置してください。", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (PlainTextComponentSerializer.plainText().serialize(event.getView().title()).contains("アーティファクト")) {
            Player player = (Player) event.getPlayer();
            Inventory guiInventory = event.getInventory();
            Deepwither plugin = Deepwither.getInstance();

            List<ItemStack> artifacts = new ArrayList<>();
            for (int slot : ArtifactGUI.ARTIFACT_SLOTS) {
                ItemStack item = guiInventory.getItem(slot);
                if (item != null && item.getType() != Material.CYAN_STAINED_GLASS_PANE) {
                    artifacts.add(item);
                }
            }

            ItemStack backpackItem = guiInventory.getItem(ArtifactGUI.BACKPACK_SLOT);
            ItemStack toSaveBackpack = null;

            if (backpackItem != null && backpackItem.getType() != Material.PURPLE_STAINED_GLASS_PANE) {
                toSaveBackpack = backpackItem;
                if (backpackItem.hasItemMeta() && backpackItem.getItemMeta().hasCustomModelData()) {
                    int model = backpackItem.getItemMeta().getCustomModelData();
                    plugin.getBackpackManager().equipBackpack(player, model);
                }
            } else {
                plugin.getBackpackManager().unequipBackpack(player);
            }

            plugin.getArtifactManager().savePlayerArtifacts(player, artifacts, toSaveBackpack);
            statManager.updatePlayerStats(player);
        }
    }

    private boolean isArtifactSlot(int slot) {
        for (int s : ArtifactGUI.ARTIFACT_SLOTS) {
            if (s == slot) return true;
        }
        return false;
    }

    private boolean isArtifact(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            for (Component line : meta.lore()) {
                if (PlainTextComponentSerializer.plainText().serialize(line).contains("アーティファクト")) return true;
            }
        }
        return false;
    }

    private boolean isBackpackItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        return item.getItemMeta().lore().stream().anyMatch(line -> PlainTextComponentSerializer.plainText().serialize(line).contains("背中装備"));
    }
}