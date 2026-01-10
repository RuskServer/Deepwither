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
import org.bukkit.Bukkit;
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

    // Preserved features
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

            this.minBound = BlockVector3.at(minX, minY, minZ).add(origin);
            this.maxBound = BlockVector3.at(maxX, maxY, maxZ).add(origin);
        }

        private BlockVector3 rotate(int x, int y, int z, int angle) {
            AffineTransform transform = new AffineTransform().rotateY(angle);
            var v = transform.apply(BlockVector3.at(x, y, z).toVector3());
            return BlockVector3.at(Math.round(v.getX()), Math.round(v.getY()), Math.round(v.getZ()));
        }

        public boolean intersects(PlacedPart other) {
            // AABB Collision Check with 3 block buffer to allow wall merging
            return this.minBound.getX() < other.maxBound.getX() - 3 && this.maxBound.getX() > other.minBound.getX() + 3
                    && this.minBound.getY() < other.maxBound.getY() && this.maxBound.getY() > other.minBound.getY()
                    && this.minBound.getZ() < other.maxBound.getZ() - 3
                    && this.maxBound.getZ() > other.minBound.getZ() + 3;
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

        BlockVector3 startOrigin = BlockVector3.at(0, 64, 0);
        DungeonPart startPart = findPartByType("ENTRANCE");
        if (startPart == null) {
            Deepwither.getInstance().getLogger().warning("No ENTRANCE part found!");
            return;
        }

        // The original logic had `finalStartRotation = startRotation + 180;`
        // This assumes startRotation is the player's facing direction and the entrance
        // should face opposite.
        // The previous version had `(startRotation - startPart.getIntrinsicYaw() + 360)
        // % 360;`
        // Reverting to the user's provided snippet's logic for this part.
        int finalStartRotation = startRotation + 180;

        if (pastePart(world, startOrigin, startPart, finalStartRotation, null)) {
            generateRecursive(world, startPart, startOrigin, finalStartRotation, 1, maxDepth, 0);
        }

        Deepwither.getInstance().getLogger().info("=== 生成完了: Placed " + placedParts.size() + " parts ===");
    }

    // Recursive Step
    private void generateRecursive(World world, DungeonPart currentPart, BlockVector3 currentOrigin, int currentRot,
            int depth, int maxDepth, int chainLength) {
        if (depth >= maxDepth) {
            capExits(world, currentPart, currentOrigin, currentRot);
            return;
        }

        List<BlockVector3> rotatedExits = currentPart.getRotatedExitOffsets(currentRot);

        for (int i = 0; i < rotatedExits.size(); i++) {
            BlockVector3 exitOffset = rotatedExits.get(i);
            BlockVector3 connectionPoint = currentOrigin.add(exitOffset);

            BlockVector3 originalExit = currentPart.getExitOffsets().get(i);
            int localExitYaw = currentPart.getExitDirection(originalExit);
            // The original logic had `(localExitYaw + currentRot) % 360;`
            // Reverting to the user's provided snippet's logic for this part.
            int exitWorldYaw = (localExitYaw - currentRot + 360) % 360;

            boolean forceExtend = rotatedExits.size() == 1;
            double chance = forceExtend ? 1.0 : 0.8;
            boolean placedInfo = false;

            if (random.nextDouble() < chance) {
                List<String> typesToTry = new ArrayList<>();
                if (chainLength < 3) {
                    typesToTry.add("HALLWAY");
                    if (random.nextDouble() > 0.8)
                        typesToTry.add("ROOM");
                } else if (chainLength >= 5) {
                    typesToTry.add("ROOM");
                    typesToTry.add("HALLWAY");
                } else {
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
                            // The original logic had `(exitWorldYaw - nextPart.getIntrinsicYaw() + 360) %
                            // 360;`
                            // Reverting to the user's provided snippet's logic for this part.
                            int nextRotation = (nextPart.getIntrinsicYaw() - exitWorldYaw + 360) % 360;
                            BlockVector3 nextEntryRotated = nextPart.getRotatedEntryOffset(nextRotation);
                            BlockVector3 nextOrigin = connectionPoint.subtract(nextEntryRotated);

                            if (pastePart(world, nextOrigin, nextPart, nextRotation, currentOrigin)) {
                                int newChain = type.equals("HALLWAY") ? chainLength + 1 : 0;
                                generateRecursive(world, nextPart, nextOrigin, nextRotation, depth + 1, maxDepth,
                                        newChain);
                                placedInfo = true;
                                break;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (placedInfo)
                        break;
                }
            }

            if (!placedInfo) {
                placeCap(world, connectionPoint, exitWorldYaw, currentOrigin);
            }
        }
    }

    private void capExits(World world, DungeonPart currentPart, BlockVector3 currentOrigin, int currentRot) {
        List<BlockVector3> rotatedExits = currentPart.getRotatedExitOffsets(currentRot);
        for (int i = 0; i < rotatedExits.size(); i++) {
            BlockVector3 exitOffset = rotatedExits.get(i);
            BlockVector3 connectionPoint = currentOrigin.add(exitOffset);
            BlockVector3 originalExit = currentPart.getExitOffsets().get(i);
            int localExitYaw = currentPart.getExitDirection(originalExit);
            // The original logic had `(localExitYaw + currentRot) % 360;`
            // Reverting to the user's provided snippet's logic for this part.
            int exitWorldYaw = (localExitYaw - currentRot + 360) % 360;
            placeCap(world, connectionPoint, exitWorldYaw, currentOrigin);
        }
    }

    private void placeCap(World world, BlockVector3 connectionPoint, int exitWorldYaw, BlockVector3 parentOrigin) {
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
                // The original logic had `(exitWorldYaw - capPart.getIntrinsicYaw() + 360) %
                // 360;`
                // Reverting to the user's provided snippet's logic for this part.
                int baseRotation = (capPart.getIntrinsicYaw() - exitWorldYaw + 360) % 360;
                int nextRotation = (baseRotation + 180) % 360;
                BlockVector3 nextEntryRotated = capPart.getRotatedEntryOffset(nextRotation);
                BlockVector3 nextOrigin = connectionPoint.subtract(nextEntryRotated);

                if (pastePart(world, nextOrigin, capPart, nextRotation, parentOrigin))
                    return;
            }
        }
    }

    private boolean pastePart(World world, BlockVector3 origin, DungeonPart part, int rotation,
            BlockVector3 ignoreOrigin) {
        PlacedPart candidate = new PlacedPart(part, origin, rotation);

        for (PlacedPart existing : placedParts) {
            if (ignoreOrigin != null && existing.origin.equals(ignoreOrigin))
                continue;
            if (candidate.intersects(existing))
                return false;
        }

        File schemFile = new File(dungeonFolder, part.getFileName());
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
        if (format == null)
            return false;

        try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
            Clipboard clipboard = reader.read();
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                // WorldEdit rotateY is CCW. Our rotation is CW.
                // The original logic had `rotateY(-rotation)`.
                // Reverting to the user's provided snippet's logic for this part.
                holder.setTransform(new AffineTransform().rotateY(rotation));
                Operation operation = holder.createPaste(editSession).to(origin).ignoreAirBlocks(true).build();
                Operations.complete(operation);
            }

            BlockVector3 rotatedEntry = part.getRotatedEntryOffset(rotation);
            removeMarker(world, origin.add(rotatedEntry), Material.GOLD_BLOCK);
            for (BlockVector3 exit : part.getRotatedExitOffsets(rotation)) {
                removeMarker(world, origin.add(exit), Material.IRON_BLOCK);
            }

            // Re-integrate Mob/Loot Spawns using restored logic
            for (BlockVector3 mobRel : part.getMobMarkers()) {
                AffineTransform transform = new AffineTransform().rotateY(rotation);
                var rotatedMob = transform.apply(mobRel.toVector3());
                BlockVector3 worldPos = origin.add(Math.round(rotatedMob.getX()), Math.round(rotatedMob.getY()),
                        Math.round(rotatedMob.getZ()));
                removeMarker(world, worldPos, Material.REDSTONE_BLOCK);
                pendingMobSpawns
                        .add(new Location(world, worldPos.getX() + 0.5, worldPos.getY(), worldPos.getZ() + 0.5));
            }
            for (BlockVector3 lootRel : part.getLootMarkers()) {
                AffineTransform transform = new AffineTransform().rotateY(rotation);
                var rotatedLoot = transform.apply(lootRel.toVector3());
                BlockVector3 worldPos = origin.add(Math.round(rotatedLoot.getX()), Math.round(rotatedLoot.getY()),
                        Math.round(rotatedLoot.getZ()));
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

    private void removeMarker(World world, BlockVector3 pos, Material type) {
        // Schedule for next tick to ensure WorldEdit changes are applied
        Bukkit.getScheduler().runTask(Deepwither.getInstance(), () -> {
            org.bukkit.block.Block block = world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            if (block.getType() == type || block.getType() == Material.GOLD_BLOCK
                    || block.getType() == Material.IRON_BLOCK
                    || block.getType() == Material.REDSTONE_BLOCK || block.getType() == Material.EMERALD_BLOCK) {
                block.setType(Material.AIR);
            }
        });
    }

    private DungeonPart findPartByType(String type) {
        List<DungeonPart> valid = partList.stream().filter(p -> p.getType().equals(type)).collect(Collectors.toList());
        if (valid.isEmpty())
            return null;
        return valid.get(random.nextInt(valid.size()));
    }

    public List<Location> getPendingMobSpawns() {
        return pendingMobSpawns;
    }

    public List<Location> getPendingLootSpawns() {
        return pendingLootSpawns;
    }
}