package com.lunar_prototype.deepwither.dungeon.roguelike;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.StatMap;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;

@DependsOn({})
public class RoguelikeBuffManager implements IManager, Listener {

    private final Deepwither plugin;
    private final Map<UUID, List<RoguelikeBuff>> playerBuffs = new HashMap<>();
    private final Map<UUID, Map<String, Long>> chestCooldowns = new HashMap<>();
    private static final long CHEST_COOLDOWN_MS = 180000;

    private final Set<UUID> selectingBuffPlayers = new HashSet<>();
    private BukkitTask particleTask;

    public RoguelikeBuffManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        startParticleTask();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
        if (particleTask != null && !particleTask.isCancelled()) {
            particleTask.cancel();
        }
        playerBuffs.clear();
    }

    public void addBuff(Player player, RoguelikeBuff buff) {
        playerBuffs.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(buff);
        recalculateAndApply(player);

        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
        player.sendMessage(Component.text("[Buff] ", NamedTextColor.GREEN)
                .append(buff.getDisplayName().colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text(" を獲得しました！", NamedTextColor.GRAY)));

        updateVisibility(player);
    }

    public int getBuffCount(Player player) {
        List<RoguelikeBuff> buffs = playerBuffs.get(player.getUniqueId());
        return buffs == null ? 0 : buffs.size();
    }

    public void clearBuffs(Player player) {
        if (playerBuffs.remove(player.getUniqueId()) != null) {
            plugin.getStatManager().removeTemporaryBuff(player.getUniqueId());
            plugin.getStatManager().updatePlayerStats(player);
        }
        chestCooldowns.remove(player.getUniqueId());
        selectingBuffPlayers.remove(player.getUniqueId());
        updateVisibility(player);

        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
    }

    public boolean tryUseChest(Player player, Location chestLoc) {
        String locKey = chestLoc.getBlockX() + "," + chestLoc.getBlockY() + "," + chestLoc.getBlockZ();
        Map<String, Long> userCooldowns = chestCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        if (userCooldowns.containsKey(locKey)) {
            long lastUsed = userCooldowns.get(locKey);
            long elapsed = System.currentTimeMillis() - lastUsed;
            if (elapsed < CHEST_COOLDOWN_MS) {
                long remainingSec = (CHEST_COOLDOWN_MS - elapsed) / 1000;
                player.sendMessage(Component.text("このチェストはあと " + remainingSec + "秒後に再使用可能です。", NamedTextColor.RED));
                return false;
            }
        }

        userCooldowns.put(locKey, System.currentTimeMillis());
        return true;
    }

    private void recalculateAndApply(Player player) {
        List<RoguelikeBuff> buffs = playerBuffs.get(player.getUniqueId());
        if (buffs == null || buffs.isEmpty()) {
            plugin.getStatManager().removeTemporaryBuff(player.getUniqueId());
        } else {
            StatMap totalBuffStats = new StatMap();
            for (RoguelikeBuff buff : buffs) {
                totalBuffStats.add(buff.getStatMap());
            }
            plugin.getStatManager().applyTemporaryBuff(player.getUniqueId(), totalBuffStats);
        }
        plugin.getStatManager().updatePlayerStats(player);
    }

    public void startBuffSelection(Player player) {
        selectingBuffPlayers.add(player.getUniqueId());
        updateVisibility(player);
    }

    public void endBuffSelection(Player player) {
        selectingBuffPlayers.remove(player.getUniqueId());
        updateVisibility(player);
    }

    public void updateVisibility(Player target) {
        boolean shouldBeHidden = selectingBuffPlayers.contains(target.getUniqueId()) || getBuffCount(target) == 0;

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(target.getUniqueId())) continue;
            if (shouldBeHidden) other.hidePlayer(plugin, target);
            else other.showPlayer(plugin, target);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player player) {
            if (selectingBuffPlayers.contains(player.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    private void startParticleTask() {
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : playerBuffs.keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline() || player.isDead()) continue;
                    List<RoguelikeBuff> buffs = playerBuffs.get(uuid);
                    if (buffs == null || buffs.isEmpty()) continue;
                    spawnBuffParticle(player, buffs.size());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 10L);
    }

    private void spawnBuffParticle(Player player, int count) {
        Location loc = player.getLocation().add(0, 1.0, 0);
        Color color = calculateColor(count);
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);
        try {
            player.getWorld().spawnParticle(Particle.DUST, loc, 5, 0.3, 0.5, 0.3, 0, dustOptions);
        } catch (Exception ignored) {}
    }

    private Color calculateColor(int count) {
        if (count <= 2) return Color.fromRGB(200, 200, 255);
        else if (count <= 5) return Color.fromRGB(100, 255, 100);
        else if (count <= 9) return Color.fromRGB(255, 255, 0);
        else return Color.fromRGB(255, 50, 50);
    }
}