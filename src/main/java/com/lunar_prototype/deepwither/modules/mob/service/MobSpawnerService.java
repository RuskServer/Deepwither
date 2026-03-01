package com.lunar_prototype.deepwither.modules.mob.service;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.aethelgard.LocationDetails;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.aethelgard.QuestProgress;
import com.lunar_prototype.deepwither.modules.mob.util.MobRegionService;
import com.lunar_prototype.deepwither.outpost.OutpostEvent;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class MobSpawnerService implements IManager {

    private final Deepwither plugin;
    private final MobConfigService configService;
    private final MobRegistryService registryService;
    private final MobRegionService regionService;
    private final MobTraitService traitService;
    private final MobLevelService levelService;
    private final PlayerQuestManager playerQuestManager;

    private final String targetWorldName = "Aether";
    private final long spawnIntervalTicks = 7 * 20L;
    private static final int MOB_CAP_PER_PLAYER = 10;
    private static final double QUEST_AREA_RADIUS_SQUARED = 20.0 * 20.0;

    public MobSpawnerService(Deepwither plugin, 
                             MobConfigService configService,
                             MobRegistryService registryService,
                             MobRegionService regionService,
                             MobTraitService traitService,
                             MobLevelService levelService,
                             PlayerQuestManager playerQuestManager) {
        this.plugin = plugin;
        this.configService = configService;
        this.registryService = registryService;
        this.regionService = regionService;
        this.traitService = traitService;
        this.levelService = levelService;
        this.playerQuestManager = playerQuestManager;
    }

    @Override
    public void init() {
        startSpawnScheduler();
    }

    @Override
    public void shutdown() {}

    private void startSpawnScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                World targetWorld = Bukkit.getWorld(targetWorldName);
                if (targetWorld == null) return;
                for (Player player : targetWorld.getPlayers()) {
                    processPlayerSpawn(player);
                }
                registryService.cleanupDeadMobs();
            }
        }.runTaskTimer(plugin, 20L, spawnIntervalTicks);
    }

    private void processPlayerSpawn(Player player) {
        Location playerLoc = player.getLocation();
        UUID playerId = player.getUniqueId();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;

        if (regionService.isSpawnLocked(playerId, playerLoc)) return;

        int currentMobs = registryService.getTrackedMobCount(playerId);
        if (currentMobs >= MOB_CAP_PER_PLAYER) {
            regionService.setSpawnLock(playerId, playerLoc);
            return;
        }

        if (regionService.isSafeZone(playerLoc)) return;
        if (regionService.isSpawnDisabledInWGRegion(playerLoc, registryService)) return;
        if (trySpawnQuestMob(player, playerLoc, currentMobs)) return;

        int tier = regionService.getTierFromLocation(playerLoc);
        if (tier == 0) return;

        MobConfigService.MobTierConfig config = configService.getTierConfig(tier);
        if (config == null || config.getRegularMobs().isEmpty()) return;

        if (trySpawnMiniBoss(playerLoc, config, playerId)) return;

        List<String> regularMobs = config.getRegularMobs();
        String mobType = regularMobs.get(plugin.getRandom().nextInt(regularMobs.size()));
        Location spawnLoc = getRandomSpawnLocation(playerLoc, 15);
        if (spawnLoc == null) return;

        if (mobType.equalsIgnoreCase("bandit")) {
            spawnBanditGroup(playerId, config, spawnLoc, tier);
        } else {
            registryService.trackSpawnedMob(playerId, spawnMythicMob(mobType, spawnLoc, tier));
        }
    }

    private void spawnBanditGroup(UUID playerId, MobConfigService.MobTierConfig config, Location spawnLoc, int tier) {
        List<String> banditList = config.getBanditMobs();
        if (banditList.isEmpty()) return;
        
        int numBandits;
        int weight = plugin.getRandom().nextInt(10);
        if (weight < 6) numBandits = 1;
        else if (weight < 9) numBandits = 2;
        else numBandits = 3;

        for (int i = 0; i < numBandits; i++) {
            String banditMobId = banditList.get(plugin.getRandom().nextInt(banditList.size()));
            registryService.trackSpawnedMob(playerId, spawnMythicMob(banditMobId, spawnLoc, tier));
        }
    }

    public UUID spawnMythicMob(String mobId, Location loc, int tier) {
        var activeMob = MythicBukkit.inst().getMobManager().spawnMob(mobId, loc);
        if (activeMob == null || activeMob.getEntity() == null) return null;
        org.bukkit.entity.Entity entity = activeMob.getEntity().getBukkitEntity();
        if (!(entity instanceof org.bukkit.entity.LivingEntity livingEntity)) return entity.getUniqueId();

        int spawnLevel = calculateSpawnLevel(loc, tier);
        String mobDisplayName = activeMob.getType().getDisplayName().get();
        if (mobDisplayName == null) mobDisplayName = mobId;

        levelService.applyLevel(livingEntity, mobDisplayName, spawnLevel);
        traitService.applyRandomTraits(livingEntity, spawnLevel);

        return livingEntity.getUniqueId();
    }

    private int calculateSpawnLevel(Location loc, int tier) {
        MobConfigService.MobTierConfig config = configService.getTierConfig(tier);
        int areaMaxLevel = (config != null) ? config.getAreaLevel() : 1;

        List<Player> nearbyPlayers = loc.getWorld().getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL)
                .filter(p -> p.getLocation().distance(loc) <= 20)
                .toList();

        int spawnLevel;
        if (!nearbyPlayers.isEmpty()) {
            Player targetPlayer = nearbyPlayers.get(plugin.getRandom().nextInt(nearbyPlayers.size()));
            spawnLevel = targetPlayer.getLevel();
        } else {
            int minLvl = Math.max(1, areaMaxLevel - 5);
            spawnLevel = plugin.getRandom().nextInt((areaMaxLevel - minLvl) + 1) + minLvl;
        }

        spawnLevel = Math.min(spawnLevel, areaMaxLevel);
        return Math.max(1, spawnLevel);
    }

    private Location getRandomSpawnLocation(Location center, int radius) {
        Random random = plugin.getRandom();
        double x = center.getX() + (random.nextDouble() * 2 * radius) - radius;
        double z = center.getZ() + (random.nextDouble() * 2 * radius) - radius;
        World world = center.getWorld();
        int startY = (int) Math.min(world.getMaxHeight() - 2, center.getY() + 3);
        for (int y = startY; y > (world.getMinHeight() + 1); y--) {
            Location checkLoc = new Location(world, x, y, z);
            if (checkLoc.getBlock().getType().isAir() && checkLoc.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                Location blockBelow = checkLoc.clone().subtract(0, 1, 0);
                if (!blockBelow.getBlock().getType().isAir() && blockBelow.getBlock().isSolid()) {
                    return new Location(world, x + 0.5, y + 0.0, z + 0.5);
                }
            }
        }
        return null;
    }

    private boolean trySpawnMiniBoss(Location centerLoc, MobConfigService.MobTierConfig config, UUID playerId) {
        Map<String, Double> miniBosses = config.getMiniBosses();
        if (miniBosses.isEmpty()) return false;
        
        double roll = plugin.getRandom().nextDouble();
        for (Map.Entry<String, Double> entry : miniBosses.entrySet()) {
            String mobId = entry.getKey();
            double chance = entry.getValue();
            if (roll <= chance) {
                Location spawnLoc = getRandomSpawnLocation(centerLoc, 15);
                if (spawnLoc != null) {
                    UUID mobUuid = spawnMythicMob(mobId, spawnLoc, regionService.getTierFromLocation(spawnLoc));
                    registryService.trackSpawnedMob(playerId, mobUuid);
                    notifyNearbyPlayers(centerLoc, mobId);
                    return true;
                }
            }
        }
        return false;
    }

    private void notifyNearbyPlayers(Location centerLoc, String mobId) {
        for (Player p : centerLoc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(centerLoc) < 40 * 40) {
                p.sendMessage(Component.text("!!! 危険な反応 !!! ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                        .append(Component.text(mobId, NamedTextColor.RED))
                        .append(Component.text("が付近に出現しました！", NamedTextColor.RED)));
            }
        }
    }

    private boolean trySpawnQuestMob(Player player, Location playerLoc, int currentMobs) {
        PlayerQuestData playerData = playerQuestManager.getPlayerData(player.getUniqueId());
        if (playerData == null || playerData.getActiveQuests().isEmpty()) return false;
        
        QuestProgress progress = playerData.getActiveQuests().values().iterator().next();
        LocationDetails locationDetails = progress.getQuestDetails().getLocationDetails();
        Location objectiveLoc = locationDetails.toBukkitLocation();
        
        if (objectiveLoc == null || !objectiveLoc.getWorld().equals(playerLoc.getWorld())) return false;
        
        if (objectiveLoc.distanceSquared(playerLoc) <= QUEST_AREA_RADIUS_SQUARED) {
            if (currentMobs >= MOB_CAP_PER_PLAYER) return true;
            String questMobId = progress.getQuestDetails().getTargetMobId();
            Location spawnLoc = getRandomSpawnLocation(playerLoc, 8);
            if (spawnLoc != null) {
                UUID mobUuid = spawnMythicMob(questMobId, spawnLoc, regionService.getTierFromLocation(spawnLoc));
                registryService.trackSpawnedMob(player.getUniqueId(), mobUuid);
                player.sendMessage(Component.text("[クエストエリア] ", NamedTextColor.GREEN).append(Component.text("目標 Mob がスポーンしました！", NamedTextColor.YELLOW)));
            }
            return true;
        }
        return false;
    }

    public int spawnOutpostMobs(String mobId, int count, String regionId, double fixedY, OutpostEvent event) {
        World world = Bukkit.getWorld(event.getOutpostData().getWorldName());
        if (world == null) return 0;
        int spawnedCount = 0;
        int maxRetries = 10;
        for (int i = 0; i < count; i++) {
            Location spawnLoc = null;
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                Location candidate = regionService.getRandomLocationInRegion(world, regionId, fixedY);
                if (candidate == null) break;
                spawnLoc = candidate;
                if (!candidate.getBlock().getType().isSolid() && !candidate.clone().add(0, 1, 0).getBlock().getType().isSolid()) break;
            }
            if (spawnLoc != null) {
                UUID mobUuid = spawnMythicMob(mobId, spawnLoc, regionService.getTierFromLocation(spawnLoc));
                if (mobUuid != null) {
                    registryService.trackOutpostMob(mobUuid, regionId);
                    spawnedCount++;
                }
            }
        }
        return spawnedCount;
    }
}
