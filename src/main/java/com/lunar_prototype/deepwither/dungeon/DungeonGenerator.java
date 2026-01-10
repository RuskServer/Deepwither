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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Collections;
import java.util.Set;
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
        private final BlockVector3 origin;
        private final int rotation;
        private final BlockVector3 minBound; // World coordinates
        private final BlockVector3 maxBound; // World coordinates

        public PlacedPart(DungeonPart part, BlockVector3 origin, int rotation) {
            this.part = part;
            this.origin = origin;
            this.rotation = rotation;
            // Calculate world bounds based on rotation
            // NOTE: DungeonPart.getMinPoint()/getMaxPoint() are already RELATIVE TO ENTRY
            // (not origin)
            BlockVector3 min = part.getMinPoint();
            BlockVector3 max = part.getMaxPoint();

            // Transform local bounds (relative to Entry) to world bounds
            // Rotate all 8 corners and find min/max for AABB
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

            // Entry offset from schematic origin, rotated
            BlockVector3 rotatedEntry = part.getRotatedEntryOffset(rotation);
            // World position of the Entry point
            BlockVector3 worldEntryPos = origin.add(rotatedEntry);

            // min/max are already relative to Entry, so just add worldEntryPos
            // (DO NOT add rotatedEntry again - it's already accounted for in worldEntryPos)
            this.minBound = BlockVector3.at(minX, minY, minZ).add(worldEntryPos);
            this.maxBound = BlockVector3.at(maxX, maxY, maxZ).add(worldEntryPos);
        }

        private BlockVector3 rotate(int x, int y, int z, int angle) {
            AffineTransform transform = new AffineTransform().rotateY(angle);
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

        if (pastePart(world, startOrigin, startPart, finalStartRotation, new HashSet<>())) {
            // Recurse
            Set<BlockVector3> ancestors = new HashSet<>();
            ancestors.add(startOrigin);
            generateRecursive(world, startPart, startOrigin, finalStartRotation, 1, maxDepth, 0, ancestors);
        }

        Deepwither.getInstance().getLogger().info("=== 生成完了: Placed " + placedParts.size() + " parts ===");
    }

    // Recursive Step
    // Recursive Step
    private void generateRecursive(World world, DungeonPart currentPart, BlockVector3 currentOrigin, int currentRot,
            int depth, int maxDepth, int chainLength, Set<BlockVector3> ancestors) {
        // Check depth limit
        if (depth >= maxDepth) {
            // Cap all exits since we reached max depth
            capExits(world, currentPart, currentOrigin, currentRot, ancestors);
            return;
        }

        List<BlockVector3> rotatedExits = currentPart.getRotatedExitOffsets(currentRot);

        // Process each exit (random shuffle for variety?)
        // Process each exit
        for (int i = 0; i < rotatedExits.size(); i++) {
            BlockVector3 exitOffsetFromEntry = rotatedExits.get(i);

            // Calculate world position of the ENTRY of the current part
            BlockVector3 rotatedEntry = currentPart.getRotatedEntryOffset(currentRot);
            BlockVector3 worldEntryPos = currentOrigin.add(rotatedEntry);

            // Calculate world position of this exit (Connection Point)
            // Since exitOffsetFromEntry is relative to Entry, add it to worldEntryPos
            BlockVector3 connectionPoint = worldEntryPos.add(exitOffsetFromEntry);

            BlockVector3 originalExit = currentPart.getExitOffsets().get(i);
            int localExitYaw = currentPart.getExitDirection(originalExit);

            // Apply current rotation (Clockwise: Local + Rot)
            int exitWorldYaw = (localExitYaw + currentRot) % 360;

            // Force extend if it's the only exit (to prevent premature dead-ends)
            boolean forceExtend = rotatedExits.size() == 1;
            double chance = forceExtend ? 1.0 : 0.8;

            boolean placedInfo = false;

            if (random.nextDouble() < chance) {
                // Determine Types to Try based on Chain Length
                List<String> typesToTry = new ArrayList<>();

                // Chain Logic:
                // Length < 3: Priority HALLWAY (Extend)
                // Length >= 5: Priority ROOM (Branch)
                // Middle: Mixed

                if (chainLength < 3) {
                    typesToTry.add("HALLWAY");
                    if (random.nextDouble() > 0.8)
                        typesToTry.add("ROOM"); // Low chance for room early
                } else if (chainLength >= 5) {
                    typesToTry.add("ROOM");
                    typesToTry.add("HALLWAY"); // Fallback
                } else {
                    // 50/50
                    if (random.nextDouble() > 0.5) {
                        typesToTry.add("ROOM");
                        typesToTry.add("HALLWAY");
                    } else {
                        typesToTry.add("HALLWAY");
                        typesToTry.add("ROOM");
                    }
                }

                for (String type : typesToTry) {
                    List<DungeonPart> candidates = partList.stream()
                            .filter(p -> p.getType().equals(type))
                            .collect(Collectors.toList());

                    if (candidates.isEmpty())
                        continue;

                    Collections.shuffle(candidates);

                    for (DungeonPart nextPart : candidates) {
                        try {
                            // Calculate Rotation: Target - Intrinsic = Rot
                            int nextRotation = (exitWorldYaw - nextPart.getIntrinsicYaw() + 360) % 360;

                            BlockVector3 nextEntryRotated = nextPart.getRotatedEntryOffset(nextRotation);
                            BlockVector3 nextOrigin = connectionPoint.subtract(nextEntryRotated);

                            Deepwither.getInstance().getLogger().info(String.format(
                                    "Trying [%s](%s) at %s Rot:%d | Chain:%d | ExYaw:%d -> TgtYaw:%d",
                                    nextPart.getFileName(), type, nextOrigin, nextRotation, chainLength, localExitYaw,
                                    exitWorldYaw));

                            if (pastePart(world, nextOrigin, nextPart, nextRotation, ancestors)) {
                                int newChain = type.equals("HALLWAY") ? chainLength + 1 : 0;

                                // Create new ancestor set for child (keep last 5 ancestors to allow large
                                // rooms)
                                Set<BlockVector3> nextAncestors = new HashSet<>(ancestors);
                                nextAncestors.add(nextOrigin);
                                // Optional: limit size if needed, but for dungeon branching, keeping direct
                                // branch path ancestors is fine

                                generateRecursive(world, nextPart, nextOrigin, nextRotation, depth + 1, maxDepth,
                                        newChain, nextAncestors);
                                placedInfo = true;
                                break; // Break candidate loop
                            }
                        } catch (Exception e) {
                            Deepwither.getInstance().getLogger()
                                    .warning("Error trying to place part " + nextPart.getFileName());
                            e.printStackTrace();
                        }
                    }
                    if (placedInfo)
                        break; // Break type loop if placed
                }
            }

            // If failed to place anything (Collision or Change skipped), Cap it.
            if (!placedInfo) {
                placeCap(world, connectionPoint, exitWorldYaw, ancestors);
            }
        }
    }

    private void capExits(World world, DungeonPart currentPart, BlockVector3 currentOrigin, int currentRot,
            Set<BlockVector3> ancestors) {
        List<BlockVector3> rotatedExits = currentPart.getRotatedExitOffsets(currentRot);
        for (int i = 0; i < rotatedExits.size(); i++) {
            BlockVector3 exitOffset = rotatedExits.get(i);
            BlockVector3 connectionPoint = currentOrigin.add(exitOffset);

            BlockVector3 originalExit = currentPart.getExitOffsets().get(i);
            int localExitYaw = currentPart.getExitDirection(originalExit);
            int exitWorldYaw = (localExitYaw - currentRot + 360) % 360;

            placeCap(world, connectionPoint, exitWorldYaw, ancestors);
        }
    }

    private void placeCap(World world, BlockVector3 connectionPoint, int exitWorldYaw, Set<BlockVector3> ancestors) {
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
                // Align Cap Intrinsic Yaw to Exit World Yaw
                // Default: Match flow direction (Parent Exit -> Child Entry -> Child Exit)
                int baseRotation = (capPart.getIntrinsicYaw() - exitWorldYaw + 360) % 360;

                // Fix for Cap: Rotate 180 degrees to face 'inwards' or block correctly?
                // Based on user feedback that rotation is wrong, attempting 180 flip.
                int nextRotation = (baseRotation + 180) % 360;

                BlockVector3 nextEntryRotated = capPart.getRotatedEntryOffset(nextRotation);
                BlockVector3 nextOrigin = connectionPoint.subtract(nextEntryRotated);

                Deepwither.getInstance().getLogger()
                        .info(String.format("Attempting CAP [%s] at %s | ExYaw:%d IntYaw:%d -> BaseRot:%d FinalRot:%d",
                                capPart.getFileName(), connectionPoint, exitWorldYaw, capPart.getIntrinsicYaw(),
                                baseRotation, nextRotation));

                if (pastePart(world, nextOrigin, capPart, nextRotation, ancestors)) {
                    Deepwither.getInstance().getLogger().info("Placed CAP at " + connectionPoint);
                    return; // Capped successfully
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

    private boolean isCollision(BlockVector3 min, BlockVector3 max, Set<BlockVector3> ignoreOrigins) {
        // Shrink slightly in X and Z to allow touching faces (2 block buffer for more
        // tolerance)
        int testMinX = min.getX() + 2;
        int testMaxX = max.getX() - 2;
        int testMinZ = min.getZ() + 2;
        int testMaxZ = max.getZ() - 2;

        if (testMinX > testMaxX || testMinZ > testMaxZ) {
            // Region too small, just check center point
            int midX = (min.getX() + max.getX()) / 2;
            int midZ = (min.getZ() + max.getZ()) / 2;
            testMinX = testMaxX = midX;
            testMinZ = testMaxZ = midZ;
        }

        Deepwither.getInstance().getLogger().info(String.format(
                "  [Collision Check] TestBounds: X[%d,%d] Z[%d,%d] | OrigBounds: %s to %s",
                testMinX, testMaxX, testMinZ, testMaxZ, min, max));

        for (PlacedPart existing : placedParts) {
            // Ignore the ancestors to allow seamless connection and large parts
            if (ignoreOrigins != null && ignoreOrigins.contains(existing.origin)) {
                continue;
            }

            // AABB Collision Check
            boolean overlapX = testMinX <= existing.maxBound.getX() && testMaxX >= existing.minBound.getX();
            boolean overlapY = min.getY() <= existing.maxBound.getY() && max.getY() >= existing.minBound.getY();
            boolean overlapZ = testMinZ <= existing.maxBound.getZ() && testMaxZ >= existing.minBound.getZ();

            if (overlapX && overlapY && overlapZ) {
                Deepwither.getInstance().getLogger().info(String.format(
                        "  [Collision] HIT with [%s] at %s | Existing Bounds: %s to %s",
                        existing.part.getFileName(), existing.origin, existing.minBound, existing.maxBound));
                return true;
            }
        }
        return false;
    }

    private boolean pastePart(World world, BlockVector3 origin, DungeonPart part, int rotation,
            Set<BlockVector3> ignoreOrigins) {
        // 1. Calculate world bounds for this part
        PlacedPart candidate = new PlacedPart(part, origin, rotation);

        // 2. Check overlap using shrunken 'interior' bounds
        if (isCollision(candidate.minBound, candidate.maxBound, ignoreOrigins)) {
            Deepwither.getInstance().getLogger().info("Collision detected at " + origin);
            return false;
        }

        // 3. Paste
        File schemFile = new File(dungeonFolder, part.getFileName());
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
        if (format == null)
            return false;

        try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
            Clipboard clipboard = reader.read();

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                holder.setTransform(new AffineTransform().rotateY(rotation));

                Operation operation = holder
                        .createPaste(editSession)
                        .to(origin)
                        .ignoreAirBlocks(true)
                        .build();
                Operations.complete(operation);
            }

            // Remove Markers logic...
            BlockVector3 rotatedEntry = part.getRotatedEntryOffset(rotation);
            BlockVector3 worldEntryPos = origin.add(rotatedEntry);
            removeMarker(world, worldEntryPos, Material.GOLD_BLOCK);

            for (BlockVector3 exit : part.getRotatedExitOffsets(rotation)) {
                // Exit is now relative to Entry, so add to worldEntryPos
                removeMarker(world, worldEntryPos.add(exit), Material.IRON_BLOCK);
            }

            // Process Mob Markers
            // Markers are now relative to Entry in DungeonPart
            for (BlockVector3 mobMarkerRelToEntry : part.getMobMarkers()) {
                BlockVector3 worldPos = worldEntryPos.add(rotateVector(mobMarkerRelToEntry, rotation));
                removeMarker(world, worldPos, Material.REDSTONE_BLOCK);
                pendingMobSpawns
                        .add(new Location(world, worldPos.getX() + 0.5, worldPos.getY(), worldPos.getZ() + 0.5));
                Deepwither.getInstance().getLogger().info("Added Mob Spawn at " + worldPos);
            }

            // Process Loot Markers
            // Markers are now relative to Entry in DungeonPart
            for (BlockVector3 lootMarkerRelToEntry : part.getLootMarkers()) {
                BlockVector3 worldPos = worldEntryPos.add(rotateVector(lootMarkerRelToEntry, rotation));
                removeMarker(world, worldPos, Material.EMERALD_BLOCK);
                pendingLootSpawns.add(new Location(world, worldPos.getX(), worldPos.getY(), worldPos.getZ()));
                Deepwither.getInstance().getLogger().info("Added Loot Spawn at " + worldPos);
            }

            placedParts.add(candidate);
            Deepwither.getInstance().getLogger().info("Placed " + part.getType() + " at " + origin);
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
    private BlockVector3 rotateVector(BlockVector3 vec, int angle) {
        AffineTransform transform = new AffineTransform().rotateY(angle);
        var v = transform.apply(vec.toVector3());
        return BlockVector3.at(Math.round(v.getX()), Math.round(v.getY()), Math.round(v.getZ()));
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