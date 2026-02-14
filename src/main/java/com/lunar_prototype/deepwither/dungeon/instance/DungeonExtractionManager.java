package com.lunar_prototype.deepwither.dungeon.instance;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.dungeon.instance.DungeonInstance;
import com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager;
import com.lunar_prototype.deepwither.dungeon.roguelike.RoguelikeBuffManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

@DependsOn({DungeonInstanceManager.class, RoguelikeBuffManager.class})
public class DungeonExtractionManager implements IManager {

    private final Deepwither plugin;
    private final Map<String, List<Location>> pendingExtractions = new HashMap<>();
    private final Map<String, List<Location>> activeExtractions = new HashMap<>();
    private final Map<String, BukkitTask> extractionTasks = new HashMap<>();
    private final Map<String, List<BukkitTask>> effectTasks = new HashMap<>();

    public DungeonExtractionManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        startDetectionTask();
    }

    @Override
    public void shutdown() {}

    public void registerExtractionTask(String instanceId, List<Location> spawnPoints) {
        plugin.getLogger().info("[DungeonExtractionManager] Registering task for instance: " + instanceId + " with " + spawnPoints.size() + " spawns.");

        List<Location> shuffled = new ArrayList<>(spawnPoints);
        Collections.shuffle(shuffled);

        pendingExtractions.put(instanceId, shuffled);
        activeExtractions.put(instanceId, new ArrayList<>());

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (DungeonInstanceManager.getInstance().getActiveInstances().get(instanceId) == null) {
                    plugin.getLogger().info("[DungeonExtractionManager] Instance " + instanceId + " not found or inactive. Cancelling task.");
                    pendingExtractions.remove(instanceId);
                    activeExtractions.remove(instanceId);
                    this.cancel();
                    return;
                }
                plugin.getLogger().info("[DungeonExtractionManager] Running extraction task for " + instanceId);
                activateNextPoint(instanceId);
            }
        }.runTaskTimer(plugin, 3600L, 3600L);

        extractionTasks.put(instanceId, task);
    }

    private void activateNextPoint(String instanceId) {
        List<Location> pending = pendingExtractions.get(instanceId);
        if (pending == null || pending.isEmpty()) {
            plugin.getLogger().warning("[DungeonExtractionManager] No pending extractions for " + instanceId);
            return;
        }

        Location loc = pending.remove(0);
        activeExtractions.get(instanceId).add(loc);

        World world = loc.getWorld();
        loc.getBlock().setType(Material.END_PORTAL_FRAME);

        world.strikeLightningEffect(loc);
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);

        Component message = Component.text("[!] 脱出地点が活性化しました！座標: ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text(loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(), NamedTextColor.WHITE));
        broadcastToInstance(instanceId, message);

        startParticleEffect(instanceId, loc);
    }

    private void startParticleEffect(String instanceId, Location loc) {
        BukkitTask effectTask = new BukkitRunnable() {
            double angle = 0;
            @Override
            public void run() {
                if (loc.getBlock().getType() != Material.END_PORTAL_FRAME) {
                    this.cancel();
                    return;
                }
                double x = Math.cos(angle) * 1.5;
                double z = Math.sin(angle) * 1.5;
                loc.getWorld().spawnParticle(Particle.WITCH, loc.clone().add(x, 1, z), 1, 0, 0, 0, 0);
                loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 2, 0), 1, 0.1, 0.5, 0.1, 0.05);
                angle += 0.2;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        effectTasks.computeIfAbsent(instanceId, k -> new ArrayList<>()).add(effectTask);
    }

    public void stopExtractionSystem(String instanceId) {
        if (extractionTasks.containsKey(instanceId)) {
            extractionTasks.get(instanceId).cancel();
            extractionTasks.remove(instanceId);
            plugin.getLogger().info("Extraction timer stopped for: " + instanceId);
        }
        if (effectTasks.containsKey(instanceId)) {
            for (BukkitTask effectTask : effectTasks.get(instanceId)) {
                effectTask.cancel();
            }
            effectTasks.remove(instanceId);
        }
        pendingExtractions.remove(instanceId);
        activeExtractions.remove(instanceId);
    }

    private void startDetectionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String instanceId = player.getWorld().getName();
                    List<Location> extractions = activeExtractions.get(instanceId);
                    if (extractions == null) continue;
                    for (Location loc : extractions) {
                        if (player.getLocation().distance(loc) < 2.0) {
                            performExtraction(player);
                            break;
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void performExtraction(Player player) {
        Title title = Title.title(
                Component.text("SUCCESS", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("ダンジョンから無事脱出した！", NamedTextColor.WHITE)
        );
        player.showTitle(title);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        DungeonInstanceManager.getInstance().leaveDungeon(player);
        Deepwither.getInstance().getRoguelikeBuffManager().clearBuffs(player);

        Bukkit.broadcast(Component.text("[Deepwither] ", NamedTextColor.GRAY)
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" がダンジョンから生還しました！", NamedTextColor.WHITE)));
    }

    private void broadcastToInstance(String instanceId, Component message) {
        DungeonInstance inst = DungeonInstanceManager.getInstance().getActiveInstances().get(instanceId);
        if (inst != null) {
            inst.getPlayers().forEach(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendMessage(message);
            });
        }
    }
}