package com.lunar_prototype.deepwither.dungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Collections;
import java.util.stream.Collectors;

public class DungeonGenerator {
    private final String dungeonName;
    private final List<DungeonPart> partList = new ArrayList<>();
    private final File dungeonFolder;
    private final Random random = new Random();

    // Store placed parts for collision detection
    private final List<PlacedPart> placedParts = new ArrayList<>();

    // Configuration & Spawns
    private int maxDepth = 10;
    private final List<String> mobIds = new ArrayList<>();
    private String lootChestTemplate = null;

    private final List<Location> pendingMobSpawns = new ArrayList<>();
    private final List<Location> pendingLootSpawns = new ArrayList<>();

    private static class PlacedPart {
        private final DungeonPart part;
        private final BlockVector3 origin; // World Entry Position
        private final int rotation;
        private final BlockVector3 minBound; // World coordinates
        private final BlockVector3 maxBound; // World coordinates

        public PlacedPart(DungeonPart part, BlockVector3 worldEntryPos, int rotation) {
            this.part = part;
            this.origin = worldEntryPos;
            this.rotation = rotation;

            // Calculate world bounds based on rotation around Entry (0,0,0 local)
            BlockVector3 min = part.getMinPoint();
            BlockVector3 max = part.getMaxPoint();

            List<BlockVector3> corners = new ArrayList<>();
            corners.add(rotate(min.getX(), min.getY(), min.getZ(), rotation));
            corners.add(rotate(min.getX(), min.getY(), max.getZ(), rotation));
            corners.add(rotate(min.getX(), max.getY(), min.getZ(), rotation));
            corners.add(rotate(min.getX(), max.getY(), max.getZ(), rotation));
            corners.add(rotate(max.getX(), min.getY(), min.getZ(), rotation));
            corners.add(rotate(max.getX(), min.getY(), max.getZ(), rotation));
            corners.add(rotate(max.getX(), max.getY(), min.getZ(), rotation));
            corners.add(rotate(max.getX(), max.getY(), max.getZ(), rotation));

            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

            for (BlockVector3 v : corners) {
                minX = Math.min(minX, v.getX());
                minY = Math.min(minY, v.getY());
                minZ = Math.min(minZ, v.getZ());
                maxX = Math.max(maxX, v.getX());
                maxY = Math.max(maxY, v.getY());
                maxZ = Math.max(maxZ, v.getZ());
            }

            this.minBound = BlockVector3.at(minX, minY, minZ).add(worldEntryPos);
            this.maxBound = BlockVector3.at(maxX, maxY, maxZ).add(worldEntryPos);
        }

        private BlockVector3 rotate(int x, int y, int z, int angle) {
            int normalizedAngle = angle % 360;
            if (normalizedAngle < 0)
                normalizedAngle += 360;
            AffineTransform transform = new AffineTransform().rotateY(-normalizedAngle);
            var v = transform.apply(BlockVector3.at(x, y, z).toVector3());
            return BlockVector3.at(Math.round(v.getX()), Math.round(v.getY()), Math.round(v.getZ()));
        }

        public Region getRegion() {
            return new CuboidRegion(BukkitAdapter.adapt(Deepwither.getInstance().getServer().getWorlds().get(0)),
                    minBound, maxBound);
        }
    }

    public DungeonGenerator(String dungeonName) {
        this.dungeonName = dungeonName;
        this.dungeonFolder = new File(Deepwither.getInstance().getDataFolder(), "dungeons/" + dungeonName);
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(Deepwither.getInstance().getDataFolder(), "dungeons/" + dungeonName + ".yml");
        if (!configFile.exists()) {
            Deepwither.getInstance().getLogger().severe("Config not found: " + configFile.getAbsolutePath());
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        this.maxDepth = config.getInt("max_depth", 10);
        this.lootChestTemplate = config.getString("loot_chest");
        this.mobIds.clear();
        this.mobIds.addAll(config.getStringList("mobs"));

        List<Map<?, ?>> maps = config.getMapList("parts");

        for (Map<?, ?> rawMap : maps) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;

            int length = ((Number) map.getOrDefault("length", 10)).intValue();
            String fileName = (String) map.get("file");
            String type = (String) map.get("type");

            if (fileName != null && type != null) {
                DungeonPart part = new DungeonPart(fileName, type.toUpperCase(), length);

                File schemFile = new File(dungeonFolder, fileName);
                if (schemFile.exists()) {
                    scanPartMarkers(part, schemFile);
                } else {
                    Deepwither.getInstance().getLogger().warning("Schematic file not found: " + fileName);
                }

                partList.add(part);
            }
        }
    }

