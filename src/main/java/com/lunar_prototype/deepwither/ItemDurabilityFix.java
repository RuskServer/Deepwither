package com.lunar_prototype.deepwither;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

public class ItemDurabilityFix implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemDamage(PlayerItemDamageEvent e) {
        Player player = e.getPlayer();
        World world = player.getWorld();

        // PvPvEダンジョンかどうかを判定 (World名の接頭辞でチェック)
        boolean isPvPvE = world.getName().startsWith("pvpve_");

        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();

        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        int maxDurability;
        if (damageable.hasMaxDamage()) {
            maxDurability = damageable.getMaxDamage();
        } else {
            maxDurability = item.getType().getMaxDurability();
        }

        if (maxDurability <= 0) {
            return;
        }

        int currentDamage = damageable.getDamage();
        int damageToApply = e.getDamage();

        if (isPvPvE) {
            // --- PvPvEダンジョンの特別仕様: 耐久値2で固定 ---
            // 次のダメージを適用すると残り耐久が2未満になる場合
            if (currentDamage + damageToApply > (maxDurability - 2)) {
                e.setCancelled(true); // ダメージイベント自体をキャンセルして「減らない」ようにする

                // まだ耐久が2より多い状態から一気に減る場合は、2に固定する処理を入れる
                if (currentDamage < (maxDurability - 2)) {
                    damageable.setDamage(maxDurability - 2);
                    item.setItemMeta(damageable);

                    // 初めて限界に達した時だけ通知
                    sendLimitNotification(player, meta, item, "§e(PvPvE保護) §c耐久値が残り2で固定されました！修理が必要です。");
                }
            }
        } else {
            // --- 通常の仕様: 耐久値1で止める (既存ロジック) ---
            if (currentDamage + damageToApply >= maxDurability) {
                e.setCancelled(true);
                damageable.setDamage(maxDurability - 1);
                item.setItemMeta(damageable);

                sendLimitNotification(player, meta, item, "§c耐久値が限界です！修理してください。");
            }
        }
    }

    /**
     * 通知と音の共通処理
     */
    private void sendLimitNotification(Player player, ItemMeta meta, ItemStack item, String message) {
        String displayName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
        player.sendMessage(ChatColor.RED + "⚠ " + displayName + " " + message);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.6f, 0.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.2f);
    }
}