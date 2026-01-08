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
import com.sk89q.worldedit.session.ClipboardHolder;
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
import java.util.stream.Collectors;

public class DungeonGenerator {
    private final String dungeonName;
    private final List<DungeonPart> partList = new ArrayList<>();
    private final File dungeonFolder;
    private final Random random = new Random();

    // Store placed parts for collision detection
    private final List<PlacedPart> placedParts = new ArrayList<>();

    private static class PlacedPart {
        DungeonPart part;
        BlockVector3 minBound; // World coordinates
        BlockVector3 maxBound; // World coordinates

        public PlacedPart(DungeonPart part, BlockVector3 origin, int rotation) {
            this.part = part;
            // Calculate world bounds based on rotation
            BlockVector3 min = part.getMinPoint();
            BlockVector3 max = part.getMaxPoint();

            // Transform local bounds to world bounds
            // This is a simplified bounding box calculation (AABB)
            // For precise collision, we'd need to rotate all 8 corners and find min/max
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

            this.minBound = BlockVector3.at(minX, minY, minZ).add(origin);
            this.maxBound = BlockVector3.at(maxX, maxY, maxZ).add(origin);
        }

        private BlockVector3 rotate(int x, int y, int z, int angle) {
            AffineTransform transform = new AffineTransform().rotateY(angle);
            var v = transform.apply(BlockVector3.at(x, y, z).toVector3());
            return BlockVector3.at(Math.round(v.getX()), Math.round(v.getY()), Math.round(v.getZ()));
        }

        public boolean intersects(PlacedPart other) {
            // AABB Collision Check with 1 block buffer to prevent tight touching
            return this.minBound.getX() < other.maxBound.getX() - 1 && this.maxBound.getX() > other.minBound.getX() + 1
                    &&
                    this.minBound.getY() < other.maxBound.getY() && this.maxBound.getY() > other.minBound.getY() &&
                    this.minBound.getZ() < other.maxBound.getZ() - 1
                    && this.maxBound.getZ() > other.minBound.getZ() + 1;
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
     */
    public void generateBranching(World world, int maxDepth, int startRotation) {
        placedParts.clear();
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

        if (pastePart(world, startOrigin, startPart, finalStartRotation)) {
            // Recurse
            generateRecursive(world, startPart, startOrigin, finalStartRotation, 1, maxDepth);
        }

        Deepwither.getInstance().getLogger().info("=== 生成完了: Placed " + placedParts.size() + " parts ===");
    }

    // Recursive Step
    private void generateRecursive(World world, DungeonPart currentPart, BlockVector3 currentOrigin, int currentRot,
            int depth, int maxDepth) {
        if (depth >= maxDepth)
            return;

        List<BlockVector3> rotatedExits = currentPart.getRotatedExitOffsets(currentRot);

        // Process each exit (random shuffle for variety?)
        for (BlockVector3 exitOffset : rotatedExits) {
            // Calculate world position of this exit (Connection Point)
            BlockVector3 connectionPoint = currentOrigin.add(exitOffset);

            // 1. Determine World Direction of this Exit
            // We use the ROTATED offset to see where it points in the world.
            // +Z=South, -X=West, etc.
            // Map Vector to Yaw(0=South, 90=West, 180=North, 270=East)
            int exitWorldYaw = getVectorYaw(exitOffset.getX(), exitOffset.getZ());

            // Try to place a part
            if (random.nextDouble() < 0.8) {
                // Select random Hallway or Room
                // String type = (random.nextDouble() > 0.7) ? "ROOM" : "HALLWAY";
                // Only try to place Hallways first to keep it simple? No, mixed.
                String type = (random.nextDouble() > 0.5) ? "ROOM" : "HALLWAY";

                List<DungeonPart> candidates = partList.stream()
                        .filter(p -> p.getType().equals(type))
                        .collect(Collectors.toList());

                if (candidates.isEmpty()) {
                    continue;
                }

                DungeonPart nextPart = candidates.get(random.nextInt(candidates.size()));

                // 2. Calculate Required Rotation
                // We want NextPart.IntrinsicYaw to align with exitWorldYaw.
                // WE Rotation is additive?
                // RotatedVec = Rot(IntrinsicVec).
                // We want Rot(Intrinsic) = Target(ExitWorld).
                // So Rot = Target - Intrinsic.

                int nextRotation = (exitWorldYaw - nextPart.getIntrinsicYaw() + 360) % 360;

                // Calculate Origin for Next Part
                BlockVector3 nextEntryRotated = nextPart.getRotatedEntryOffset(nextRotation);
                BlockVector3 nextOrigin = connectionPoint.subtract(nextEntryRotated);

                // Try paste
                if (pastePart(world, nextOrigin, nextPart, nextRotation)) {
                    // Success, recurse
                    generateRecursive(world, nextPart, nextOrigin, nextRotation, depth + 1, maxDepth);
                }
            }
        }
    }

    private int getVectorYaw(int x, int z) {
        if (Math.abs(x) > Math.abs(z)) {
            return (x > 0) ? 270 : 90; // +X=East=270, -X=West=90
        } else {
            return (z > 0) ? 0 : 180; // +Z=South=0, -Z=North=180
        }
    }

    private boolean pastePart(World world, BlockVector3 origin, DungeonPart part, int rotation) {
        // 1. Create Collision Box Candidate
        PlacedPart candidate = new PlacedPart(part, origin, rotation);

        // 2. Check overlap
        for (PlacedPart existing : placedParts) {
            if (candidate.intersects(existing)) {
                Deepwither.getInstance().getLogger().info("Collision detected at " + origin);
                return false;
            }
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
            removeMarker(world, origin.add(rotatedEntry), Material.GOLD_BLOCK);

            for (BlockVector3 exit : part.getRotatedExitOffsets(rotation)) {
                removeMarker(world, origin.add(exit), Material.IRON_BLOCK);
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
        Location loc = new Location(world, pos.getX(), pos.getY(), pos.getZ());
        if (loc.getBlock().getType() == expectedType) {
            loc.getBlock().setType(Material.AIR);
        }
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
}