    private void scanPartMarkers(DungeonPart part, File file) {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null)
            return;

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            part.scanMarkers(clipboard);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 新しい分岐生成メソッド (再帰的)
     * Configから読み込んだ maxDepth を使用
     */
    public void generateBranching(World world, int startRotation) {
        generateBranching(world, this.maxDepth, startRotation);
    }

    /**
     * 新しい分岐生成メソッド (再帰的)
     */
    public void generateBranching(World world, int maxDepth, int startRotation) {
        placedParts.clear();
        pendingMobSpawns.clear();
        pendingLootSpawns.clear();
        Deepwither.getInstance().getLogger().info("=== 生成開始: Branching Dungeon (MaxDepth:" + maxDepth + ") ===");

        // 基準点
        BlockVector3 startOrigin = BlockVector3.at(0, 64, 0);

        // 1. START Placement
        DungeonPart startPart = findPartByType("ENTRANCE"); // Or reuse ENTRANCE as start
        if (startPart == null) {
            Deepwither.getInstance().getLogger().warning("No ENTRANCE part found!");
            return;
        }

        // Place Start (Assume safe/no collision at origin)
        // Adjust start rotation to face the desired direction?
        // Typically ENTRANCE exits to +Z (South), so Rotation 0 -> Exits South.
        // If we want it to face South, we use startRotation + 180 (similar to straight
        // gen pivot)
        // Let's stick to straight gen logic: first part is rotated 180 to align?
        // Actually, let's just place it at Rotation 0 and assume standard flow.

        // For 'straight' gen compat:
        int finalStartRotation = startRotation + 180;

        List<PlacedPart> ignoreParts = new ArrayList<>();
        if (pastePart(world, startOrigin, startPart, finalStartRotation, ignoreParts)) {
            // Recurse
            PlacedPart firstPlaced = placedParts.get(placedParts.size() - 1);
            List<PlacedPart> nextIgnore = new ArrayList<>();
            nextIgnore.add(firstPlaced);
            generateRecursive(world, startPart, startOrigin, finalStartRotation, 1, maxDepth, 0, nextIgnore);
        }

        Deepwither.getInstance().getLogger().info("=== 生成完了: Placed " + placedParts.size() + " parts ===");
    }

    // Recursive Step
    // Recursive Step
    private void generateRecursive(World world, DungeonPart currentPart, BlockVector3 currentEntry, int currentRot,
            int depth, int maxDepth, int chainLength, List<PlacedPart> ignoreParts) {
        if (depth >= maxDepth) {
            capExits(world, currentPart, currentEntry, currentRot, ignoreParts);
            return;
        }

        List<BlockVector3> rotatedExits = currentPart.getRotatedExitOffsets(currentRot);

        for (int i = 0; i < rotatedExits.size(); i++) {
            BlockVector3 exitOffsetRelToEntry = rotatedExits.get(i);
            BlockVector3 connectionPoint = currentEntry.add(exitOffsetRelToEntry);

            BlockVector3 originalExit = currentPart.getExitOffsets().get(i);
            int localExitYaw = currentPart.getExitDirection(originalExit);
            int exitWorldYaw = (localExitYaw + currentRot) % 360;

            boolean forceExtend = rotatedExits.size() == 1;
            double chance = forceExtend ? 1.0 : 0.8;
            boolean placedInfo = false;

            if (random.nextDouble() < chance) {
                List<String> typesToTry = new ArrayList<>();
                if (currentPart.getType().equals("ROOM")) {
                    typesToTry.add("HALLWAY");
                } else if (chainLength < 2) { // Shorter chain for room check
                    typesToTry.add("HALLWAY");
                    if (random.nextDouble() > 0.7)
                        typesToTry.add("ROOM");
                } else {
                    typesToTry.add("ROOM");
                    typesToTry.add("HALLWAY");
                }

                for (String type : typesToTry) {
                    List<DungeonPart> candidates = partList.stream()
                            .filter(p -> p.getType().equals(type))
                            .collect(Collectors.toList());
                    if (candidates.isEmpty())
                        continue;
                    Collections.shuffle(candidates);

                    for (DungeonPart nextPart : candidates) {
                        int nextRotation = (exitWorldYaw - nextPart.getIntrinsicYaw() + 360) % 360;
                        if (pastePart(world, connectionPoint, nextPart, nextRotation, ignoreParts)) {
                            int newChain = type.equals("HALLWAY") ? chainLength + 1 : 0;

                            // Last placed part is at the end of placedParts
                            PlacedPart lastPlaced = placedParts.get(placedParts.size() - 1);

                            // Maintain ignoreParts list (parent and possibly grandparent)
                            List<PlacedPart> nextIgnore = new ArrayList<>();
                            nextIgnore.add(lastPlaced);
                            if (!ignoreParts.isEmpty()) {
                                nextIgnore.add(ignoreParts.get(0)); // Only keep 1 ancestor (parent) to make it 2 total
                            }

                            generateRecursive(world, nextPart, connectionPoint, nextRotation, depth + 1, maxDepth,
                                    newChain, nextIgnore);
                            placedInfo = true;
                            break;
                        }
                    }
                    if (placedInfo)
                        break;
                }
            }

            if (!placedInfo) {
                placeCap(world, connectionPoint, exitWorldYaw, ignoreParts);
            }
        }
    }

