package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({})
public class ItemDurabilityFix implements Listener, IManager {

    private final JavaPlugin plugin;

    public ItemDurabilityFix(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemDamage(PlayerItemDamageEvent e) {
        Player player = e.getPlayer();
        World world = player.getWorld();

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
            if (currentDamage + damageToApply > (maxDurability - 2)) {
                e.setCancelled(true);
                if (currentDamage < (maxDurability - 2)) {
                    damageable.setDamage(maxDurability - 2);
                    item.setItemMeta(damageable);
                    sendLimitNotification(player, meta, item, Component.text("(PvPvE保護) ", NamedTextColor.YELLOW).append(Component.text("耐久値が残り2で固定されました！修理が必要です。", NamedTextColor.RED)));
                }
            }
        } else {
            if (currentDamage + damageToApply >= maxDurability) {
                e.setCancelled(true);
                damageable.setDamage(maxDurability - 1);
                item.setItemMeta(damageable);
                sendLimitNotification(player, meta, item, Component.text("耐久値が限界です！修理してください。", NamedTextColor.RED));
            }
        }
    }

    private void sendLimitNotification(Player player, ItemMeta meta, ItemStack item, Component message) {
        Component displayName = meta.hasDisplayName() ? meta.displayName() : Component.text(item.getType().name());
        player.sendMessage(Component.text("⚠ ", NamedTextColor.RED).append(displayName).append(Component.text(" ")).append(message));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.6f, 0.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.2f);
    }
}
