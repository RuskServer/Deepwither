package com.lunar_prototype.deepwither.listeners;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({ItemFactory.class})
public class ArmorSetListener implements Listener, IManager {

    private ItemFactory itemFactory;
    private final JavaPlugin plugin;

    public ArmorSetListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.itemFactory = Deepwither.getInstance().getItemFactory();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = event.getNewItem();
        ItemStack oldItem = event.getOldItem();

        if (isSetItem(newItem)) {
            handleEquip(player, newItem);
        }

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

        if (isEquippingPartner(player, partnerId)) return;

        ItemStack partnerStack = findPartnerInInventory(player, partnerId);

        if (partnerStack != null) {
            equipPartner(player, partnerStack);
            player.sendMessage(Component.text("セット装備「", NamedTextColor.GREEN)
                    .append(Component.text(partnerId, NamedTextColor.WHITE))
                    .append(Component.text("」を自動で装着しました。", NamedTextColor.GREEN)));
        } else {
            Bukkit.getScheduler().runTask(Deepwither.getInstance(), () -> {
                unequipItem(player, equippedItem);
                player.sendMessage(Component.text("この装備には相方のアイテム(", NamedTextColor.RED)
                        .append(Component.text(partnerId, NamedTextColor.YELLOW))
                        .append(Component.text(")がインベントリに必要です。", NamedTextColor.RED)));
            });
        }
    }

    private void handleUnequip(Player player, ItemStack removedItem) {
        String partnerId = removedItem.getItemMeta().getPersistentDataContainer().get(ItemFactory.SET_PARTNER_KEY, PersistentDataType.STRING);
        if (partnerId == null) return;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (isSameCustomId(armor, partnerId)) {
                Bukkit.getScheduler().runTask(Deepwither.getInstance(), () -> {
                    unequipSpecificItem(player, armor);
                    player.sendMessage(Component.text("セット装備が解除されたため、相方のパーツも外しました。", NamedTextColor.GRAY));
                });
                break;
            }
        }
    }

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
        Material type = partner.getType();
        if (type.name().contains("CHESTPLATE")) player.getInventory().setChestplate(partner);
        else if (type.name().contains("LEGGINGS")) player.getInventory().setLeggings(partner);
        else if (type.name().contains("HELMET")) player.getInventory().setHelmet(partner);
        else if (type.name().contains("BOOTS")) player.getInventory().setBoots(partner);
        player.getInventory().removeItem(partner);
    }

    private void unequipItem(Player player, ItemStack item) {
        if (player.getInventory().getChestplate() != null && player.getInventory().getChestplate().equals(item)) player.getInventory().setChestplate(null);
        else if (player.getInventory().getLeggings() != null && player.getInventory().getLeggings().equals(item)) player.getInventory().setLeggings(null);
        player.getInventory().addItem(item);
    }

    private void unequipSpecificItem(Player player, ItemStack armorItem) {
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
