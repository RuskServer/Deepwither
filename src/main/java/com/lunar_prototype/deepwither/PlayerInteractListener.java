package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({StatManager.class})
public class PlayerInteractListener implements Listener, IManager {

    private final JavaPlugin plugin;

    public PlayerInteractListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // アイテムが手に持たれていて、右クリックアクションであるかを確認
        if (item == null || !item.hasItemMeta() || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // 1. 回復アイテムであるかを確認し、値を取得
        if (container.has(ItemLoader.RECOVERY_AMOUNT_KEY, PersistentDataType.DOUBLE)) {
            event.setCancelled(true); // イベントをキャンセルして、デフォルトの動作（ブロックの配置など）を防ぐ

            double recoveryAmount = container.get(ItemLoader.RECOVERY_AMOUNT_KEY, PersistentDataType.DOUBLE);
            int cooldownSeconds = container.getOrDefault(ItemLoader.COOLDOWN_KEY, PersistentDataType.INTEGER, 0);

            // 2. クールダウンチェック
            if (cooldownSeconds > 0) {
                long currentTime = System.currentTimeMillis();
                long cooldownEndTime = cooldowns.getOrDefault(player.getUniqueId(), 0L);

                if (currentTime < cooldownEndTime) {
                    // クールダウン中の処理
                    long timeLeft = (cooldownEndTime - currentTime) / 1000;
                    player.sendMessage(ChatColor.RED + "このアイテムはクールダウン中です！残り: " + timeLeft + "秒");
                    return;
                }
            }

            // 3. 回復処理の実行
            Deepwither.getInstance().getStatManager().heal(player,recoveryAmount);

            // 回復エフェクト（任意）
            player.getWorld().spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0, 2, 0), 1, 0.5, 0.5, 0.5, 0);
            player.sendMessage(ChatColor.GREEN + "回復しました！ (" + recoveryAmount + ")");

            // 4. クールダウンのセット
            if (cooldownSeconds > 0) {
                long newCooldownEndTime = System.currentTimeMillis() + (cooldownSeconds * 1000L);
                cooldowns.put(player.getUniqueId(), newCooldownEndTime);
            }

            // 5. アイテムの消費（必要に応じて）
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                // 手からアイテムを削除する処理
                // メインハンドとオフハンドのどちらかを確認し、ItemStack.setAmount(0)を使用
                if (event.getHand() == EquipmentSlot.HAND) {
                    player.getInventory().setItemInMainHand(null);
                } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(null);
                }
            }
        }
    }
}