    private void capExits(World world, DungeonPart currentPart, BlockVector3 currentEntry, int currentRot,
            List<PlacedPart> ignoreParts) {
        List<BlockVector3> rotatedExits = currentPart.getRotatedExitOffsets(currentRot);
        for (int i = 0; i < rotatedExits.size(); i++) {
            BlockVector3 rotExit = rotatedExits.get(i);
            BlockVector3 connectionPoint = currentEntry.add(rotExit);

            BlockVector3 originalExit = currentPart.getExitOffsets().get(i);
            int localExitYaw = currentPart.getExitDirection(originalExit);
            int exitWorldYaw = (localExitYaw + currentRot) % 360;

            placeCap(world, connectionPoint, exitWorldYaw, ignoreParts);
        }
    }

    private void placeCap(World world, BlockVector3 connectionPoint, int exitWorldYaw, List<PlacedPart> ignoreParts) {
        // Try CAP then ENTRANCE (as fallback)
        List<String> capTypes = new ArrayList<>();
        capTypes.add("CAP");
        capTypes.add("ENTRANCE");

        for (String type : capTypes) {
            List<DungeonPart> candidates = partList.stream()
                    .filter(p -> p.getType().equals(type))
                    .collect(Collectors.toList());
            if (candidates.isEmpty())
                continue;

            Collections.shuffle(candidates);

            for (DungeonPart capPart : candidates) {
                int nextRotation = (exitWorldYaw - capPart.getIntrinsicYaw() + 360) % 360;

                if (pastePart(world, connectionPoint, capPart, nextRotation, ignoreParts)) {
                    Deepwither.getInstance().getLogger().info("Placed CAP at " + connectionPoint);
                    return; // Success
                }
            }
        }
        Deepwither.getInstance().getLogger().info("Failed to CAP at " + connectionPoint);
    }

    private int getVectorYaw(int x, int z) {
        if (Math.abs(x) > Math.abs(z)) {
            return (x > 0) ? 270 : 90; // +X=East=270, -X=West=90
        } else {
            return (z > 0) ? 0 : 180; // +Z=South=0, -Z=North=180
        }
    }

    private boolean isCollision(BlockVector3 min, BlockVector3 max, List<PlacedPart> ignoreParts) {
        // Shrink slightly in X and Z
        int testMinX = min.getX() + 1;
        int testMaxX = max.getX() - 1;
        int testMinZ = min.getZ() + 1;
        int testMaxZ = max.getZ() - 1;

        if (testMinX > testMaxX || testMinZ > testMaxZ) {
            int midX = (min.getX() + max.getX()) / 2;
            int midZ = (min.getZ() + max.getZ()) / 2;
            testMinX = testMaxX = midX;
            testMinZ = testMaxZ = midZ;
        }

        for (PlacedPart existing : placedParts) {
            if (ignoreParts != null && ignoreParts.contains(existing))
                continue;

            boolean overlapX = testMinX <= existing.maxBound.getX() && testMaxX >= existing.minBound.getX();
            boolean overlapY = min.getY() <= existing.maxBound.getY() && max.getY() >= existing.minBound.getY();
            boolean overlapZ = testMinZ <= existing.maxBound.getZ() && testMaxZ >= existing.minBound.getZ();

            if (overlapX && overlapY && overlapZ)
                return true;
        }
        return false;
    }

