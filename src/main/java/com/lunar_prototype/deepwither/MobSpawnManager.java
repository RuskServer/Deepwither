package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.outpost.OutpostEvent;
import com.lunar_prototype.deepwither.aethelgard.LocationDetails;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.aethelgard.QuestProgress;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@DependsOn({PlayerQuestManager.class})
public class MobSpawnManager implements IManager {

    private final Deepwither plugin;
    private final PlayerQuestManager playerQuestManager;
    private MobLevelManager levelManager;

    private final String targetWorldName = "Aether";
    private final long spawnIntervalTicks = 7 * 20L;

    private static final int MOB_CAP_PER_PLAYER = 10;
    private static final int COUNT_RADIUS = 20;

    private static final double QUEST_AREA_RADIUS_SQUARED = 20.0 * 20.0;

    private final Map<UUID, Location> spawnLockLocations = new HashMap<>();
    private static final double MOVE_UNLOCK_DISTANCE_SQUARED = 30.0 * 30.0;

    private final Map<UUID, Set<UUID>> spawnedMobsTracker = new ConcurrentHashMap<>();
    private final Map<UUID, String> outpostMobTracker = new ConcurrentHashMap<>();
    private final Set<String> spawnDisabledRegions = ConcurrentHashMap.newKeySet();
    private final Map<Integer, MobTierConfig> mobTierConfigs = new HashMap<>();

    private static final NamespacedKey TRAIT_KEY = new NamespacedKey(Deepwither.getInstance(), "mob_traits");

    public MobSpawnManager(Deepwither plugin, PlayerQuestManager playerQuestManager) {
        this.plugin = plugin;
        this.playerQuestManager = playerQuestManager;
    }

    @Override
    public void init() {
        this.levelManager = new MobLevelManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(levelManager, plugin);
        loadMobTierConfigs();
        startSpawnScheduler();
        startGlobalTraitTicker();
    }

    @Override
    public void shutdown() {}

    private enum MobTrait {
        BERSERK(Component.text("[狂化]", NamedTextColor.RED), "Basic"),
        HARDENED(Component.text("[硬化]", NamedTextColor.GRAY), "Basic"),
        PIERCING(Component.text("[貫通]", NamedTextColor.YELLOW), "Basic"),
        SNARING_AURA(Component.text("[鈍足]", NamedTextColor.BLUE), "Basic"),
        MANA_LEECH(Component.text("[魔食]", NamedTextColor.LIGHT_PURPLE), "Intermediate"),
        DISRUPTIVE(Component.text("[妨害]", NamedTextColor.DARK_PURPLE), "Intermediate"),
        BLINKING(Component.text("[瞬身]", NamedTextColor.AQUA), "Intermediate"),
        SUMMONER(Component.text("[召喚]", NamedTextColor.GREEN), "Intermediate");

        private final Component displayName;
        private final String category;

        MobTrait(Component displayName, String category) {
            this.displayName = displayName;
            this.category = category;
        }
    }

    private static final double TRAIT_SPAWN_CHANCE = 0.15;

    private void loadMobTierConfigs() {
        mobTierConfigs.clear();
        ConfigurationSection mobSpawnsSection = plugin.getConfig().getConfigurationSection("mob_spawns");
        if (mobSpawnsSection == null) return;

        for (String tierKey : mobSpawnsSection.getKeys(false)) {
            try {
                int tierNumber = Integer.parseInt(tierKey);
                ConfigurationSection tierSection = mobSpawnsSection.getConfigurationSection(tierKey);
                if (tierSection == null) continue;
                int areaLevel = tierSection.getInt("area_level", 999);
                List<String> regularMobs = tierSection.getStringList("regular_mobs");
                List<String> banditMobs = tierSection.getStringList("bandit_mobs");
                if (regularMobs.isEmpty() && banditMobs.isEmpty()) continue;
                ConfigurationSection bossSection = tierSection.getConfigurationSection("mini_bosses");
                Map<String, Double> miniBosses = new HashMap<>();
                if (bossSection != null) {
                    for (String mobId : bossSection.getKeys(false)) {
                        miniBosses.put(mobId, bossSection.getDouble(mobId));
                    }
                }
                mobTierConfigs.put(tierNumber, new MobTierConfig(areaLevel, regularMobs, banditMobs, miniBosses));
            } catch (NumberFormatException ignored) {}
        }
    }

