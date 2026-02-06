package com.lunar_prototype.deepwither.listeners;

import com.lunar_prototype.deepwither.FabricationGrade;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.StatType;
import com.lunar_prototype.deepwither.StatMap;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ItemUpgradeListener implements Listener {

    private final ItemFactory factory;

    public ItemUpgradeListener(ItemFactory factory) {
        this.factory = factory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 右クリックかつ、アイテムをカーソルで持っている状態
        if (event.getClick() != ClickType.RIGHT) return;

        ItemStack cursorItem = event.getCursor(); // 手に持っている強化アイテム
        ItemStack targetItem = event.getCurrentItem(); // 下にある対象アイテム

        if (cursorItem == null || targetItem == null || targetItem.getType() == Material.AIR) return;

        // カーソルアイテムが特殊アクションアイテムかチェック
        if (!cursorItem.hasItemMeta()) return;
        String actionType = cursorItem.getItemMeta().getPersistentDataContainer()
                .get(ItemFactory.SPECIAL_ACTION_KEY, PersistentDataType.STRING);

        if (actionType == null) return;

        // 対象アイテムがDeepwitherのカスタムアイテムか（IDを持っているか）チェック
        if (!targetItem.getItemMeta().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(com.lunar_prototype.deepwither.Deepwither.getInstance(), "custom_id"), PersistentDataType.STRING)) {
            return;
        }

        // イベントをキャンセルして持ち替えを防ぐ
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        double roll = ThreadLocalRandom.current().nextDouble() * 100;

        if (actionType.equalsIgnoreCase("GRADE_UP")) {
            handleGradeUpgrade(player, cursorItem, targetItem, roll);
        } else if (actionType.equalsIgnoreCase("REROLL")) {
            handleModifierReroll(player, cursorItem, targetItem, roll);
        }
    }

    private void handleGradeUpgrade(Player player, ItemStack cursor, ItemStack target, double roll) {
        if (roll <= 40.0) { // 40% 成功
            // 現在のグレードを取得
            // (ItemFactoryにグレード更新用のメソッドがあるとスムーズです)
            player.sendMessage("§a§lSUCCESS! §f製造グレードが向上しました！");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            factory.upgradeItemGrade(target);
        } else {
            // 失敗：破壊
            target.setAmount(0);
            player.sendMessage("§c§lFAILURE... §7強化に失敗し、アイテムは粉々に砕け散った。");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
        }
        consumeOne(cursor);
    }

    private void handleModifierReroll(Player player, ItemStack cursor, ItemStack target, double roll) {
        if (roll <= 60.0) { // 60% 成功
            player.sendMessage("§d§lSUCCESS! §f付加能力が再抽選されました。");
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
            factory.rerollModifiers(target);
        } else {
            // 失敗：破壊
            target.setAmount(0);
            player.sendMessage("§c§lFAILURE... §7魔力の暴走により、アイテムは消滅した。");
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
        }
        consumeOne(cursor);
    }

    private void consumeOne(ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            item.setAmount(0);
        }
    }
}