    private boolean pastePart(World world, BlockVector3 entryPos, DungeonPart part, int rotation,
            List<PlacedPart> ignoreParts) {
        PlacedPart candidate = new PlacedPart(part, entryPos, rotation);
        if (isCollision(candidate.minBound, candidate.maxBound, ignoreParts))
            return false;

        File schemFile = new File(dungeonFolder, part.getFileName());
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
        if (format == null)
            return false;

        try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
            Clipboard clipboard = reader.read();
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                holder.setTransform(new AffineTransform().rotateY(-rotation));

                BlockVector3 rotatedOriginOffset = part.getRotatedOriginOffset(rotation);
                BlockVector3 pastePos = entryPos.add(rotatedOriginOffset);

                Operation operation = holder.createPaste(editSession).to(pastePos).ignoreAirBlocks(true).build();
                Operations.complete(operation);
            }

            removeMarker(world, entryPos, Material.GOLD_BLOCK);
            for (BlockVector3 exit : part.getRotatedExitOffsets(rotation)) {
                removeMarker(world, entryPos.add(exit), Material.IRON_BLOCK);
            }
            for (BlockVector3 mobRel : part.getMobMarkers()) {
                BlockVector3 rotMob = part.transformVector(mobRel, rotation);
                BlockVector3 worldPos = entryPos.add(rotMob);
                removeMarker(world, worldPos, Material.REDSTONE_BLOCK);
                pendingMobSpawns
                        .add(new Location(world, worldPos.getX() + 0.5, worldPos.getY(), worldPos.getZ() + 0.5));
            }
            for (BlockVector3 lootRel : part.getLootMarkers()) {
                BlockVector3 rotLoot = part.transformVector(lootRel, rotation);
                BlockVector3 worldPos = entryPos.add(rotLoot);
                removeMarker(world, worldPos, Material.EMERALD_BLOCK);
                pendingLootSpawns.add(new Location(world, worldPos.getX(), worldPos.getY(), worldPos.getZ()));
            }

            placedParts.add(candidate);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Deprecated but kept for compatibility/testing single paths
    public void generateStraight(World world, int hallwayCount, int rotation) {
        generateBranching(world, hallwayCount, rotation);
    }

    private void removeMarker(World world, BlockVector3 pos, Material expectedType) {
        // Schedule for next tick to ensure WorldEdit changes are applied
        Bukkit.getScheduler().runTask(Deepwither.getInstance(), () -> {
            Location loc = new Location(world, pos.getX(), pos.getY(), pos.getZ());
            Material currentType = loc.getBlock().getType();

            // At connections, GOLD and IRON often overlap and override each other.
            // GOLD, IRON, REDSTONE, EMERALD are all markers that should be removed.
            boolean isMarker = currentType == Material.GOLD_BLOCK || currentType == Material.IRON_BLOCK ||
                    currentType == Material.REDSTONE_BLOCK || currentType == Material.EMERALD_BLOCK;

            if (isMarker) {
                loc.getBlock().setType(Material.AIR);
                Deepwither.getInstance().getLogger().info("Removed marker " + currentType + " at " + pos);
            }
            // else: silently skip, could be already removed or overridden by a structural
            // block
        });
    }

    private DungeonPart findPartByType(String type) {
        // Randomize
        List<DungeonPart> valid = partList.stream()
                .filter(p -> p.getType().equals(type))
                .collect(Collectors.toList());
        if (valid.isEmpty())
            return null;
        return valid.get(random.nextInt(valid.size()));
    }

    // Helpers
    private BlockVector3 transformVector(BlockVector3 vec, int angle) {
        if (vec == null)
            return BlockVector3.ZERO;

        int normalizedAngle = angle % 360;
        if (normalizedAngle < 0)
            normalizedAngle += 360;

        // Convert Clockwise (Minecraft Yaw) to Counter-Clockwise (WorldEdit
        // AffineTransform)
        int weAngle = (360 - normalizedAngle) % 360;
        AffineTransform transform = new AffineTransform().rotateY(weAngle);
        var v3 = transform.apply(vec.toVector3());
        return BlockVector3.at(Math.round(v3.getX()), Math.round(v3.getY()), Math.round(v3.getZ()));
    }

    public List<Location> getPendingMobSpawns() {
        return pendingMobSpawns;
    }

    public List<Location> getPendingLootSpawns() {
        return pendingLootSpawns;
    }

    public List<String> getMobIds() {
        return mobIds;
    }

    public String getLootChestTemplate() {
        return lootChestTemplate;
    }
}