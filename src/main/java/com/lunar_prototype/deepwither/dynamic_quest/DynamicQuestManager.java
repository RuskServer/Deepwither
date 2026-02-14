package com.lunar_prototype.deepwither.dynamic_quest;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SafeZoneListener;
import com.lunar_prototype.deepwither.dynamic_quest.dialogue.DialogueGenerator;
import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestDifficulty;
import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestPersona;
import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestType;
import com.lunar_prototype.deepwither.dynamic_quest.event.SupplyConvoyEvent;
import com.lunar_prototype.deepwither.dynamic_quest.npc.QuestNPC;
import com.lunar_prototype.deepwither.dynamic_quest.obj.DynamicQuest;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@DependsOn({SafeZoneListener.class})
public class DynamicQuestManager implements IManager, Listener {

    private final Deepwither plugin;
    private final DialogueGenerator dialogueGenerator;
    private final List<QuestNPC> activeNPCs = new ArrayList<>();
    private BukkitTask spawnTask;
    private final Random random = new Random();

    // Configuration
    private static final int NPCS_PER_REGION = 1; // Number of NPCs to try spawning per safezone
    private static final long REFRESH_INTERVAL_TICKS = 20L * 60 * 10; // 10 minutes

    public DynamicQuestManager(Deepwither plugin) {
        this.plugin = plugin;
        this.dialogueGenerator = new DialogueGenerator();
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startSpawnTask();
    }

    @Override
    public void shutdown() {
        stopSpawnTask();
        despawnAll();
    }

    public void forceSpawnAt(Location location) {
        spawnNPC(location);
    }

    public int getActiveNPCCount() {
        return activeNPCs.size();
    }

    public void reload() {
        refreshNPCs();
    }

