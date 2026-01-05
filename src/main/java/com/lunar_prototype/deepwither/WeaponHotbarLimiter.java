package com.lunar_prototype.deepwither;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WeaponHotbarLimiter implements Listener {
    private static final int MAININV_START = 9;
    private static final int MAININV_END = 36;

    // 拾得時の制限
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;

        ItemStack item = e.getItem().getItemStack();
        // 変更: ItemStackを渡すように修正
        if (!isWeapon(item)) return;

        if (hasWeaponInHotbar(player)) {
            if (!hasSpaceInMainInventory(player.getInventory())) {
                e.setCancelled(true);
            }
        }
    }

    // インベントリ操作時の制限
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        InventoryType invType = e.getInventory().getType();
        if (invType != InventoryType.PLAYER && invType != InventoryType.CHEST && invType != InventoryType.CRAFTING) return;

        ItemStack clickedItem = e.getCurrentItem();
        ItemStack cursorItem = e.getCursor();
        int slot = e.getSlot();

        // 1. 数字キー (0-9) によるショートカット移動の制限
        if (e.getClick() == ClickType.NUMBER_KEY) {
            // 変更: clickedItemを直接渡して判定
            if (isWeapon(clickedItem)) {
                if (hasWeaponInHotbar(player) && !isSlotInHotbar(slot)) {
                    player.sendMessage("§cホットバーに武器は1つしか置けません。");
                    e.setCancelled(true);
                    return;
                }
            }
        }

        // 2. シフトクリックによる移動の制限
        if (e.isShiftClick() && isWeapon(clickedItem)) { // 変更: ItemStack判定
            if (!isSlotInHotbar(slot)) {
                if (hasWeaponInHotbar(player)) {
                    player.sendMessage("§cホットバーに武器は1つしか置けません。");
                    e.setCancelled(true);
                }
            }
            return;
        }

        // 3. 通常クリック（持ち上げ・配置）の制限
        if (isSlotInHotbar(slot) && e.getClickedInventory() instanceof PlayerInventory) {
            // カーソルに武器を持っている場合
            if (isWeapon(cursorItem)) { // 変更: ItemStack判定
                // すでにホットバーに武器があり、かつ置こうとしているスロットのアイテムが武器でない場合
                if (hasWeaponInHotbar(player) && !isWeapon(clickedItem)) { // 変更: ItemStack判定
                    player.sendMessage("§cホットバーに武器は1つしか置けません。");
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onCloseInventory(@NotNull InventoryCloseEvent inventoryCloseEvent) {
        if (!(inventoryCloseEvent.getPlayer() instanceof Player player)) return;

        var view = inventoryCloseEvent.getView();
        var itemStack = view.getCursor();
        if (itemStack.isEmpty() || !isWeapon(itemStack)) return;
        view.setCursor(ItemStack.empty());

        var inventory = player.getInventory();
        var firstEmpty = inventory.firstEmpty();
        var bl = hasWeaponInHotbar(player);

        if (isSlotInHotbar(firstEmpty) && !bl) {
            inventory.setItem(firstEmpty, itemStack);
            return;
        }

        var emptySlot = getSpaceInMainInventory(inventory);

        if (isSlotInMainInventory(emptySlot)) {
            inventory.setItem(emptySlot, itemStack);
        } else {
            var dropLocation = player.getLocation().add(0, .5, 0);
            player.getWorld().dropItem(dropLocation, itemStack).setPickupDelay(40);
        }

        if (bl) player.sendMessage("§cホットバーに武器は1つしか置けません。");

    }

    // --- 判定用ヘルパー ---

    /**
     * 武器かどうかを判定します。
     * STICKの場合はLoreに「カテゴリ:スクロール」が含まれていれば武器とみなさず false を返します。
     */
    private boolean isWeapon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        Material mat = item.getType();

        // 1. STICK（棒）の特例判定を最優先で行う
        if (mat == Material.STICK) {
            if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                List<String> lore = item.getItemMeta().getLore();
                for (String line : lore) {
                    // ★重要: 色コードを剥がしてから判定する
                    String plainLine = ChatColor.stripColor(line);

                    // 空白なども考慮して「カテゴリ:スクロール」が含まれているかチェック
                    if (plainLine.replace(" ", "").contains("カテゴリ:スクロール")) {
                        return false; // スクロールなので武器判定から除外
                    }
                }
            }
        }

        // 2. 通常の武器判定
        String name = mat.name();

        // 1.21+ のメイス対応
        if (mat.name().equals("MACE")) return true;

        return name.endsWith("_SWORD") ||
                name.endsWith("_AXE") ||
                name.endsWith("_BOW") ||
                name.endsWith("_CROSSBOW") ||
                name.endsWith("_TRIDENT") ||
                mat == Material.STICK ||    // 特例を抜けた棒は武器（杖など）扱い
                mat == Material.FEATHER;    // 羽を武器（扇など）として扱う場合
    }

    private boolean hasWeaponInHotbar(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            // 変更: ItemStackを渡す
            if (item != null && isWeapon(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSlotInHotbar(int slot) {
        return slot >= 0 && slot <= 8;
    }

    private boolean hasSpaceInMainInventory(PlayerInventory inv) {
        for (int i = 9; i <= 35; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) return true;
        }
        return false;
    }

    private static boolean isSlotInMainInventory(int slotId) {
        return slotId >= MAININV_START && slotId < MAININV_END;
    }

    private static int getSpaceInMainInventory(Inventory inventory) {
        for (var slotId = MAININV_START; slotId < MAININV_END; slotId++) {
            var itemstack = inventory.getItem(slotId);
            if (itemstack == null || itemstack.isEmpty()) return slotId;
        }

        return -1;
    }
}