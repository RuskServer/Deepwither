package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@DependsOn({ArtifactGUI.class, StatManager.class})
public class ArtifactGUIListener implements Listener, IManager {

    private ArtifactGUI artifactGUI;
    private IStatManager statManager;
    private final Deepwither plugin;

    public ArtifactGUIListener(Deepwither plugin) {
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
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.contains("Artifacts & Equipment")) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        int slot = event.getSlot();
        ItemStack cursorItem = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        if (clickedInventory.equals(event.getView().getTopInventory())) {
            if (slot == ArtifactGUI.MENU_SLOT) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                player.closeInventory();
                Deepwither.getInstance().getMenuGUI().open(player);
                return;
            }

            boolean isArtifact = isArtifactSlot(slot);
            boolean isBackpack = (slot == ArtifactGUI.BACKPACK_SLOT);

            if (isArtifact || isBackpack) {
                // プレースホルダーの持ち出し禁止
                if (cursorItem.getType() == Material.AIR && currentItem != null && currentItem.getType() != Material.AIR) {
                    if (currentItem.getType() == Material.CYAN_STAINED_GLASS_PANE ||
                            currentItem.getType() == Material.PURPLE_STAINED_GLASS_PANE) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("このプレースホルダーは持ち出せません。", NamedTextColor.RED));
                        return;
                    }
                    
                    // アイテムを取り出した場合、プレースホルダーを即座に復活させる
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        ItemStack itemAfterClick = clickedInventory.getItem(slot);
                        if (itemAfterClick == null || itemAfterClick.getType() == Material.AIR) {
                            if (isArtifact) {
                                clickedInventory.setItem(slot, ArtifactGUI.getArtifactPlaceholder());
                            } else if (isBackpack) {
                                clickedInventory.setItem(slot, ArtifactGUI.getBackpackPlaceholder());
                            }
                        }
                    });
                    return;
                }

                if (cursorItem.getType() != Material.AIR) {
                    if (isArtifact && !isArtifact(cursorItem)) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("ここにはアーティファクトのみ装備できます。", NamedTextColor.RED));
                        return;
                    } else if (isBackpack && !isBackpackItem(cursorItem)) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("ここには背中装備のみ装備できます。", NamedTextColor.RED));
                        return;
                    }

                    if (isArtifact && artifactWouldExceedTypeLimit(event, cursorItem)) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("同じタイプのアーティファクトは2つまでです。", NamedTextColor.RED));
                        return;
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
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains("Artifacts & Equipment")) {
            Player player = (Player) event.getPlayer();
            Inventory guiInventory = event.getInventory();

            List<ItemStack> artifacts = new ArrayList<>();
            List<ItemStack> rejectedItems = new ArrayList<>();
            List<ItemStack> selectedArtifacts = new ArrayList<>();
            for (int slot : ArtifactGUI.ARTIFACT_SLOTS) {
                ItemStack item = guiInventory.getItem(slot);
                if (item != null && item.getType() != Material.CYAN_STAINED_GLASS_PANE) {
                    if (!isArtifact(item)) {
                        rejectedItems.add(item.clone());
                    } else if (plugin.getArtifactManager().wouldExceedTypeLimit(selectedArtifacts, item, -1)) {
                        rejectedItems.add(item.clone());
                    } else {
                        selectedArtifacts.add(item.clone());
                    }
                }
            }
            artifacts.addAll(selectedArtifacts);

            ItemStack backpackItem = guiInventory.getItem(ArtifactGUI.BACKPACK_SLOT);
            ItemStack toSaveBackpack = null;

            if (backpackItem != null && backpackItem.getType() != Material.PURPLE_STAINED_GLASS_PANE) {
                if (isBackpackItem(backpackItem)) {
                    toSaveBackpack = backpackItem;
                    if (backpackItem.hasItemMeta() && backpackItem.getItemMeta().hasCustomModelData()) {
                        int model = backpackItem.getItemMeta().getCustomModelData();
                        plugin.getBackpackManager().equipBackpack(player, model);
                    }
                } else {
                    rejectedItems.add(backpackItem.clone());
                    plugin.getBackpackManager().unequipBackpack(player);
                }
            } else {
                plugin.getBackpackManager().unequipBackpack(player);
            }

            plugin.getArtifactManager().savePlayerArtifacts(player, artifacts, toSaveBackpack);
            returnRejectedArtifacts(player, rejectedItems);
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

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String fullsetType = pdc.get(ItemFactory.ARTIFACT_FULLSET_TYPE, PersistentDataType.STRING);
        if (fullsetType != null && !fullsetType.isBlank()) {
            return true;
        }

        String itemType = pdc.get(ItemFactory.ITEM_TYPE_KEY, PersistentDataType.STRING);
        if (itemType != null && itemType.contains("アーティファクト")) {
            return true;
        }

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

    private boolean artifactWouldExceedTypeLimit(InventoryClickEvent event, ItemStack candidate) {
        Inventory top = event.getView().getTopInventory();
        List<ItemStack> items = new ArrayList<>();
        int slot = event.getSlot();

        for (int artifactSlot : ArtifactGUI.ARTIFACT_SLOTS) {
            if (artifactSlot == slot) {
                continue;
            }
            ItemStack existing = top.getItem(artifactSlot);
            if (existing != null && existing.getType() != Material.CYAN_STAINED_GLASS_PANE) {
                items.add(existing);
            }
        }

        return plugin.getArtifactManager().wouldExceedTypeLimit(items, candidate, -1);
    }

    private void returnRejectedArtifacts(Player player, List<ItemStack> rejectedItems) {
        if (rejectedItems.isEmpty()) {
            return;
        }

        for (ItemStack rejected : rejectedItems) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(rejected);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }
        player.sendMessage(Component.text("配置できなかったアイテムをインベントリに戻しました。", NamedTextColor.YELLOW));
    }
}