    private void refreshNPCs() {
        despawnAll();
        
        // Find safe zones
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            plugin.getLogger().warning("WorldGuard not enabled, cannot spawn Dynamic Quest NPCs.");
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        
        // Iterate through worlds to find regions with "safezone"
        int regionCount = 0;
        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
            if (regionManager == null) continue;

            List<ProtectedRegion> safeRegions = regionManager.getRegions().values().stream()
                    .filter(r -> r.getId().toLowerCase().contains("safezone"))
                    .collect(Collectors.toList());

            if (safeRegions.isEmpty()) continue;
            regionCount += safeRegions.size();

            // Try to spawn NPCs in EACH found region
            for (ProtectedRegion region : safeRegions) {
                for (int i = 0; i < NPCS_PER_REGION; i++) {
                    Location spawnLoc = getRandomLocationInRegion(world, region);
                    if (spawnLoc != null) {
                        spawnNPC(spawnLoc);
                    }
                }
            }
        }
        plugin.getLogger().info("[DynamicQuest] Spawning cycle complete. Found " + regionCount + " safezone regions. Spawned " + activeNPCs.size() + " NPCs.");
    }

    private void startSpawnTask() {
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshNPCs, 100L, REFRESH_INTERVAL_TICKS);
    }

    private void stopSpawnTask() {
        if (spawnTask != null && !spawnTask.isCancelled()) {
            spawnTask.cancel();
        }
    }

    private Location getRandomLocationInRegion(World world, ProtectedRegion region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        for (int i = 0; i < 10; i++) { // Try 10 times to find a valid spot
            int x = ThreadLocalRandom.current().nextInt(min.getBlockX(), max.getBlockX() + 1);
            int z = ThreadLocalRandom.current().nextInt(min.getBlockZ(), max.getBlockZ() + 1);
            int y = world.getHighestBlockYAt(x, z);

            if (y >= min.getBlockY() && y <= max.getBlockY()) {
                Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                // Check if block below is solid and block at/above is air
                if (loc.getBlock().getType().isAir() && loc.clone().add(0, 1, 0).getBlock().getType().isAir()
                        && loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                    return loc;
                }
            }
        }
        return null;
    }

    private void spawnNPC(Location location) {
        QuestPersona persona = QuestPersona.values()[random.nextInt(QuestPersona.values().length)];
        QuestType type = QuestType.values()[random.nextInt(QuestType.values().length)];
        // Determine difficulty based on some logic, random for now
        QuestDifficulty difficulty = type.getDefaultDifficulty(); 

        // Generate Target Location (Placeholder logic: 100-500 blocks away)
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = 100 + random.nextDouble() * 400;
        int dx = (int) (Math.cos(angle) * distance);
        int dz = (int) (Math.sin(angle) * distance);
        Location target = location.clone().add(dx, 0, dz);
        target.setY(target.getWorld().getHighestBlockYAt(target.getBlockX(), target.getBlockZ()) + 1);

        String text = dialogueGenerator.generate(persona, type, target);
        
        // Calculate reward
        double baseReward = 100; // Base credits
        double reward = baseReward * difficulty.getRewardMultiplier();

        DynamicQuest quest = new DynamicQuest(type, difficulty, persona, text, target, "クエスト詳細", reward);
        QuestNPC npc = new QuestNPC(quest, location);
        npc.spawn();
        activeNPCs.add(npc);
    }

    private void despawnAll() {
        for (QuestNPC npc : activeNPCs) {
            npc.despawn();
        }
        activeNPCs.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        Player player = event.getPlayer();

        for (QuestNPC npc : activeNPCs) {
            if (npc.isEntity(clicked.getUniqueId())) {
                event.setCancelled(true);
                handleNPCInteraction(player, npc);
                return;
            }
        }
    }

    private void handleNPCInteraction(Player player, QuestNPC npc) {
        DynamicQuest quest = npc.getQuest();
        
        // Display Dialogue
        player.sendMessage(Component.text("--------------------------------", NamedTextColor.GRAY));
        player.sendMessage(Component.text(quest.getPersona().getDisplayName(), NamedTextColor.YELLOW)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(quest.getGeneratedDialogue(), NamedTextColor.WHITE)));
        
        // Display Options
        Component accept = Component.text("[受諾する]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/dq accept " + quest.getQuestId()))
                .hoverEvent(HoverEvent.showText(Component.text("クリックしてクエストを開始")));
        
        Component decline = Component.text("[拒否する]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/dq decline " + quest.getQuestId())) // Placeholder command
                .hoverEvent(HoverEvent.showText(Component.text("この話を忘れる")));

        player.sendMessage(Component.empty());
        player.sendMessage(accept.append(Component.text("  ")).append(decline));
        player.sendMessage(Component.text("--------------------------------", NamedTextColor.GRAY));
    }

    // --- Command Handling ---

    public void acceptQuest(Player player, UUID questId) {
        Optional<QuestNPC> npcOpt = activeNPCs.stream()
                .filter(n -> n.getQuest().getQuestId().equals(questId))
                .findFirst();

        if (npcOpt.isEmpty()) {
            player.sendMessage(Component.text("そのクエストはもう存在しません。", NamedTextColor.RED));
            return;
        }

        QuestNPC npc = npcOpt.get();
        DynamicQuest quest = npc.getQuest();
        activeNPCs.remove(npc);
        npc.despawn(); // Remove NPC on accept (or keep it?) - Removing for now as per "One-off" style or let's say they leave.

        player.sendMessage(Component.text("クエストを受諾しました！", NamedTextColor.GREEN));

        if (quest.getType() == QuestType.RAID) {
            // Start Raid Event
            // Start point: near player
            Location start = player.getLocation().add(random.nextInt(20) - 10, 0, random.nextInt(20) - 10);
            start.setY(start.getWorld().getHighestBlockYAt(start.getBlockX(), start.getBlockZ()) + 1);
            
            new SupplyConvoyEvent(plugin, start, quest.getTargetLocation()).start();
            player.sendMessage(Component.text(">> 補給部隊が近くに出現した！追跡して襲撃しろ！", NamedTextColor.RED));
        } else {
            // Handle other types (Placeholder)
            player.sendMessage(Component.text(">> 指定地点へ向かい、任務を遂行せよ。(未実装: " + quest.getType().name() + ")", NamedTextColor.YELLOW));
        }
    }

    public void declineQuest(Player player, UUID questId) {
        player.sendMessage(Component.text("クエストを拒否しました。", NamedTextColor.GRAY));
    }
}