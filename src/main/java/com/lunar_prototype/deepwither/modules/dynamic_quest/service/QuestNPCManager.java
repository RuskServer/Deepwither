package com.lunar_prototype.deepwither.modules.dynamic_quest.service;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.modules.dynamic_quest.enums.QuestDifficulty;
import com.lunar_prototype.deepwither.modules.dynamic_quest.enums.QuestPersona;
import com.lunar_prototype.deepwither.modules.dynamic_quest.enums.QuestType;
import com.lunar_prototype.deepwither.modules.dynamic_quest.npc.QuestNPC;
import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.DynamicQuest;
import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.QuestLocation;
import com.lunar_prototype.deepwither.modules.dynamic_quest.repository.QuestLocationRepository;
import com.lunar_prototype.deepwither.modules.dynamic_quest.dialogue.DialogueGenerator;
import com.lunar_prototype.deepwither.modules.dynamic_quest.objective.*;
import com.lunar_prototype.deepwither.modules.integration.service.IMobService;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class QuestNPCManager {

    private final Deepwither plugin;
    private final QuestLocationRepository repository;
    private final IMobService mobService;
    private final DialogueGenerator dialogueGenerator = new DialogueGenerator();
    private final List<QuestNPC> activeNPCs = new ArrayList<>();
    private final Random random = new Random();
    private BukkitTask spawnTask;

    private static final int NPCS_PER_REGION = 1;
    private static final long REFRESH_INTERVAL_TICKS = 20L * 60 * 10;

    /**
     * Creates a QuestNPCManager and wires required services for managing dynamic quest NPCs.
     *
     * @param plugin      main plugin instance used for scheduling, server context, and plugin APIs
     * @param repository  repository providing quest location lookup by type and layer
     * @param mobService  service used to select candidate mobs for combat-type quests
     */
    public QuestNPCManager(Deepwither plugin, QuestLocationRepository repository, IMobService mobService) {
        this.plugin = plugin;
        this.repository = repository;
        this.mobService = mobService;
    }

    /**
     * Initializes the manager by removing stale quest NPCs and scheduling the periodic spawn/refresh task.
     *
     * Performs an immediate cleanup of old NPC entities and starts the repeating task that spawns and refreshes NPCs.
     */
    public void init() {
        cleanupOldNPCs();
        startSpawnTask();
    }

    /**
     * Stops the recurring spawn task and despawns all active quest NPCs, performing manager shutdown cleanup.
     */
    public void shutdown() {
        stopSpawnTask();
        despawnAll();
    }

    /**
     * Schedules and starts the repeating spawn/refresh task that periodically updates dynamic quest NPCs.
     *
     * The scheduled task invokes refreshNPCs() on the configured interval and stores the task handle
     * so it can be cancelled later.
     */
    public void startSpawnTask() {
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshNPCs, 100L, REFRESH_INTERVAL_TICKS);
    }

    /**
     * Cancels the scheduled NPC spawn/refresh task if it is active.
     *
     * If no task is scheduled or it is already cancelled, this method does nothing.
     */
    public void stopSpawnTask() {
        if (spawnTask != null && !spawnTask.isCancelled()) {
            spawnTask.cancel();
        }
    }

    /**
     * Cleans up inactive quest NPCs and spawns new NPCs inside configured safezone regions.
     *
     * <p>First, performs a logical cleanup by removing NPCs that are expired, completed, or still in
     * the CREATED state (not accepted yet) from the tracked list and despawning them.
     * Then, iterates through all safezone regions, performing a physical cleanup of ghost NPC entities
     * and spawning new NPCs up to the limit defined by NPCS_PER_REGION, ensuring that total NPCs
     * in each region (including those currently ACTIVE) do not exceed the limit.</p>
     */
    public void refreshNPCs() {
        // 1. Logical Cleanup: Remove expired, completed, or unaccepted NPCs from tracking
        activeNPCs.removeIf(npc -> {
            boolean shouldRemove = npc.getQuest().getStatus() == DynamicQuest.QuestStatus.CREATED
                    || npc.getQuest().isExpired()
                    || npc.getQuest().getStatus() == DynamicQuest.QuestStatus.COMPLETED;
            if (shouldRemove) {
                npc.despawn();
                return true;
            }
            return false;
        });

        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        NamespacedKey key = new NamespacedKey(plugin, "quest_npc");

        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
            if (regionManager == null) continue;

            List<ProtectedRegion> safeRegions = regionManager.getRegions().values().stream()
                    .filter(r -> r.getId().toLowerCase().contains("safezone"))
                    .collect(Collectors.toList());

            for (ProtectedRegion region : safeRegions) {
                // 2. Physical Cleanup: Remove ghost NPCs in this specific region that aren't in activeNPCs
                cleanupRegionPhysical(world, region, key);

                // 3. Count remaining tracked NPCs in this region (now only contains ACTIVE ones)
                long currentCount = activeNPCs.stream()
                        .filter(npc -> isLocationInRegion(npc.getLocation(), region))
                        .count();

                // 4. Spawn new NPCs if we are below the limit
                int toSpawn = NPCS_PER_REGION - (int) currentCount;
                for (int i = 0; i < toSpawn; i++) {
                    Location spawnLoc = getRandomLocationInRegion(world, region);
                    if (spawnLoc != null) {
                        spawnNPC(spawnLoc);
                    }
                }
            }
        }
    }

    /**
     * Physically removes any NPC entities in a region that are marked as quest NPCs but are not
     * tracked by this manager's active NPC list.
     *
     * @param world  the world containing the region
     * @param region the ProtectedRegion to scan for ghost NPCs
     * @param key    the NamespacedKey used to identify quest NPC entities
     */
    private void cleanupRegionPhysical(World world, ProtectedRegion region, NamespacedKey key) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        
        // Use a conservative radius for getNearbyEntities based on region size
        double centerX = (min.x() + max.x()) / 2.0;
        double centerY = (min.y() + max.y()) / 2.0;
        double centerZ = (min.z() + max.z()) / 2.0;
        double radiusX = (max.x() - min.x()) / 2.0 + 2.0;
        double radiusY = (max.y() - min.y()) / 2.0 + 2.0;
        double radiusZ = (max.z() - min.z()) / 2.0 + 2.0;

        Location center = new Location(world, centerX, centerY, centerZ);
        Collection<Entity> entities = world.getNearbyEntities(center, radiusX, radiusY, radiusZ);

        for (Entity entity : entities) {
            if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                // Check if it's actually inside the region
                if (region.contains(BukkitAdapter.asBlockVector(entity.getLocation()))) {
                    boolean isActive = activeNPCs.stream().anyMatch(npc -> npc.isEntity(entity.getUniqueId()));
                    if (!isActive) {
                        entity.remove();
                    }
                }
            }
        }
    }

    /**
     * Checks whether a location is within the bounds of a WorldGuard region.
     *
     * @param loc    the location to check
     * @param region the ProtectedRegion to check against
     * @return true if the location is inside the region, false otherwise
     */
    private boolean isLocationInRegion(Location loc, ProtectedRegion region) {
        if (loc == null || loc.getWorld() == null) return false;
        return region.contains(BukkitAdapter.asBlockVector(loc));
    }

    /**
     * Selects a random location inside the given WorldGuard region that is suitable for spawning an NPC.
     *
     * @param world  the World containing the region
     * @param region the ProtectedRegion to sample the location from
     * @return a Location within the region whose block below is solid and whose current and above blocks are passable, or `null` if no suitable location is found
     */
    private Location getRandomLocationInRegion(World world, ProtectedRegion region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        for (int i = 0; i < 15; i++) {
            int x = ThreadLocalRandom.current().nextInt(min.x(), max.x() + 1);
            int z = ThreadLocalRandom.current().nextInt(min.z(), max.z() + 1);
            int minY = Math.max(world.getMinHeight(), min.y());
            int maxY = Math.min(world.getMaxHeight(), max.y());
            
            for (int j = 0; j < 10; j++) {
                int y = ThreadLocalRandom.current().nextInt(minY, maxY + 1);
                Location loc = new Location(world, x + 0.5, y, z + 0.5);
                if (loc.getBlock().isPassable() && 
                    loc.clone().add(0, 1, 0).getBlock().isPassable() && 
                    loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                    return loc;
                }
            }
        }
        return null;
    }

    /**
     * Spawns a quest-giving NPC at the specified location and sets up its associated DynamicQuest.
     *
     * <p>The method selects a persona, quest type, and difficulty, determines a quest target (from repository
     * locations for the current layer or by picking a distant random location), generates dialogue, constructs
     * a DynamicQuest with a type-specific objective and reward, creates and spawns a QuestNPC, and registers it
     * with the manager's active NPC list.</p>
     *
     * @param location the world location where the NPC will be spawned
     */
    public void spawnNPC(Location location) {
        QuestPersona persona = QuestPersona.values()[random.nextInt(QuestPersona.values().length)];
        QuestType type = QuestType.values()[random.nextInt(QuestType.values().length)];
        QuestDifficulty difficulty = type.getDefaultDifficulty(); 

        int npcLayer = getLayerId(location);

        Location target = null;
        Location startLocForEvent = null;
        List<QuestLocation> qLocs = repository.getLocations(type);
        if (!qLocs.isEmpty()) {
            List<QuestLocation> layerLocs = qLocs.stream()
                    .filter(l -> l.getLayerId() == npcLayer)
                    .collect(Collectors.toList());

            if (!layerLocs.isEmpty()) {
                QuestLocation chosen = layerLocs.get(random.nextInt(layerLocs.size()));
                target = chosen.getPos();
                if (type == QuestType.RAID) {
                    startLocForEvent = chosen.getPos();
                    target = chosen.getPos2() != null ? chosen.getPos2() : chosen.getPos();
                }
            }
        }

        if (target == null) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 100 + random.nextDouble() * 400;
            int dx = (int) (Math.cos(angle) * distance);
            int dz = (int) (Math.sin(angle) * distance);
            target = location.clone().add(dx, 0, dz);
            target.setY(target.getWorld().getHighestBlockYAt(target.getBlockX(), target.getBlockZ()) + 1);
        }

        String text = dialogueGenerator.generate(persona, type, target);
        double baseReward = 100;
        double reward = baseReward * difficulty.getRewardMultiplier();

        DynamicQuest quest = new DynamicQuest(type, difficulty, persona, text, target, reward);

        switch (type) {
            case FETCH:
                Material[] mats = {Material.IRON_INGOT, Material.BREAD, Material.COPPER_INGOT, Material.COAL};
                Material targetMat = mats[random.nextInt(mats.length)];
                int amount = 3 + random.nextInt(8);
                quest.setObjective(new FetchObjective(targetMat, amount));
                quest.setTargetItem(new ItemStack(targetMat));
                quest.setTargetAmount(amount);
                break;
            case DELIVERY:
                ItemStack item = new ItemStack(Material.PAPER);
                item.editMeta(meta -> meta.displayName(Component.text("重要書類", NamedTextColor.GOLD)));
                quest.setObjective(new ReachLocationObjective("指定地点まで 重要書類 を運ぶ", target, 10.0));
                quest.setTargetItem(item);
                break;
            case ELIMINATE:
                List<String> candidates = mobService.getQuestCandidateMobIdsByTier(npcLayer);
                String targetMob = candidates.isEmpty() ? "ZOMBIE" : candidates.get(random.nextInt(candidates.size()));
                int killCount = 5 + random.nextInt(6);
                quest.setObjective(new EliminateObjective(targetMob, killCount, mobService));
                break;
            case SCOUT:
                quest.setObjective(new ReachLocationObjective("指定地点の状況を確認する", target, 10.0));
                break;
            case RAID:
                quest.setObjective(new RaidObjective());
                break;
        }

        if (startLocForEvent != null) {
            quest.setStartLocation(startLocForEvent);
        }
        
        QuestNPC npc = new QuestNPC(quest, location);
        npc.spawn();
        activeNPCs.add(npc);
    }

    /**
     * Determine the applicable region layer (tier) for a location by parsing WorldGuard region IDs.
     *
     * @param loc the location to evaluate
     * @return the highest numeric tier parsed from WorldGuard region IDs that apply to the location (for example, `t3` -> 3); returns 0 if no tier is found or if WorldGuard is unavailable
     */
    public int getLayerId(Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return 0;
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        
        int maxTier = 0;
        for (ProtectedRegion region : set) {
            int tier = parseTierFromId(region.getId().toLowerCase());
            if (tier > maxTier) {
                maxTier = tier;
            }
        }
        return maxTier;
    }

    /**
     * Parse the numeric tier from a region ID following patterns like `t<number>` or `_t<number>`.
     *
     * <p>Examples: `t2` -> `2`, `region_t3_extra` -> `3`.</p>
     *
     * @param id the region identifier to parse
     * @return the parsed tier number, or `0` if no valid tier is present or the id indicates a safezone
     */
    private int parseTierFromId(String id) {
        if (id.contains("safezone")) return 0;

        // "t" の位置を特定 (先頭 または "_t" の直後)
        int tIdx = -1;
        if (id.startsWith("t")) {
            tIdx = 0;
        } else {
            int found = id.indexOf("_t");
            if (found != -1) {
                tIdx = found + 1; // 't' のインデックス
            }
        }

        if (tIdx == -1 || tIdx + 1 >= id.length()) return 0;

        // 数字の開始位置を特定 ("t1" or "t_1")
        int startDigit = -1;
        char next = id.charAt(tIdx + 1);
        if (Character.isDigit(next)) {
            startDigit = tIdx + 1;
        } else if (next == '_' && tIdx + 2 < id.length() && Character.isDigit(id.charAt(tIdx + 2))) {
            startDigit = tIdx + 2;
        }

        if (startDigit == -1) return 0;

        // 連続する数字を抽出
        StringBuilder numStr = new StringBuilder();
        for (int i = startDigit; i < id.length() && Character.isDigit(id.charAt(i)); i++) {
            numStr.append(id.charAt(i));
        }

        try {
            return Integer.parseInt(numStr.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * Removes leftover quest NPC entities found inside WorldGuard "safezone" regions.
     *
     * <p>Scans each world's WorldGuard region manager for regions whose id contains "safezone" and
     * asynchronously inspects chunks in those regions; any entity marked with the quest NPC key
     * that does not correspond to a currently tracked active NPC will be removed.</p>
     *
     * <p>If the WorldGuard plugin is not enabled, the method returns without performing any action.</p>
     */
    private void cleanupOldNPCs() {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return;

        NamespacedKey key = new NamespacedKey(plugin, "quest_npc");
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
            if (regionManager == null) continue;

            // safezoneリージョンを取得
            List<ProtectedRegion> safeRegions = regionManager.getRegions().values().stream()
                    .filter(r -> r.getId().toLowerCase().contains("safezone"))
                    .collect(Collectors.toList());

            for (ProtectedRegion region : safeRegions) {
                BlockVector3 min = region.getMinimumPoint();
                BlockVector3 max = region.getMaximumPoint();

                // リージョン内のチャンクを非同期でロードし、古いエンティティを削除
                for (int x = min.x() >> 4; x <= max.x() >> 4; x++) {
                    for (int z = min.z() >> 4; z <= max.z() >> 4; z++) {
                        world.getChunkAtAsync(x, z).thenAccept(chunk -> {
                            for (Entity entity : chunk.getEntities()) {
                                if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                                    // 起動直後のクリーンアップだが、非同期のため新しく生成されたNPCを消さないようガード
                                    boolean isActive = activeNPCs.stream().anyMatch(npc -> npc.isEntity(entity.getUniqueId()));
                                    if (!isActive) {
                                        entity.remove();
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    /**
     * Despawns every currently tracked quest NPC and clears the manager's active NPC list.
     *
     * Calls each tracked NPC's despawn method and removes all references so no NPCs remain active.
     */
    private void despawnAll() {
        for (QuestNPC npc : activeNPCs) {
            npc.despawn();
        }
        activeNPCs.clear();
    }

    /**
     * Removes the specified QuestNPC from the manager's active list and despawns it if it was tracked.
     *
     * @param npc the QuestNPC to remove; if it is currently active it will be despawned
     */
    public void removeNPC(QuestNPC npc) {
        if (activeNPCs.remove(npc)) {
            npc.despawn();
        }
    }

    /**
     * Returns a snapshot of the currently spawned quest NPCs.
     *
     * The returned list is a new ArrayList containing the active QuestNPC instances; modifying it does not affect the manager's internal state.
     *
     * @return a new List of currently active QuestNPC objects
     */
    public List<QuestNPC> getActiveNPCs() {
        return new ArrayList<>(activeNPCs);
    }
}