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
import com.lunar_prototype.deepwither.MobSpawnManager;
import com.lunar_prototype.deepwither.api.DW;
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
    private final DialogueGenerator dialogueGenerator = new DialogueGenerator();
    private final List<QuestNPC> activeNPCs = new ArrayList<>();
    private final Random random = new Random();
    private BukkitTask spawnTask;

    private static final int NPCS_PER_REGION = 1;
    private static final long REFRESH_INTERVAL_TICKS = 20L * 60 * 10;

    public QuestNPCManager(Deepwither plugin, QuestLocationRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void init() {
        cleanupOldNPCs();
        startSpawnTask();
    }

    public void shutdown() {
        stopSpawnTask();
        despawnAll();
    }

    public void startSpawnTask() {
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshNPCs, 100L, REFRESH_INTERVAL_TICKS);
    }

    public void stopSpawnTask() {
        if (spawnTask != null && !spawnTask.isCancelled()) {
            spawnTask.cancel();
        }
    }

    public void refreshNPCs() {
        activeNPCs.removeIf(npc -> {
            if (npc.getQuest().getStatus() == DynamicQuest.QuestStatus.CREATED) {
                npc.despawn();
                return true;
            }
            return false;
        });

        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
            if (regionManager == null) continue;

            List<ProtectedRegion> safeRegions = regionManager.getRegions().values().stream()
                    .filter(r -> r.getId().toLowerCase().contains("safezone"))
                    .collect(Collectors.toList());

            for (ProtectedRegion region : safeRegions) {
                for (int i = 0; i < NPCS_PER_REGION; i++) {
                    Location spawnLoc = getRandomLocationInRegion(world, region);
                    if (spawnLoc != null) {
                        spawnNPC(spawnLoc);
                    }
                }
            }
        }
    }

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
                MobSpawnManager mobManager = DW.get(MobSpawnManager.class);
                List<String> candidates = mobManager != null ? mobManager.getQuestCandidateMobIdsByTier(npcLayer) : new ArrayList<>();
                String targetMob = candidates.isEmpty() ? "ZOMBIE" : candidates.get(random.nextInt(candidates.size()));
                int killCount = 5 + random.nextInt(6);
                quest.setObjective(new EliminateObjective(targetMob, killCount));
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

    public int getLayerId(Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return 0;
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        int maxTier = 0;
        for (ProtectedRegion region : set) {
            String id = region.getId().toLowerCase();
            if (id.contains("safezone")) continue;
            int tier = 0;
            int tIdx = id.startsWith("t") ? 0 : id.contains("_t") ? id.indexOf("_t") + 1 : -1;
            if (tIdx != -1 && tIdx + 1 < id.length()) {
                char next = id.charAt(tIdx + 1);
                int startDigit = Character.isDigit(next) ? tIdx + 1 : (next == '_' && tIdx + 2 < id.length() && Character.isDigit(id.charAt(tIdx + 2))) ? tIdx + 2 : -1;
                if (startDigit != -1) {
                    StringBuilder numStr = new StringBuilder();
                    for (int i = startDigit; i < id.length() && Character.isDigit(id.charAt(i)); i++) { numStr.append(id.charAt(i)); }
                    try { tier = Integer.parseInt(numStr.toString()); } catch (NumberFormatException ignored) {}
                }
            }
            if (tier > maxTier) maxTier = tier;
        }
        return maxTier;
    }

    private void cleanupOldNPCs() {
        NamespacedKey key = new NamespacedKey(plugin, "quest_npc");
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    private void despawnAll() {
        for (QuestNPC npc : activeNPCs) {
            npc.despawn();
        }
        activeNPCs.clear();
    }

    public List<QuestNPC> getActiveNPCs() {
        return activeNPCs;
    }
}
