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

        int damageToApply = e.getDamage(); // 今回減る予定の耐久値
        int currentDamage = damageable.getDamage(); // 現在の累積ダメージ量
        int maxDurability = item.getType().getMaxDurability(); // アイテムの最大耐久値

        // アイテムの残り耐久値を計算
        int remainingDurability = maxDurability - currentDamage;

        // 次のダメージで耐久値がゼロ以下になる（＝破壊される）かチェック
        if (damageToApply >= remainingDurability) {

            // ★ 耐久値がゼロになるのをキャンセル
            e.setCancelled(true);

            // 破壊をキャンセルした後、耐久値を強制的に 1 に設定して壊れない状態を維持する。
            // (オプション: ユーザーにアイテムが壊れる寸前であることをフィードバックする)

            damageable.setDamage(maxDurability - 1); // 耐久値を残り1の状態に設定
            item.setItemMeta(damageable);

            // プレイヤーにメッセージを送信
            e.getPlayer().sendMessage("§cあなたの " + item.getItemMeta().getDisplayName() + " §cの耐久値が限界に達しました！すぐに修理が必要です。");
        }

        // 耐久値が限界に達していない場合、イベントはキャンセルされず、通常の耐久値減少が行われます。
    }
}