    private void startSpawnScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                World targetWorld = Bukkit.getWorld(targetWorldName);
                if (targetWorld == null) return;
                for (Player player : targetWorld.getPlayers()) processPlayerSpawn(player);
                cleanupDeadMobs();
            }
        }.runTaskTimer(plugin, 20L, spawnIntervalTicks);
    }

    private void processPlayerSpawn(Player player) {
        Location playerLoc = player.getLocation();
        UUID playerId = player.getUniqueId();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;

        Location lockLoc = spawnLockLocations.get(playerId);
        if (lockLoc != null) {
            if (playerLoc.distanceSquared(lockLoc) < MOVE_UNLOCK_DISTANCE_SQUARED) return;
            else spawnLockLocations.remove(playerId);
        }

        int currentMobs = getTrackedMobCount(playerId);
        if (currentMobs >= MOB_CAP_PER_PLAYER) {
            spawnLockLocations.put(playerId, playerLoc);
            return;
        }

        if (isSafeZone(playerLoc)) return;
        if (isOutpostDisabledRegion(playerLoc)) return;
        if (trySpawnQuestMob(player, playerLoc, currentMobs)) return;

        int tier = getTierFromLocation(playerLoc);
        if (tier == 0) return;

        MobTierConfig config = mobTierConfigs.get(tier);
        if (config == null || config.getRegularMobs().isEmpty()) return;

        if (trySpawnMiniBoss(playerLoc, config, playerId)) return;

        List<String> regularMobs = config.getRegularMobs();
        String mobType = regularMobs.get(plugin.getRandom().nextInt(regularMobs.size()));
        Location spawnLoc = getRandomSpawnLocation(playerLoc, 15);
        if (spawnLoc == null) return;

        if (mobType.equalsIgnoreCase("bandit")) {
            List<String> banditList = config.getBanditMobs();
            if (banditList.isEmpty()) return;
            int numBandits;
            int weight = plugin.getRandom().nextInt(10);
            if (weight < 6) numBandits = 1;
            else if (weight < 9) numBandits = 2;
            else numBandits = 3;

            for (int i = 0; i < numBandits; i++) {
                String banditMobId = banditList.get(plugin.getRandom().nextInt(banditList.size()));
                trackSpawnedMob(playerId, spawnMythicMob(banditMobId, spawnLoc, tier));
            }
        } else {
            trackSpawnedMob(playerId, spawnMythicMob(mobType, spawnLoc, tier));
        }
    }

    public UUID spawnDungeonMob(Location loc, String mobId, int level) {
        var activeMob = MythicBukkit.inst().getMobManager().spawnMob(mobId, loc);
        if (activeMob == null || activeMob.getEntity() == null) return null;
        org.bukkit.entity.Entity entity = activeMob.getEntity().getBukkitEntity();
        if (!(entity instanceof org.bukkit.entity.LivingEntity livingEntity)) return entity.getUniqueId();
        String mobDisplayName = activeMob.getType().getDisplayName().get();
        if (mobDisplayName == null) mobDisplayName = mobId;
        levelManager.applyLevel(livingEntity, mobDisplayName, level);
        return livingEntity.getUniqueId();
    }

    public void disableNormalSpawning(String regionId) { spawnDisabledRegions.add(regionId.toLowerCase()); }
    public void enableNormalSpawning(String regionId) { spawnDisabledRegions.remove(regionId.toLowerCase()); }
    public String getMobOutpostId(Entity mob) { return outpostMobTracker.get(mob.getUniqueId()); }
    public void untrackOutpostMob(UUID mobUuid) { outpostMobTracker.remove(mobUuid); }

    private boolean isOutpostDisabledRegion(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        for (ProtectedRegion region : set) {
            if (spawnDisabledRegions.contains(region.getId().toLowerCase())) return true;
        }
        return false;
    }

    private int getTrackedMobCount(UUID playerId) {
        return spawnedMobsTracker.getOrDefault(playerId, Collections.emptySet()).size();
    }

    private void trackSpawnedMob(UUID playerId, UUID mobUuid) {
        if (mobUuid == null) return;
        spawnedMobsTracker.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(mobUuid);
    }

    public void untrackMob(UUID mobUuid) {
        for (Set<UUID> mobUuids : spawnedMobsTracker.values()) mobUuids.remove(mobUuid);
    }

    private void cleanupDeadMobs() {
        for (Map.Entry<UUID, Set<UUID>> entry : spawnedMobsTracker.entrySet()) {
            entry.getValue().removeIf(uuid -> Bukkit.getEntity(uuid) == null);
        }
        spawnedMobsTracker.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public int spawnOutpostMobs(String mobId, int count, String regionId, double fixedY, OutpostEvent event) {
        World world = Bukkit.getWorld(event.getOutpostData().getWorldName());
        if (world == null) return 0;
        int spawnedCount = 0;
        int maxRetries = 10;
        for (int i = 0; i < count; i++) {
            Location spawnLoc = null;
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                Location candidate = getRandomLocationInRegion(world, regionId, fixedY);
                if (candidate == null) break;
                spawnLoc = candidate;
                if (!candidate.getBlock().getType().isSolid() && !candidate.clone().add(0, 1, 0).getBlock().getType().isSolid()) break;
            }
            if (spawnLoc != null) {
                UUID mobUuid = spawnMythicMob(mobId, spawnLoc, getTierFromLocation(spawnLoc));
                if (mobUuid != null) {
                    outpostMobTracker.put(mobUuid, regionId);
                    spawnedCount++;
                }
            }
        }
        return spawnedCount;
    }

    public void removeAllOutpostMobs(String regionId) {
        if (regionId == null) return;
        List<UUID> mobsToRemove = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : outpostMobTracker.entrySet()) {
            if (regionId.equalsIgnoreCase(entry.getValue())) mobsToRemove.add(entry.getKey());
        }
        if (mobsToRemove.isEmpty()) return;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (mobsToRemove.contains(entity.getUniqueId())) entity.remove();
            }
        }
        for (UUID mobUuid : mobsToRemove) outpostMobTracker.remove(mobUuid);
    }

    private UUID spawnMythicMob(String mobId, Location loc, int tier) {
        var activeMob = MythicBukkit.inst().getMobManager().spawnMob(mobId, loc);
        if (activeMob == null || activeMob.getEntity() == null) return null;
        org.bukkit.entity.Entity entity = activeMob.getEntity().getBukkitEntity();
        if (!(entity instanceof org.bukkit.entity.LivingEntity livingEntity)) return entity.getUniqueId();

        List<Player> nearbyPlayers = loc.getWorld().getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL)
                .filter(p -> p.getLocation().distance(loc) <= 20)
                .toList();

        int spawnLevel;
        MobTierConfig config = mobTierConfigs.get(tier);
        int areaMaxLevel = (config != null) ? config.getAreaLevel() : 1;

        if (!nearbyPlayers.isEmpty()) {
            Player targetPlayer = nearbyPlayers.get(plugin.getRandom().nextInt(nearbyPlayers.size()));
            spawnLevel = targetPlayer.getLevel();
        } else {
            int minLvl = Math.max(1, areaMaxLevel - 5);
            spawnLevel = plugin.getRandom().nextInt((areaMaxLevel - minLvl) + 1) + minLvl;
        }

        spawnLevel = Math.min(spawnLevel, areaMaxLevel);
        spawnLevel = Math.max(1, spawnLevel);

        String mobDisplayName = activeMob.getType().getDisplayName().get();
        if (mobDisplayName == null) mobDisplayName = mobId;

        levelManager.applyLevel(livingEntity, mobDisplayName, spawnLevel);
        applyRandomTraits(livingEntity, spawnLevel, mobDisplayName);

        return livingEntity.getUniqueId();
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

    private void applyRandomTraits(LivingEntity entity, int level, String originalName) {
        if (plugin.getRandom().nextDouble() > TRAIT_SPAWN_CHANCE) return;
        List<MobTrait> selectedTraits = getRandomTraitsForLevel(level);
        if (selectedTraits.isEmpty()) return;

        String traitData = selectedTraits.stream().map(Enum::name).collect(Collectors.joining(","));
        entity.getPersistentDataContainer().set(TRAIT_KEY, PersistentDataType.STRING, traitData);
        entity.setGlowing(true);

        Component traitText = Component.empty();
        for (int i = 0; i < selectedTraits.size(); i++) {
            traitText = traitText.append(selectedTraits.get(i).displayName);
            if (i < selectedTraits.size() - 1) traitText = traitText.append(Component.text(" "));
        }

        Component finalTraitText = traitText;
        TextDisplay display = entity.getWorld().spawn(entity.getLocation(), TextDisplay.class, (td) -> {
            td.text(finalTraitText);
            td.setBillboard(Display.Billboard.CENTER);
            td.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            td.setShadowed(true);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            Transformation transformation = td.getTransformation();
            transformation.getTranslation().set(0, 1.2f, 0);
            td.setTransformation(transformation);
        });
        entity.addPassenger(display);
        startCleanupTask(entity, display);
    }

    public void startGlobalTraitTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (LivingEntity entity : world.getLivingEntities()) {
                        List<String> traits = getTraits(entity);
                        if (traits.isEmpty()) continue;
                        if (traits.contains("SNARING_AURA")) {
                            entity.getNearbyEntities(5, 5, 5).stream()
                                    .filter(e -> e instanceof Player)
                                    .forEach(p -> ((Player)p).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0)));
                        }
                        if (traits.contains("SUMMONER") && Math.random() < 0.05) {
                            entity.getWorld().spawnEntity(entity.getLocation(), EntityType.SILVERFISH);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private List<String> getTraits(LivingEntity entity) {
        String data = entity.getPersistentDataContainer().get(TRAIT_KEY, PersistentDataType.STRING);
        if (data == null) return List.of();
        return Arrays.asList(data.split(","));
    }

    private void startCleanupTask(LivingEntity mob, TextDisplay display) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead()) {
                    display.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    public List<String> getMobTraits(LivingEntity entity) {
        String data = entity.getPersistentDataContainer().get(TRAIT_KEY, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return Collections.emptyList();
        return Arrays.asList(data.split(","));
    }

    private List<MobTrait> getRandomTraitsForLevel(int level) {
        List<MobTrait> available = new ArrayList<>();
        int count = 0;
        if (level >= 10 && level < 15) { count = 1; available = getTraitsByCategory("Basic"); }
        else if (level >= 15 && level < 25) { count = plugin.getRandom().nextInt(3) + 1; available = getTraitsByCategory("Basic"); }
        else if (level >= 25 && level <= 35) { count = plugin.getRandom().nextInt(2) + 1; available = getTraitsByCategory("Intermediate"); }
        if (available.isEmpty()) return Collections.emptyList();
        Collections.shuffle(available);
        return available.stream().limit(count).collect(Collectors.toList());
    }

    private List<MobTrait> getTraitsByCategory(String category) {
        return Arrays.stream(MobTrait.values()).filter(t -> t.category.equals(category)).collect(Collectors.toList());
    }

    private Location getRandomLocationInRegion(World world, String regionId, double fixedY) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
        if (regionManager == null) return null;
        ProtectedRegion region = regionManager.getRegion(regionId);
        if (region == null) return null;
        Random random = plugin.getRandom();
        int minX = region.getMinimumPoint().x();
        int maxX = region.getMaximumPoint().x();
        int minZ = region.getMinimumPoint().z();
        int maxZ = region.getMaximumPoint().z();
        double x = minX + random.nextDouble() * (maxX - minX + 1);
        double z = minZ + random.nextDouble() * (maxZ - minZ + 1);
        return new Location(world, x, fixedY, z);
    }

    private boolean trySpawnMiniBoss(Location centerLoc, MobTierConfig config, UUID playerId) {
        Map<String, Double> miniBosses = config.getMiniBosses();
        if (miniBosses.isEmpty()) return false;
        Random random = plugin.getRandom();
        double roll = random.nextDouble();
        for (Map.Entry<String, Double> entry : miniBosses.entrySet()) {
            String mobId = entry.getKey();
            double chance = entry.getValue();
            if (roll <= chance) {
                Location spawnLoc = getRandomSpawnLocation(centerLoc, 15);
                if (spawnLoc != null) {
                    UUID mobUuid = spawnMythicMob(mobId, spawnLoc, getTierFromLocation(spawnLoc));
                    trackSpawnedMob(playerId, mobUuid);
                    for (Player p : centerLoc.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(centerLoc) < 40 * 40) {
                            p.sendMessage(Component.text("!!! 危険な反応 !!! ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                                    .append(Component.text(mobId, NamedTextColor.RED))
                                    .append(Component.text("が付近に出現しました！", NamedTextColor.RED)));
                        }
                    }
                    return true;
                }
            }
        }
        return false;
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
                UUID mobUuid = spawnMythicMob(questMobId, spawnLoc, getTierFromLocation(spawnLoc));
                trackSpawnedMob(player.getUniqueId(), mobUuid);
                player.sendMessage(Component.text("[クエストエリア] ", NamedTextColor.GREEN).append(Component.text("目標 Mob がスポーンしました！", NamedTextColor.YELLOW)));
            }
            return true;
        }
        return false;
    }

    private boolean isSafeZone(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        for (ProtectedRegion region : set) {
            String regionId = region.getId().toLowerCase();
            if (regionId.contains("safezone") || regionId.contains("kbf")) return true;
        }
        return false;
    }

    public List<String> getQuestCandidateMobIdsByTier(int tier) {
        MobTierConfig config = mobTierConfigs.get(tier);
        if (config == null) return Collections.emptyList();
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String mobId : config.getRegularMobs()) {
            if (mobId == null || mobId.isEmpty()) continue;
            if (mobId.equalsIgnoreCase("bandit")) candidates.addAll(config.getBanditMobs());
            else candidates.add(mobId);
        }
        if (candidates.isEmpty()) candidates.addAll(config.getBanditMobs());
        return new ArrayList<>(candidates);
    }

    public int getTierFromLocation(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        int maxTier = 0;
        for (ProtectedRegion region : set) {
            String id = region.getId().toLowerCase();
            if (id.contains("safezone")) return 0;
            int tierIndex = id.indexOf("t");
            if (tierIndex != -1 && tierIndex + 1 < id.length()) {
                char nextChar = id.charAt(tierIndex + 1);
                if (Character.isDigit(nextChar)) {
                    StringBuilder tierStr = new StringBuilder();
                    int i = tierIndex + 1;
                    while (i < id.length() && Character.isDigit(id.charAt(i))) {
                        tierStr.append(id.charAt(i));
                        i++;
                    }
                    try {
                        int tier = Integer.parseInt(tierStr.toString());
                        if (tier > maxTier) maxTier = tier;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return maxTier;
    }

    private static class MobTierConfig {
        private final int areaLevel;
        private final List<String> regularMobs;
        private final List<String> banditMobs;
        private final Map<String, Double> miniBosses;
        public MobTierConfig(int areaLevel, List<String> regularMobs, List<String> banditMobs, Map<String, Double> miniBosses) {
            this.areaLevel = areaLevel; this.regularMobs = regularMobs; this.banditMobs = banditMobs; this.miniBosses = miniBosses;
        }
        public int getAreaLevel() { return areaLevel; }
        public List<String> getRegularMobs() { return regularMobs; }
        public List<String> getBanditMobs() { return banditMobs; }
        public Map<String, Double> getMiniBosses() { return miniBosses; }
    }
}
