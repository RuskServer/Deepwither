package com.lunar_prototype.deepwither.modules.mine;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.LevelManager;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineService implements IManager {

    private static final int DEFAULT_DURABILITY = 4;
    private static final int DEFAULT_RESPAWN_SECONDS = 300;
    private static final int DEFAULT_EXP = 10;
    private static final int DEFAULT_BAR_LENGTH = 4;
    private static final double DEFAULT_DISPLAY_OFFSET_Y = 1.2D;
    private static final int DEFAULT_SUPPRESSION_RADIUS = 8;

    private final Deepwither plugin;
    private final NamespacedKey displayKey;
    private final NamespacedKey positionKey;
    private final Map<BlockPos, OreState> states = new ConcurrentHashMap<>();
    private final Map<BlockPos, BukkitTask> respawnTasks = new ConcurrentHashMap<>();
    private final Map<Material, OreRule> ruleCache = new EnumMap<>(Material.class);
    private final Map<String, Boolean> suppressionCache = new ConcurrentHashMap<>();

    private final MiningSkillService miningSkillService;
    private LevelManager levelManager;
    private ProfessionManager professionManager;
    private ItemFactory itemFactory;

    public MineService(Deepwither plugin, MiningSkillService miningSkillService) {
        this.plugin = plugin;
        this.miningSkillService = miningSkillService;
        this.displayKey = new NamespacedKey(plugin, "mine_display");
        this.positionKey = new NamespacedKey(plugin, "mine_position");
    }

    @Override
    public void init() {
        this.levelManager = plugin.getLevelManager();
        this.professionManager = plugin.getProfessionManager();
        this.itemFactory = plugin.getItemFactory();
        if (levelManager == null) {
            plugin.getLogger().warning("MineService: LevelManager is not available; mining EXP will be skipped.");
        }
        if (professionManager == null) {
            plugin.getLogger().warning("MineService: ProfessionManager is not available; mining profession EXP will be skipped.");
        }
        cleanupOrphanDisplays();
    }

    @Override
    public void shutdown() {
        for (OreState state : states.values()) {
            cancelRespawn(state);
            removeDisplay(state);
        }
        states.clear();
        for (BukkitTask task : respawnTasks.values()) {
            task.cancel();
        }
        respawnTasks.clear();
        suppressionCache.clear();
        cleanupOrphanDisplays();
    }

    public boolean isTrackedOre(Material material) {
        return resolveRule(material) != null;
    }

    public boolean shouldSuppressMobSpawns(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        String key = suppressionKey(location);
        Boolean cached = suppressionCache.get(key);
        if (cached != null) {
            return cached;
        }

        boolean result = hasNearbyOre(location);
        suppressionCache.put(key, result);
        return result;
    }

    public boolean handleMiningAttempt(org.bukkit.entity.Player player, Block block) {
        OreRule rule = resolveRule(block.getType());
        if (rule == null) {
            return false;
        }

        MiningSkillService.MiningProfile profile = miningSkillService.resolveProfile(player);
        MiningSkillService.MiningStrike strike = miningSkillService.resolveStrike(profile);

        BlockPos pos = BlockPos.of(block);
        OreState state = states.compute(pos, (key, existing) -> {
            if (existing == null || existing.material() != block.getType()) {
                return new OreState(pos, block.getType(), rule, rule.durability());
            }
            return existing;
        });

        ensureDisplay(block, state);

        state.remainingDurability = Math.max(0, state.remainingDurability - strike.damage());
        updateDisplay(state);

        if (state.remainingDurability <= 0) {
            playBreakFeedback(block, state, strike.critical());
            completeMine(player, block, pos, state, profile, true);
        } else {
            playDamageFeedback(block, state, strike.critical());
        }
        return true;
    }

    public void releaseState(Block block) {
        BlockPos pos = BlockPos.of(block);
        OreState state = states.remove(pos);
        if (state != null) {
            cancelRespawn(state);
            removeDisplay(state);
        }
        suppressionCache.clear();
        BukkitTask respawnTask = respawnTasks.remove(pos);
        if (respawnTask != null) {
            respawnTask.cancel();
        }
    }

    private void completeMine(org.bukkit.entity.Player player, Block block, BlockPos pos, OreState state,
                              MiningSkillService.MiningProfile profile, boolean allowGeologicalBreak) {
        cancelRespawn(state);
        removeDisplay(state);
        states.remove(pos);
        suppressionCache.clear();

        List<ItemStack> drops = resolveDrops(player, block, state.rule(), profile);
        block.setType(Material.AIR, false);

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
        }

        if (levelManager != null) {
            levelManager.addExp(player, state.rule().exp());
        }
        if (professionManager != null) {
            professionManager.addExp(player, ProfessionType.MINING, state.rule().exp());
        }

        scheduleRespawn(block.getWorld(), pos, state.rule(), state.material());

        if (allowGeologicalBreak) {
            triggerGeologicalBreak(player, block, profile, pos);
        }
    }

    private void playDamageFeedback(Block block, OreState state, boolean critical) {
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        World world = block.getWorld();
        float pitch = 1.0f + (0.12f * (state.rule().durability() - state.remainingDurability));
        if (critical) {
            pitch += 0.25f;
        }

        world.playSound(center, Sound.BLOCK_STONE_HIT, 0.55f, pitch);
        world.spawnParticle(Particle.BLOCK, center, 4, 0.18, 0.18, 0.18, Bukkit.createBlockData(block.getType()));
        if (critical) {
            world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.35f, 1.15f);
            world.spawnParticle(Particle.CRIT, center, 6, 0.2, 0.2, 0.2, 0.08);
        }
    }

    private void playBreakFeedback(Block block, OreState state, boolean critical) {
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        World world = block.getWorld();

        world.playSound(center, Sound.BLOCK_STONE_BREAK, 0.85f, 0.9f);
        world.playSound(center, Sound.BLOCK_ANVIL_LAND, 0.35f, 1.3f);
        world.spawnParticle(Particle.BLOCK, center, 16, 0.25, 0.25, 0.25, Bukkit.createBlockData(block.getType()));
        world.spawnParticle(Particle.CRIT, center, 8, 0.2, 0.2, 0.2, 0.08);
        if (critical) {
            world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 0.9f);
        }
    }

    private List<ItemStack> resolveDrops(org.bukkit.entity.Player player, Block block, OreRule rule,
                                         MiningSkillService.MiningProfile profile) {
        if (!rule.drops().isEmpty() && itemFactory != null) {
            List<ItemStack> drops = new ArrayList<>();
            for (DropDefinition drop : rule.drops()) {
                double chance = drop.chance();
                if (player != null) {
                    chance = miningSkillService.adjustDropChance(profile, chance);
                }
                if (plugin.getRandom().nextDouble() > chance) {
                    continue;
                }
                ItemStack stack = resolveCustomDrop(drop.itemId());
                if (stack != null && !stack.getType().isAir()) {
                    drops.add(stack);
                }
            }
            return drops;
        }

        Collection<ItemStack> vanillaDrops = blockDrops(block);
        return new ArrayList<>(vanillaDrops);
    }

    private Collection<ItemStack> blockDrops(Block block) {
        try {
            return block.getDrops();
        } catch (Exception e) {
            return List.of(new ItemStack(block.getType()));
        }
    }

    private void triggerGeologicalBreak(org.bukkit.entity.Player player, Block sourceBlock,
                                        MiningSkillService.MiningProfile profile, BlockPos sourcePos) {
        if (player == null || profile == null) {
            return;
        }

        MiningSkillService.GeologicalBurst burst = miningSkillService.resolveGeologicalBurst(profile);
        if (!burst.triggered() || burst.maxBlocks() <= 0) {
            return;
        }

        List<Block> nearbyOreBlocks = findNearbyOreBlocks(sourceBlock, burst.radius(), sourcePos);
        int broken = 0;
        for (Block block : nearbyOreBlocks) {
            if (broken >= burst.maxBlocks()) {
                break;
            }
            if (!isTrackedOre(block.getType())) {
                continue;
            }
            if (block.getType().isAir()) {
                continue;
            }

            broken++;
            breakOreInstant(player, block, profile);
        }
    }

    private List<Block> findNearbyOreBlocks(Block sourceBlock, int radius, BlockPos sourcePos) {
        List<Block> result = new ArrayList<>();
        World world = sourceBlock.getWorld();
        int centerX = sourceBlock.getX();
        int centerY = sourceBlock.getY();
        int centerZ = sourceBlock.getZ();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = Math.max(world.getMinHeight(), centerY - radius); y <= Math.min(world.getMaxHeight() - 1, centerY + radius); y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isTrackedOre(block.getType())) {
                        continue;
                    }
                    if (BlockPos.of(block).equals(sourcePos)) {
                        continue;
                    }
                    result.add(block);
                }
            }
        }

        result.sort((left, right) -> Double.compare(distanceSquared(left, sourceBlock), distanceSquared(right, sourceBlock)));
        return result;
    }

    private double distanceSquared(Block first, Block second) {
        double dx = first.getX() - second.getX();
        double dy = first.getY() - second.getY();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void breakOreInstant(org.bukkit.entity.Player player, Block block, MiningSkillService.MiningProfile profile) {
        OreRule rule = resolveRule(block.getType());
        if (rule == null) {
            return;
        }

        BlockPos pos = BlockPos.of(block);
        OreState state = states.compute(pos, (key, existing) -> {
            if (existing == null || existing.material() != block.getType()) {
                return new OreState(pos, block.getType(), rule, rule.durability());
            }
            return existing;
        });

        state.remainingDurability = 0;
        playBreakFeedback(block, state, false);
        completeMine(player, block, pos, state, profile, false);
    }

    private ItemStack resolveCustomDrop(String itemId) {
        if (itemFactory == null) {
            return null;
        }
        ItemStack stack = itemFactory.getCustomItemStack(itemId);
        if (stack == null) {
            plugin.getLogger().warning("MineService: unknown custom drop item_id: " + itemId);
        }
        return stack;
    }

    private void scheduleRespawn(World world, BlockPos pos, OreRule rule, Material material) {
        BukkitTask existing = respawnTasks.remove(pos);
        if (existing != null) {
            existing.cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            respawnTasks.remove(pos);
            Block respawnBlock = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (!respawnBlock.getType().isAir()) {
                return;
            }

            respawnBlock.setType(material, false);
            OreState next = new OreState(pos, material, rule, rule.durability());
            states.put(pos, next);
            suppressionCache.clear();
            ensureDisplay(respawnBlock, next);
        }, rule.respawnTicks());

        respawnTasks.put(pos, task);
    }

    private void ensureDisplay(Block block, OreState state) {
        TextDisplay display = state.displayUuid == null ? null : findDisplay(state.displayUuid);
        if (display == null) {
            display = spawnDisplay(block, state);
            state.displayUuid = display.getUniqueId();
        }

        updateDisplay(display, state);
    }

    private TextDisplay spawnDisplay(Block block, OreState state) {
        Location location = block.getLocation().add(0.5D, DEFAULT_DISPLAY_OFFSET_Y, 0.5D);
        TextDisplay display = block.getWorld().spawn(location, TextDisplay.class, td -> {
            td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setShadowed(true);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setPersistent(true);
            td.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
            td.getPersistentDataContainer().set(positionKey, PersistentDataType.STRING, state.position.serialize());
        });
        return display;
    }

    private void updateDisplay(OreState state) {
        TextDisplay display = state.displayUuid == null ? null : findDisplay(state.displayUuid);
        if (display == null) {
            return;
        }
        updateDisplay(display, state);
    }

    private void updateDisplay(TextDisplay display, OreState state) {
        OreRule rule = state.rule();
        int total = Math.max(1, rule.barLength());
        int remaining = Math.max(0, Math.min(state.remainingDurability, total));
        int filled = Math.max(0, Math.min(remaining, total));

        Component bar = Component.empty();
        for (int i = 0; i < filled; i++) {
            bar = bar.append(Component.text("■", rule.filledColor()));
        }
        for (int i = filled; i < total; i++) {
            bar = bar.append(Component.text("□", rule.emptyColor()));
        }

        display.text(bar);
    }

    private void removeDisplay(OreState state) {
        if (state.displayUuid == null) {
            return;
        }
        TextDisplay display = findDisplay(state.displayUuid);
        if (display != null) {
            display.remove();
        }
        state.displayUuid = null;
    }

    private TextDisplay findDisplay(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        var entity = Bukkit.getEntity(uuid);
        if (entity instanceof TextDisplay display && display.isValid()) {
            return display;
        }
        return null;
    }

    private void cancelRespawn(OreState state) {
        if (state == null) {
            return;
        }
        BukkitTask respawnTask = respawnTasks.remove(state.position);
        if (respawnTask != null) {
            respawnTask.cancel();
        }
    }

    private void cleanupOrphanDisplays() {
        for (World world : plugin.getServer().getWorlds()) {
            for (var entity : world.getEntities()) {
                if (entity instanceof TextDisplay display
                        && display.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    private OreRule resolveRule(Material material) {
        if (material == null || material.isAir()) {
            return null;
        }

        OreRule cached = ruleCache.get(material);
        if (cached != null) {
            return cached;
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ore_setting." + material.name());
        if (section != null) {
            OreRule rule = parseConfiguredRule(material, section);
            ruleCache.put(material, rule);
            return rule;
        }

        if (!isDefaultOreMaterial(material)) {
            return null;
        }

        OreRule rule = defaultRule(material);
        ruleCache.put(material, rule);
        return rule;
    }

    private OreRule parseConfiguredRule(Material material, ConfigurationSection section) {
        int durability = Math.max(1, section.getInt("durability", DEFAULT_DURABILITY));
        int respawnSeconds = Math.max(0, section.getInt("respawn_time", DEFAULT_RESPAWN_SECONDS));
        int exp = Math.max(0, section.getInt("exp", DEFAULT_EXP));
        int barLength = Math.max(1, section.getInt("bar_length", durability));
        NamedTextColor filledColor = parseColor(section.getString("bar_filled_color", "GREEN"), NamedTextColor.GREEN);
        NamedTextColor emptyColor = parseColor(section.getString("bar_empty_color", "DARK_GRAY"), NamedTextColor.DARK_GRAY);

        List<DropDefinition> drops = new ArrayList<>();
        List<Map<?, ?>> dropList = section.getMapList("drops");
        for (Map<?, ?> entry : dropList) {
            String itemId = Objects.toString(entry.get("item_id"), null);
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            double chance = 1.0D;
            Object chanceObj = entry.get("chance");
            if (chanceObj instanceof Number number) {
                chance = Math.max(0.0D, Math.min(1.0D, number.doubleValue()));
            }
            drops.add(new DropDefinition(itemId, chance));
        }

        return new OreRule(material, durability, respawnSeconds * 20L, exp, barLength, filledColor, emptyColor, drops);
    }

    private OreRule defaultRule(Material material) {
        NamedTextColor color = switch (material) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, ANCIENT_DEBRIS -> NamedTextColor.AQUA;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> NamedTextColor.GREEN;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> NamedTextColor.GOLD;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> NamedTextColor.RED;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> NamedTextColor.BLUE;
            case IRON_ORE, DEEPSLATE_IRON_ORE, COPPER_ORE, DEEPSLATE_COPPER_ORE -> NamedTextColor.GRAY;
            default -> NamedTextColor.WHITE;
        };
        return new OreRule(material, DEFAULT_DURABILITY, DEFAULT_RESPAWN_SECONDS * 20L, DEFAULT_EXP,
                DEFAULT_BAR_LENGTH, color, NamedTextColor.DARK_GRAY, List.of());
    }

    private boolean isDefaultOreMaterial(Material material) {
        return material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    private NamedTextColor parseColor(String value, NamedTextColor fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.toUpperCase()) {
            case "BLACK" -> NamedTextColor.BLACK;
            case "DARK_BLUE" -> NamedTextColor.DARK_BLUE;
            case "DARK_GREEN" -> NamedTextColor.DARK_GREEN;
            case "DARK_AQUA" -> NamedTextColor.DARK_AQUA;
            case "DARK_RED" -> NamedTextColor.DARK_RED;
            case "DARK_PURPLE" -> NamedTextColor.DARK_PURPLE;
            case "GOLD" -> NamedTextColor.GOLD;
            case "GRAY" -> NamedTextColor.GRAY;
            case "DARK_GRAY" -> NamedTextColor.DARK_GRAY;
            case "BLUE" -> NamedTextColor.BLUE;
            case "GREEN" -> NamedTextColor.GREEN;
            case "AQUA" -> NamedTextColor.AQUA;
            case "RED" -> NamedTextColor.RED;
            case "LIGHT_PURPLE" -> NamedTextColor.LIGHT_PURPLE;
            case "YELLOW" -> NamedTextColor.YELLOW;
            case "WHITE" -> NamedTextColor.WHITE;
            default -> fallback;
        };
    }

    private boolean hasNearbyOre(Location location) {
        int radius = plugin.getConfig().getInt("mine.mob_spawn_suppression_radius", DEFAULT_SUPPRESSION_RADIUS);
        int minY = Math.max(location.getWorld().getMinHeight(), location.getBlockY() - 3);
        int maxY = Math.min(location.getWorld().getMaxHeight() - 1, location.getBlockY() + 3);

        for (int x = location.getBlockX() - radius; x <= location.getBlockX() + radius; x++) {
            for (int z = location.getBlockZ() - radius; z <= location.getBlockZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Material material = location.getWorld().getBlockAt(x, y, z).getType();
                    if (isTrackedOre(material)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String suppressionKey(Location location) {
        int radius = plugin.getConfig().getInt("mine.mob_spawn_suppression_radius", DEFAULT_SUPPRESSION_RADIUS);
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ() + ":" + radius;
    }

    private record DropDefinition(String itemId, double chance) {}

    private record OreRule(Material material, int durability, long respawnTicks, int exp, int barLength,
                           NamedTextColor filledColor, NamedTextColor emptyColor,
                           List<DropDefinition> drops) {}

    private static final class OreState {
        private final BlockPos position;
        private final Material material;
        private final OreRule rule;
        private int remainingDurability;
        private UUID displayUuid;

        private OreState(BlockPos position, Material material, OreRule rule, int initialDurability) {
            this.position = position;
            this.material = material;
            this.rule = rule;
            this.remainingDurability = initialDurability;
        }

        private Material material() {
            return material;
        }

        private OreRule rule() {
            return rule;
        }
    }

    private record BlockPos(UUID worldId, int x, int y, int z) {
        static BlockPos of(Block block) {
            return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        String serialize() {
            return worldId + ":" + x + ":" + y + ":" + z;
        }
    }
}
