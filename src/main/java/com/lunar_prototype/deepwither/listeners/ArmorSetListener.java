package com.lunar_prototype.deepwither.listeners;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class ArmorSetListener implements Listener {

    private final ItemFactory itemFactory;

    public ArmorSetListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = event.getNewItem();
        ItemStack oldItem = event.getOldItem();

        // 1. 装備した場合のロジック
        if (isSetItem(newItem)) {
            handleEquip(player, newItem);
        }

        // 2. 外した場合のロジック (共連れで外す)
        if (isSetItem(oldItem)) {
            handleUnequip(player, oldItem);
        }
    }

    private boolean isSetItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(ItemFactory.SET_PARTNER_KEY, PersistentDataType.STRING);
    }

    private void handleEquip(Player player, ItemStack equippedItem) {
        String partnerId = equippedItem.getItemMeta().getPersistentDataContainer().get(ItemFactory.SET_PARTNER_KEY, PersistentDataType.STRING);
        if (partnerId == null) return;

        // すでに相方を装備中か確認
        if (isEquippingPartner(player, partnerId)) return;

        // インベントリから相方を探す
        ItemStack partnerStack = findPartnerInInventory(player, partnerId);

        if (partnerStack != null) {
            // 相方が見つかった場合：自動装着
            equipPartner(player, partnerStack);
            player.sendMessage("§aセット装備「" + partnerId + "」を自動で装着しました。");
        } else {
            // 相方を持っていない場合：現在の装備を強制解除
            // 1チック後に実行しないとイベント内で矛盾が起きる場合がある
            Bukkit.getScheduler().runTask(Deepwither.getInstance(), () -> {
                unequipItem(player, equippedItem);
                player.sendMessage("§cこの装備には相方のアイテム(" + partnerId + ")がインベントリに必要です。");
            });
        }
    }

    private void handleUnequip(Player player, ItemStack removedItem) {
        String partnerId = removedItem.getItemMeta().getPersistentDataContainer().get(ItemFactory.SET_PARTNER_KEY, PersistentDataType.STRING);
        if (partnerId == null) return;

        // 相方を装備しているか探し、装備していれば外す
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (isSameCustomId(armor, partnerId)) {
                Bukkit.getScheduler().runTask(Deepwither.getInstance(), () -> {
                    unequipSpecificItem(player, armor);
                    player.sendMessage("§7セット装備が解除されたため、相方のパーツも外しました。");
                });
                break;
            }
        }
    }

    // --- ヘルパーメソッド群 ---

    private boolean isEquippingPartner(Player player, String partnerId) {
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (isSameCustomId(armor, partnerId)) return true;
        }
        return false;
    }

    private boolean isSameCustomId(ItemStack item, String targetId) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(Deepwither.getInstance(), "custom_id"), PersistentDataType.STRING);
        return targetId.equals(id);
    }

    private ItemStack findPartnerInInventory(Player player, String partnerId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isSameCustomId(item, partnerId)) return item;
        }
        return null;
    }

    private void equipPartner(Player player, ItemStack partner) {
        // アイテムの種類に応じてスロットを決定
        Material type = partner.getType();
        if (type.name().contains("CHESTPLATE")) player.getInventory().setChestplate(partner);
        else if (type.name().contains("LEGGINGS")) player.getInventory().setLeggings(partner);
        else if (type.name().contains("HELMET")) player.getInventory().setHelmet(partner);
        else if (type.name().contains("BOOTS")) player.getInventory().setBoots(partner);

        player.getInventory().removeItem(partner); // 元の場所から消す
    }

    private void unequipItem(Player player, ItemStack item) {
        // 指定されたアイテムをインベントリに戻し、装備スロットを空にする
        if (player.getInventory().getChestplate() != null && player.getInventory().getChestplate().equals(item)) player.getInventory().setChestplate(null);
        else if (player.getInventory().getLeggings() != null && player.getInventory().getLeggings().equals(item)) player.getInventory().setLeggings(null);
        // ...他部位も同様
        player.getInventory().addItem(item);
    }

    private void unequipSpecificItem(Player player, ItemStack armorItem) {
        // 防具スロットにある特定のアイテムを安全に外す
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && armor[i].equals(armorItem)) {
                ItemStack toReturn = armor[i].clone();
                armor[i] = null;
                player.getInventory().setArmorContents(armor);
                player.getInventory().addItem(toReturn);
                break;
            }
        }
    }
}