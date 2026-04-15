package com.lunar_prototype.deepwither;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class RepairManager {

    private final Deepwither plugin;

    public RepairManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    public void repairItem(Player player, ItemStack itemToRepair) {
        ItemMeta meta = itemToRepair.getItemMeta();
        if (meta == null || !(meta instanceof Damageable damageable)) {
            player.sendMessage(Component.text("修理できるアイテムではありません。", NamedTextColor.RED));
            return;
        }

        int maxDurability = itemToRepair.getType().getMaxDurability();
        int currentDamage = damageable.getDamage();
        int remainingDurability = maxDurability - currentDamage;

        // すでに全回復している場合
        if (currentDamage == 0) {
            player.sendMessage(Component.text("このアイテムはすでに修理されています。", NamedTextColor.YELLOW));
            return;
        }

        int finalCost = maxDurability * 2;

        if (!Deepwither.getEconomy().has(player, finalCost)) {
            player.sendMessage(Component.text("修理費用が不足しています！ 必要額: " + Deepwither.getEconomy().format(finalCost), NamedTextColor.RED));
            return;
        }

        Deepwither.getEconomy().withdrawPlayer(player, finalCost);

        damageable.setDamage(0);
        itemToRepair.setItemMeta(damageable);

        player.sendMessage(Component.text("武器を修理しました。 ", NamedTextColor.GREEN)
                .append(Component.text("(費用: " + Deepwither.getEconomy().format(finalCost) + ")", NamedTextColor.GRAY)));
    }
}
