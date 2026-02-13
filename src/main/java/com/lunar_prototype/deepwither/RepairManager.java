package com.lunar_prototype.deepwither;

import org.bukkit.ChatColor;
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

    // 定数
    private static final double MAX_WEAR_RATE = 100.0;
    private static final double WEAR_RATE_PER_REPAIR = 25.0; // 修理1回で25%損耗率が上昇すると仮定
    private static final double STAT_BOOST_MIN = 0.10; // 10%
    private static final double STAT_BOOST_MAX = 0.20; // 20%

    public RepairManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    /**
     * メインハンドのアイテムの修理を実行する。
     */
    public void repairItem(Player player, ItemStack itemToRepair) {

        ItemMeta meta = itemToRepair.getItemMeta();
        if (meta == null || !(meta instanceof Damageable damageable)) {
            player.sendMessage(ChatColor.RED + "修理できるアイテムではありません。");
            return;
        }

        int maxDurability = itemToRepair.getType().getMaxDurability();
        int currentDamage = damageable.getDamage();
        int remainingDurability = maxDurability - currentDamage;

        // 1. 修理条件チェック: 耐久値が1の壊れる寸前の状態でのみ修理可能

        // 修理可能となる閾値（30%）を計算
        double thresholdRatio = 0.30; // 30%
        int repairThreshold = (int) (maxDurability * thresholdRatio);

        // 現在の残り耐久率を計算（表示用）
        int currentPercent = (int) (((double) remainingDurability / maxDurability) * 100);

        // 1. 修理条件チェック: 残り耐久が閾値(30%)より多い場合は修理不可
        if (remainingDurability > repairThreshold) {
            player.sendMessage(ChatColor.YELLOW + "このアイテムはまだ修理できません。");
            player.sendMessage(ChatColor.YELLOW + "修理可能ライン: 30%以下 (現在: " + currentPercent + "%)");
            return;
        }

        // 2. コスト計算 (ここでは単純に最大耐久値に基づいて計算)
        int baseCost = maxDurability * 2;

        // 3. 損耗率の取得とコストへの反映
        StatMap statmap = StatManager.readStatsFromItem(itemToRepair);
        double currentWearRate = statmap.getFinal(StatType.WEAR);

        // 損耗率に応じたコスト増加 (例: 損耗率10%ごとにコスト+10%)
        double costMultiplier = 1.0 + (currentWearRate / 100.0);
        int finalCost = (int) Math.round(baseCost * costMultiplier);

        // 4. 残高チェック (Economyクラスは仮定)
        if (!Deepwither.getEconomy().has(player, finalCost)) {
            player.sendMessage(ChatColor.RED + "修理費用が不足しています！ 必要額: " + Deepwither.getEconomy().format(finalCost));
            return;
        }

        // 5. 取引実行
        Deepwither.getEconomy().withdrawPlayer(player, finalCost);

        // 6. 損耗率のチェックと修理の実行
        if (currentWearRate + WEAR_RATE_PER_REPAIR >= MAX_WEAR_RATE) {
            // --- ★ マスタリー昇格＆オーバーホール修理 ---
            performMasteryUpgrade(player, itemToRepair, meta, damageable);

        } else {
            // --- 通常修理 ---
            performStandardRepair(player, itemToRepair, meta, damageable, currentWearRate);
        }

        player.getInventory().setItemInMainHand(itemToRepair); // 変更を反映
    }

    // ----------------------------------------------------
    // --- ヘルパーメソッド ---
    // ----------------------------------------------------

    private void performStandardRepair(Player player, ItemStack item, ItemMeta meta, Damageable damageable, double currentWearRate) {
        // 耐久値を全快にする
        Damageable itemdmg = (Damageable) item.getItemMeta();
        itemdmg.setDamage(0);

        // 損耗率を更新
        double newWearRate = currentWearRate + WEAR_RATE_PER_REPAIR;
        StatMap statmap = StatManager.readStatsFromItem(item);
        statmap.setFlat(StatType.WEAR,newWearRate);

        PersistentDataContainer container = itemdmg.getPersistentDataContainer();

        for (StatType type : statmap.getAllTypes()) {
            // NamespacedKeyのインスタンス化 (ここでは Deepwither.java のインスタンスを使用)
            NamespacedKey flatKey = new NamespacedKey("rpgstats", type.name().toLowerCase() + "_flat");
            NamespacedKey percentKey = new NamespacedKey("rpgstats", type.name().toLowerCase() + "_percent");

            // PDCに新しいFlat値を書き込む
            container.set(flatKey, PersistentDataType.DOUBLE, statmap.getFlat(type));
            // PDCに新しいPercent値を書き込む
            container.set(percentKey, PersistentDataType.DOUBLE, statmap.getPercent(type));
        }

        itemdmg.setLore(LoreBuilder.updateExistingLore(item, statmap, newWearRate, (int) statmap.getFinal(StatType.MASTERY)));

        // 5. ロアの更新とメッセージ
        item.setItemMeta(itemdmg);

        player.sendMessage(ChatColor.GREEN + "武器を修理しました。 §7(費用: " + Deepwither.getEconomy().format(item.getType().getMaxDurability() * 2) + ")");
        player.sendMessage(ChatColor.YELLOW + "損耗率: " + String.format("%.0f", newWearRate) + "%");
    }

    private void performMasteryUpgrade(Player player, ItemStack item, ItemMeta meta, Damageable damageable) {
        // 1. 耐久値を全快にする
        Damageable itemdmg = (Damageable) item.getItemMeta();
        itemdmg.setDamage(0);
        item.setItemMeta(itemdmg);

        // 2. 損耗率をリセット
        StatMap statmap = StatManager.readStatsFromItem(item);
        statmap.setFlat(StatType.WEAR,0);

        // 3. マスタリーレベルを上げる
        statmap.setFlat(StatType.MASTERY,statmap.getFinal(StatType.MASTERY) + 1);
        double newMastery = statmap.getFinal(StatType.MASTERY) + 1;

        // 4. ステータスのランダムな強化
        // StatMap/ItemFactoryを通じてカスタムステータスを取得・強化するロジックが必要
        // ここでは仮にLoreにメッセージを追加することで表現
        double boost = STAT_BOOST_MIN + (STAT_BOOST_MAX - STAT_BOOST_MIN) * random.nextDouble();

        statmap.multiplyAll(1.0 + boost);

        PersistentDataContainer container = meta.getPersistentDataContainer();

        for (StatType type : statmap.getAllTypes()) {
            // NamespacedKeyのインスタンス化 (ここでは Deepwither.java のインスタンスを使用)
            NamespacedKey flatKey = new NamespacedKey("rpgstats", type.name().toLowerCase() + "_flat");
            NamespacedKey percentKey = new NamespacedKey("rpgstats", type.name().toLowerCase() + "_percent");

            // PDCに新しいFlat値を書き込む
            container.set(flatKey, PersistentDataType.DOUBLE, statmap.getFlat(type));
            // PDCに新しいPercent値を書き込む
            container.set(percentKey, PersistentDataType.DOUBLE, statmap.getPercent(type));
        }

        meta.setLore(LoreBuilder.updateExistingLore(item,statmap,0,Integer.parseInt(String.valueOf(statmap.getFinal(StatType.MASTERY)))));

        // 5. ロアの更新とメッセージ
        item.setItemMeta(meta);

        player.sendMessage(" ");
        player.sendMessage(ChatColor.GOLD + ChatColor.BOLD.toString() + "【オーバーホール完了！】");
        player.sendMessage(ChatColor.AQUA + "マスタリーレベルが " + newMastery + " に上昇しました！");
        player.sendMessage(ChatColor.GREEN + "武器の性能がランダムに " + String.format("%.1f", boost * 100) + "% 強化されました！");
        player.sendMessage(" ");
    }
}