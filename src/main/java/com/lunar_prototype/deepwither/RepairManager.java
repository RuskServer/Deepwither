package com.lunar_prototype.deepwither;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

public class RepairManager {

    private final Deepwither plugin;
    private final Random random = new Random();

    private static final double MAX_WEAR_RATE = 100.0;
    private static final double WEAR_RATE_PER_REPAIR = 25.0; 
    private static final double STAT_BOOST_MIN = 0.10; 
    private static final double STAT_BOOST_MAX = 0.20; 

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

        double thresholdRatio = 0.30;
        int repairThreshold = (int) (maxDurability * thresholdRatio);
        int currentPercent = (int) (((double) remainingDurability / maxDurability) * 100);

        if (remainingDurability > repairThreshold) {
            player.sendMessage(Component.text("このアイテムはまだ修理できません。", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("修理可能ライン: 30%以下 (現在: " + currentPercent + "%)", NamedTextColor.YELLOW));
            return;
        }

        int baseCost = maxDurability * 2;
        StatMap statmap = StatManager.readStatsFromItem(itemToRepair);
        double currentWearRate = statmap.getFinal(StatType.WEAR);

        double costMultiplier = 1.0 + (currentWearRate / 100.0);
        int finalCost = (int) Math.round(baseCost * costMultiplier);

        if (!Deepwither.getEconomy().has(player, finalCost)) {
            player.sendMessage(Component.text("修理費用が不足しています！ 必要額: " + Deepwither.getEconomy().format(finalCost), NamedTextColor.RED));
            return;
        }

        Deepwither.getEconomy().withdrawPlayer(player, finalCost);

        if (currentWearRate + WEAR_RATE_PER_REPAIR >= MAX_WEAR_RATE) {
            performMasteryUpgrade(player, itemToRepair, meta, damageable);
        } else {
            performStandardRepair(player, itemToRepair, meta, damageable, currentWearRate);
        }

        player.getInventory().setItemInMainHand(itemToRepair);
    }

    private void performStandardRepair(Player player, ItemStack item, ItemMeta meta, Damageable damageable, double currentWearRate) {
        Damageable itemdmg = (Damageable) item.getItemMeta();
        itemdmg.setDamage(0);

        double newWearRate = currentWearRate + WEAR_RATE_PER_REPAIR;
        StatMap statmap = StatManager.readStatsFromItem(item);
        statmap.setFlat(StatType.WEAR,newWearRate);

        PersistentDataContainer container = itemdmg.getPersistentDataContainer();

        for (StatType type : statmap.getAllTypes()) {
            NamespacedKey flatKey = new NamespacedKey("rpgstats", type.name().toLowerCase() + "_flat");
            NamespacedKey percentKey = new NamespacedKey("rpgstats", type.name().toLowerCase() + "_percent");
            container.set(flatKey, PersistentDataType.DOUBLE, statmap.getFlat(type));
            container.set(percentKey, PersistentDataType.DOUBLE, statmap.getPercent(type));
        }

        itemdmg.lore(LoreBuilder.updateExistingLore(item, statmap, newWearRate, (int) statmap.getFinal(StatType.MASTERY)));
        item.setItemMeta(itemdmg);

        player.sendMessage(Component.text("武器を修理しました。 ", NamedTextColor.GREEN)
                .append(Component.text("(費用: " + Deepwither.getEconomy().format(item.getType().getMaxDurability() * 2) + ")", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("損耗率: " + String.format("%.0f", newWearRate) + "%", NamedTextColor.YELLOW));
    }

    private void performMasteryUpgrade(Player player, ItemStack item, ItemMeta meta, Damageable damageable) {
        Damageable itemdmg = (Damageable) item.getItemMeta();
        itemdmg.setDamage(0);
        item.setItemMeta(itemdmg);

        StatMap statmap = StatManager.readStatsFromItem(item);
        statmap.setFlat(StatType.WEAR,0);

        statmap.setFlat(StatType.MASTERY,statmap.getFinal(StatType.MASTERY) + 1);
        double newMastery = statmap.getFinal(StatType.MASTERY);

        double boost = STAT_BOOST_MIN + (STAT_BOOST_MAX - STAT_BOOST_MIN) * random.nextDouble();
        statmap.multiplyAll(1.0 + boost);

        PersistentDataContainer container = meta.getPersistentDataContainer();

        for (StatType type : statmap.getAllTypes()) {
            NamespacedKey flatKey = new NamespacedKey("rpgstats", type.name().toLowerCase() + "_flat");
            NamespacedKey percentKey = new NamespacedKey("rpgstats", type.name().toLowerCase() + "_percent");
            container.set(flatKey, PersistentDataType.DOUBLE, statmap.getFlat(type));
            container.set(percentKey, PersistentDataType.DOUBLE, statmap.getPercent(type));
        }

        meta.lore(LoreBuilder.updateExistingLore(item,statmap,0,(int) statmap.getFinal(StatType.MASTERY)));
        item.setItemMeta(meta);

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("【オーバーホール完了！】", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("マスタリーレベルが " + (int)newMastery + " に上昇しました！", NamedTextColor.AQUA));
        player.sendMessage(Component.text("武器の性能がランダムに " + String.format("%.1f", boost * 100) + "% 強化されました！", NamedTextColor.GREEN));
        player.sendMessage(Component.empty());
    }
}
