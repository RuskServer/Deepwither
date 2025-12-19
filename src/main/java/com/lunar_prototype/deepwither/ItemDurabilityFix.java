package com.lunar_prototype.deepwither;

import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

public class ItemDurabilityFix implements Listener {

    /**
     * アイテムの耐久値がゼロになり破壊される瞬間だけをキャンセルします。
     * 通常の耐久値の減少はそのまま行われます。
     *
     * @param e プレイヤーがアイテムにダメージを与えたときに発生するイベント
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerItemDamage(PlayerItemDamageEvent e) {
        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();

        if (meta == null || !(meta instanceof Damageable damageable)) {
            return; // 耐久値を持たないアイテムは無視
        }

        int damageToApply = e.getDamage(); // 今回増えるダメージ量
        int currentDamage = damageable.getDamage(); // 現在の蓄積ダメージ量
        int maxDurability = item.getType().getMaxDurability(); // アイテムの最大耐久値（これを超えると壊れる）

        // 【修正】: 「現在のダメージ + 今回受けるダメージ」が最大耐久値以上になるかチェック
        if (currentDamage + damageToApply >= maxDurability) {

            // ★ 破壊されるためイベントをキャンセル
            e.setCancelled(true);

            // 【修正箇所】: 耐久値を「最大値 - 1」に設定する
            // setDamageは「受けたダメージ」を設定するため、大きい数字ほど壊れかけになります。
            damageable.setDamage(maxDurability - 1);

            item.setItemMeta(damageable);

            // 表示名を取得（名前がない場合はアイテムタイプ名を使用）
            String itemName = (meta.hasDisplayName()) ? meta.getDisplayName() : item.getType().name();

            // プレイヤーにメッセージを送信
            e.getPlayer().sendMessage("§cあなたの " + itemName + " §cの耐久値が限界に達しました！すぐに修理が必要です。");
        }
    }
}