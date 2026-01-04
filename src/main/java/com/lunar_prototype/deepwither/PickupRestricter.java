package com.lunar_prototype.deepwither;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;

public class PickupRestricter implements Listener {

    private final PlayerSettingsManager settingsManager;

    public PickupRestricter(PlayerSettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;

        ItemStack pickedUp = e.getItem().getItemStack();
        int originalAmount = pickedUp.getAmount();
        PlayerInventory inv = player.getInventory();

        // 1. まずメインインベントリ (9-35) への収納を試みる
        int remaining = addToMainInventoryCustom(inv, pickedUp);
        int pickedAmount = originalAmount - remaining;

        // アイテムを拾えた場合、かつ設定がONの場合にメッセージを表示
        if (pickedAmount > 0) {
            sendPickupMessage(player, pickedUp, pickedAmount);
        }

        if (remaining <= 0) {
            // 全てメインインベントリに入った場合
            e.setCancelled(true);
            e.getItem().remove();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
            return;
        }

        // 一部、または全部がメインインベントリに入りきらなかった場合
        // pickedUpの数量を残量に更新
        pickedUp.setAmount(remaining);

        // 2. メインインベントリが満杯（または残量あり）の場合のホットバー判定
        if (hasSpaceInHotbar(inv)) {
            if (isWeapon(pickedUp.getType())) {
                // 武器はホットバーには絶対に入れない（残量を維持してキャンセル）
                e.setCancelled(true);
                // 拾えなかった分を戻す（数量が変わっている可能性があるため）
                e.getItem().setItemStack(pickedUp);
            } else {
                // 武器以外なら、残りの分はデフォルトの挙動（ホットバーへの収納）に任せる
                // ここではキャンセルせず、数量を更新した状態のアイテムをBukkitに処理させる
                e.getItem().setItemStack(pickedUp);
            }
        } else {
            // ホットバーも満杯ならキャンセル
            e.setCancelled(true);
            e.getItem().setItemStack(pickedUp);
        }
    }

    private void sendPickupMessage(Player player, ItemStack item, int amount) {
        // 設定がOFFなら何もしない
        if (!settingsManager.isEnabled(player, PlayerSettingsManager.SettingType.SHOW_PICKUP_LOG)) {
            return;
        }

        String itemName;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            itemName = item.getItemMeta().getDisplayName();
        } else {
            // 日本語化が必要な場合は別途翻訳マップが必要ですが、簡易的にはMaterial名
            itemName = "§f" + item.getType().name();
        }

        player.sendMessage("§7[+] " + itemName + " §fx" + amount);
    }

    /**
     * スロット9〜35のみを対象にアイテムを格納する
     * @return 収納しきれなかった残りの数量
     */
    private int addToMainInventoryCustom(PlayerInventory inv, ItemStack item) {
        int toAdd = item.getAmount();
        Material type = item.getType();

        // 1回目：既存のスタックを探して重ねる
        for (int i = 9; i <= 35; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot != null && slot.isSimilar(item)) {
                int space = slot.getMaxStackSize() - slot.getAmount();
                if (space > 0) {
                    int canAdd = Math.min(toAdd, space);
                    slot.setAmount(slot.getAmount() + canAdd);
                    toAdd -= canAdd;
                }
            }
            if (toAdd <= 0) return 0;
        }

        // 2回目：空きスロットを探して入れる
        for (int i = 9; i <= 35; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                int canAdd = Math.min(toAdd, type.getMaxStackSize());
                ItemStack newItem = item.clone();
                newItem.setAmount(canAdd);
                inv.setItem(i, newItem);
                toAdd -= canAdd;
            }
            if (toAdd <= 0) return 0;
        }

        return toAdd;
    }

    private boolean hasSpaceInHotbar(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) return true;
        }
        return false;
    }

    private boolean isWeapon(Material mat) {
        String name = mat.name();
        return name.endsWith("_SWORD") ||
                name.endsWith("_AXE") ||
                name.endsWith("_BOW") ||
                name.endsWith("_CROSSBOW") ||
                name.endsWith("_TRIDENT") ||
                name.endsWith("STICK") ||
                name.endsWith("FEATHER") ||
                mat == Material.MACE;
    }
}