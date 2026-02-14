package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({})
public class ChargeManager implements Listener, IManager {
    private final Map<UUID, Long> chargeStartTimes = new HashMap<>();
    private final Map<UUID, String> fullyChargedType = new HashMap<>();
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private final JavaPlugin plugin;

    public ChargeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
        for (BukkitTask task : activeTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        activeTasks.clear();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (e.isSneaking()) {
            if (activeTasks.containsKey(uuid)) {
                return;
            }

            ItemStack item = p.getInventory().getItemInMainHand();
            if (item == null || !item.hasItemMeta()) return;
            String type = item.getItemMeta().getPersistentDataContainer().get(ItemLoader.CHARGE_ATTACK_KEY, PersistentDataType.STRING);
            if (type == null) return;

            chargeStartTimes.put(uuid, System.currentTimeMillis());

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline() || !p.isSneaking()) {
                        stopCharge(uuid);
                        return;
                    }

                    if (!chargeStartTimes.containsKey(uuid)) {
                        stopCharge(uuid);
                        return;
                    }

                    long duration = System.currentTimeMillis() - chargeStartTimes.get(uuid);

                    if (duration < 2500 && (duration % 400) < 100) {
                        float pitch = 0.5f + ((float) duration / 2500f) * 0.7f;
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.6f, pitch);
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, pitch);
                    }

                    if (duration >= 2500) {
                        if (!fullyChargedType.containsKey(uuid)) {
                            fullyChargedType.put(uuid, type);
                            p.sendMessage(Component.text("★ 溜め完了！ ★", NamedTextColor.GOLD, TextDecoration.BOLD));
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.5f);
                        }
                        if (duration % 200 < 100) {
                            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 2.0f);
                        }
                        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.02);
                    } else {
                        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 1, 0.3, 0.3, 0.3, 0.01);
                    }
                }
            }.runTaskTimer(plugin, 0L, 2L);

            activeTasks.put(uuid, task);

        } else {
            stopCharge(uuid);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopCharge(e.getPlayer().getUniqueId());
    }

    private void stopCharge(UUID uuid) {
        if (activeTasks.containsKey(uuid)) {
            activeTasks.get(uuid).cancel();
            activeTasks.remove(uuid);
        }
        chargeStartTimes.remove(uuid);
        fullyChargedType.remove(uuid);
    }

    public String consumeCharge(UUID uuid) {
        stopCharge(uuid);
        return fullyChargedType.remove(uuid);
    }
}
