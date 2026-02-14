package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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

        if (item == null || !item.hasItemMeta() || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        if (container.has(ItemLoader.RECOVERY_AMOUNT_KEY, PersistentDataType.DOUBLE)) {
            event.setCancelled(true);

            double recoveryAmount = container.get(ItemLoader.RECOVERY_AMOUNT_KEY, PersistentDataType.DOUBLE);
            int cooldownSeconds = container.getOrDefault(ItemLoader.COOLDOWN_KEY, PersistentDataType.INTEGER, 0);

            if (cooldownSeconds > 0) {
                long currentTime = System.currentTimeMillis();
                long cooldownEndTime = cooldowns.getOrDefault(player.getUniqueId(), 0L);

                if (currentTime < cooldownEndTime) {
                    long timeLeft = (cooldownEndTime - currentTime) / 1000;
                    player.sendMessage(Component.text("このアイテムはクールダウン中です！残り: " + timeLeft + "秒", NamedTextColor.RED));
                    return;
                }
            }

            Deepwither.getInstance().getStatManager().heal(player,recoveryAmount);
            player.getWorld().spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0, 2, 0), 1, 0.5, 0.5, 0.5, 0);
            player.sendMessage(Component.text("回復しました！ (" + recoveryAmount + ")", NamedTextColor.GREEN));

            if (cooldownSeconds > 0) {
                long newCooldownEndTime = System.currentTimeMillis() + (cooldownSeconds * 1000L);
                cooldowns.put(player.getUniqueId(), newCooldownEndTime);
            }

            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                if (event.getHand() == EquipmentSlot.HAND) {
                    player.getInventory().setItemInMainHand(null);
                } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(null);
                }
            }
        }
    }
}