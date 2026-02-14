package com.lunar_prototype.deepwither.loot;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@DependsOn({ItemFactory.class})
public class LootChestManager implements IManager {

    private final Deepwither plugin;
    private final Random random = ThreadLocalRandom.current();
    private final Map<Integer, List<WeightedTemplate>> tieredTemplates = new HashMap<>();
    private final Map<Location, BukkitTask> activeLootChests = new ConcurrentHashMap<>();
    private final Map<String, LootChestTemplate> templates = new HashMap<>();
    private int spawnRadius = 30;
    private long spawnIntervalTicks = 3600L;

    public LootChestManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        loadConfigs();
        startScheduler();
    }

    @Override
    public void shutdown() {
        removeAllLootChests();
    }

    private static class WeightedTemplate {
        final LootChestTemplate template;
        final int weight;
        WeightedTemplate(LootChestTemplate template, int weight) {
            this.template = template;
            this.weight = weight;
        }
    }

    public void loadConfigs() {
        File lootFile = new File(plugin.getDataFolder(), "lootchest.yml");
        if (!lootFile.exists()) {
            plugin.saveResource("lootchest.yml", false);
        }
        YamlConfiguration lootConfig = YamlConfiguration.loadConfiguration(lootFile);

        templates.clear();
        for (String templateName : lootConfig.getKeys(false)) {
            ConfigurationSection section = lootConfig.getConfigurationSection(templateName);
            if (section != null) {
                templates.put(templateName, LootChestTemplate.loadFromConfig(templateName, section));
            }
        }

        FileConfiguration config = plugin.getConfig();
        ConfigurationSection generalSection = config.getConfigurationSection("loot_chest_settings");
        if (generalSection != null) {
            this.spawnRadius = generalSection.getInt("spawn_radius", 30);
            this.spawnIntervalTicks = generalSection.getLong("spawn_interval_ticks", 3600L);
        }

        tieredTemplates.clear();
        ConfigurationSection hierarchySection = config.getConfigurationSection("loot_chest_settings.hierarchy_chests");
        if (hierarchySection != null) {
            for (String tierKey : hierarchySection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(tierKey);
                    List<WeightedTemplate> weightedList = new ArrayList<>();
                    for (Map<?, ?> map : hierarchySection.getMapList(tierKey)) {
                        String templateName = (String) map.get("template");
                        int weight = (int) map.get("weight");
                        LootChestTemplate template = templates.get(templateName);
                        if (template != null) weightedList.add(new WeightedTemplate(template, weight));
                        else plugin.getLogger().warning("LootChest template not found: " + templateName);
                    }
                    tieredTemplates.put(tier, weightedList);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid tier key in config.yml: " + tierKey);
                }
            }
        }
        plugin.getLogger().info("Loaded " + templates.size() + " LootChest templates and " + tieredTemplates.size() + " tier definitions.");
    }

    public void startScheduler() {
        if (spawnIntervalTicks <= 0) return;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) spawnLootChest(player);
        }, 0L, spawnIntervalTicks);
    }

    public void spawnLootChest(Player player) {
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return;
        if (activeLootChests.size() >= 500) return;

        double checkRadius = 32.0;
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.equals(player)) continue;
            if (nearby.getGameMode() != GameMode.SURVIVAL && nearby.getGameMode() != GameMode.ADVENTURE) continue;
            if (nearby.getLocation().distanceSquared(player.getLocation()) < checkRadius * checkRadius) {
                if (nearby.getUniqueId().compareTo(player.getUniqueId()) < 0) return;
            }
        }

        int tier = getTierFromLocation(player.getLocation());
        if (tier == 0) return;

        LootChestTemplate template = selectChestTemplate(tier);
        if (template == null) return;

        Location spawnLoc = findSafeSpawnLocation(player.getLocation());
        if (spawnLoc == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Block block = spawnLoc.getBlock();
            block.setType(Material.CHEST);
            Chest chestState = (Chest) block.getState();
            fillChest(chestState, template);
            registerChest(chestState);
            player.sendMessage(Component.text("ルートチェストが近くに出現しました！", NamedTextColor.GOLD));
        });
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

    private LootChestTemplate selectChestTemplate(int tier) {
        List<WeightedTemplate> list = tieredTemplates.get(tier);
        if (list == null || list.isEmpty()) return null;
        int totalWeight = list.stream().mapToInt(t -> t.weight).sum();
        int roll = random.nextInt(totalWeight);
        for (WeightedTemplate wt : list) {
            roll -= wt.weight;
            if (roll < 0) return wt.template;
        }
        return null;
    }

    private Location findSafeSpawnLocation(Location playerLoc) {
        World world = playerLoc.getWorld();
        if (world == null) return null;
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = random.nextDouble() * spawnRadius + 5;
            double dx = radius * Math.cos(angle);
            double dz = radius * Math.sin(angle);
            Location attemptLoc = playerLoc.clone().add(dx, 0, dz);
            int randomY = random.nextInt(11) - 5;
            attemptLoc.add(0, randomY, 0);
            Block block = attemptLoc.getBlock();
            Block below = block.getRelative(BlockFace.DOWN);
            if (block.getType() == Material.AIR && below.getType().isSolid()) return block.getLocation();
        }
        return null;
    }

    private void fillChest(Chest chestState, LootChestTemplate template) {
        Inventory inv = chestState.getInventory();
        inv.clear();
        int min = template.getMinItems();
        int max = template.getMaxItems();
        int itemSlots = random.nextInt(max - min + 1) + min;
        for (int i = 0; i < itemSlots; i++) {
            double totalChance = template.getTotalChance();
            double roll = random.nextDouble() * totalChance;
            LootEntry selectedEntry = null;
            double currentRoll = roll;
            for (LootEntry entry : template.getEntries()) {
                currentRoll -= entry.getChance();
                if (currentRoll <= 0) {
                    selectedEntry = entry;
                    break;
                }
            }
            if (selectedEntry != null) {
                ItemStack item = selectedEntry.createItem(random);
                if (item != null) {
                    int slot = random.nextInt(inv.getSize());
                    if (inv.getItem(slot) == null) inv.setItem(slot, item);
                }
            }
        }
        if (inv.isEmpty() && !template.getEntries().isEmpty()) {
            LootEntry fallback = template.getEntries().get(random.nextInt(template.getEntries().size()));
            ItemStack item = fallback.createItem(random);
            if (item != null) inv.setItem(random.nextInt(inv.getSize()), item);
        }
    }

    public void registerChest(Chest chest) {
        Location loc = chest.getLocation();
        BukkitTask expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> expireChest(loc), spawnIntervalTicks);
        activeLootChests.put(loc, expireTask);
    }

    public void expireChest(Location loc) {
        BukkitTask task = activeLootChests.remove(loc);
        if (task != null) task.cancel();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (loc.getBlock().getType() == Material.CHEST) {
                loc.getBlock().setType(Material.AIR);
                loc.getWorld().getPlayers().forEach(p -> {
                    if (p.getLocation().distanceSquared(loc) < 25 * 25) {
                        p.sendMessage(Component.text("[情報] ", NamedTextColor.GRAY).append(Component.text("近くのルートチェストが消滅しました。", NamedTextColor.WHITE)));
                    }
                });
            }
        });
    }

    public void despawnLootChest(Location loc) {
        expireChest(loc);
    }

    public boolean isActiveLootChest(Location loc) {
        return activeLootChests.containsKey(loc);
    }

    public void removeAllLootChests() {
        Bukkit.getScheduler().cancelTasks(plugin);
        for (Location loc : activeLootChests.keySet()) {
            BukkitTask task = activeLootChests.get(loc);
            if (task != null) task.cancel();
            Block block = loc.getBlock();
            if (block.getType() == Material.CHEST) block.setType(Material.AIR);
        }
        activeLootChests.clear();
    }

    public void placeDungeonLootChest(Location loc, String templateName) {
        LootChestTemplate template = templates.get(templateName);
        if (template == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Block block = loc.getBlock();
            block.setType(Material.CHEST);
            if (block.getState() instanceof Chest chest) fillChest(chest, template);
        });
    }
}
