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
import java.util.Collections;
import java.util.stream.Collectors;

public class DungeonGenerator {
    private final String dungeonName;
    private final List<DungeonPart> partList = new ArrayList<>();
    private final File dungeonFolder;
    private final String difficulty;
    private int mobLevel;
    private final Random random = new Random();

    // Store placed parts for collision detection
    private final List<PlacedPart> placedParts = new ArrayList<>();

    private final List<String> dungeonMobList = new ArrayList<>();
    private final List<PendingSpawner> pendingSpawners = new ArrayList<>();
    private boolean isMonitoring = false;

    private int maxDepth = 10;

    private String lootChestId;

    private static class PendingSpawner {
        private final Location location;
        private final String mobId;
        private final int level;

        public PendingSpawner(Location location, String mobId, int level) {
            this.location = location;
            this.mobId = mobId;
            this.level = level;
        }
    }

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
            // AABB Collision Check with 2 block buffer to allow wall merging
            // Relaxed from 1 to 2 based on user feedback (too sensitive)
            return this.minBound.getX() < other.maxBound.getX() - 3 && this.maxBound.getX() > other.minBound.getX() + 3
                    &&
                    this.minBound.getY() < other.maxBound.getY() && this.maxBound.getY() > other.minBound.getY() &&
                    this.minBound.getZ() < other.maxBound.getZ() - 3
                    && this.maxBound.getZ() > other.minBound.getZ() + 3;
        }
    }

    public DungeonGenerator(String dungeonName, String difficulty) {
        this.dungeonName = dungeonName;
        this.difficulty = difficulty;
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

        List<String> mobs = config.getStringList("mobs");
        this.dungeonMobList.clear();
        this.dungeonMobList.addAll(mobs);

        this.maxDepth = config.getInt("max_depth", 10);

        this.mobLevel = config.getInt("difficulty." + difficulty + ".mob_level", 1);
        this.lootChestId = config.getString("difficulty." + difficulty + ".loot_id", "common_loot_chest");

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

        if (pastePart(world, startOrigin, startPart, finalStartRotation, null)) {
            // Recurse
            generateRecursive(world, startPart, startOrigin, finalStartRotation, 1, maxDepth, 0);
        }

        Deepwither.getInstance().getLogger().info("=== 生成完了: Placed " + placedParts.size() + " parts ===");
    }

    // Recursive Step
    private void generateRecursive(World world, DungeonPart currentPart, BlockVector3 currentOrigin, int currentRot,
            int depth, int maxDepth, int chainLength) {
        // Check depth limit
        if (depth >= maxDepth) {
            // Cap all exits since we reached max depth
            capExits(world, currentPart, currentOrigin, currentRot);
            return;
        }

        List<BlockVector3> rotatedExits = currentPart.getRotatedExitOffsets(currentRot);

        // Process each exit (random shuffle for variety?)
        // Process each exit
        for (int i = 0; i < rotatedExits.size(); i++) {
            BlockVector3 exitOffset = rotatedExits.get(i);

            // Calculate world position of this exit (Connection Point)
            BlockVector3 connectionPoint = currentOrigin.add(exitOffset);

            BlockVector3 originalExit = currentPart.getExitOffsets().get(i);
            int localExitYaw = currentPart.getExitDirection(originalExit);

            // Apply current rotation (Corrected Math: Local - Rot)
            int exitWorldYaw = (localExitYaw - currentRot + 360) % 360;

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
                            // Calculate Rotation: Intrinsic - Target = Rot
                            int nextRotation = (nextPart.getIntrinsicYaw() - exitWorldYaw + 360) % 360;

                            BlockVector3 nextEntryRotated = nextPart.getRotatedEntryOffset(nextRotation);
                            BlockVector3 nextOrigin = connectionPoint.subtract(nextEntryRotated);

                            Deepwither.getInstance().getLogger().info(String.format(
                                    "Trying [%s](%s) at %s Rot:%d | Chain:%d | ExYaw:%d -> TgtYaw:%d",
                                    nextPart.getFileName(), type, nextOrigin, nextRotation, chainLength, localExitYaw,
                                    exitWorldYaw));

                            if (pastePart(world, nextOrigin, nextPart, nextRotation, currentOrigin)) {
                                int newChain = type.equals("HALLWAY") ? chainLength + 1 : 0;
                                generateRecursive(world, nextPart, nextOrigin, nextRotation, depth + 1, maxDepth,
                                        newChain);
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
            int exitWorldYaw = (localExitYaw - currentRot + 360) % 360;

            placeCap(world, connectionPoint, exitWorldYaw, currentOrigin);
        }
    }

    private void placeCap(World world, BlockVector3 connectionPoint, int exitWorldYaw, BlockVector3 parentOrigin) {
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

                if (pastePart(world, nextOrigin, capPart, nextRotation, parentOrigin)) {
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

    private boolean pastePart(World world, BlockVector3 origin, DungeonPart part, int rotation,
            BlockVector3 ignoreOrigin) {
        // 1. Create Collision Box Candidate
        PlacedPart candidate = new PlacedPart(part, origin, rotation);

        // 2. Check overlap
        for (PlacedPart existing : placedParts) {
            // Ignore collision with parent part (Source of connection)
            if (ignoreOrigin != null && existing.origin.equals(ignoreOrigin))
                continue;

            if (candidate.intersects(existing)) {
                Deepwither.getInstance().getLogger().info("Collision detected at " + origin);
                return false;
            }
        }

        // 3. Paste and Marker Handling
        File schemFile = new File(dungeonFolder, part.getFileName());
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
        if (format == null) return false;

        try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
            Clipboard clipboard = reader.read();

            // --- ワールドへの貼り付け実行 ---
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

            // --- マーカー消去 (入口・出口) ---
            BlockVector3 rotatedEntry = part.getRotatedEntryOffset(rotation);
            removeMarker(world, origin.add(rotatedEntry), Material.GOLD_BLOCK);

            for (BlockVector3 exit : part.getRotatedExitOffsets(rotation)) {
                removeMarker(world, origin.add(exit), Material.IRON_BLOCK);
            }

            // --- モブスポーン処理 (レッドストーンブロック) ---
            if (!dungeonMobList.isEmpty()) {
                for (BlockVector3 spawnerOffset : part.getRotatedMobSpawnerOffsets(rotation)) {
                    BlockVector3 spawnPos = origin.add(spawnerOffset);
                    removeMarker(world, spawnPos, Material.REDSTONE_BLOCK);

                    String mobId = dungeonMobList.get(random.nextInt(dungeonMobList.size()));
                    Location loc = new Location(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                    // 即スポーンさせず、リストに追加
                    pendingSpawners.add(new PendingSpawner(loc, mobId, 1));
                }

                // 監視タスクが動いていなければ開始
                startSpawnerMonitor();
            }

            // --- ルートチェスト設置処理 (エメラルドブロック) ---
            for (BlockVector3 chestOffset : part.getRotatedLootChestOffsets(rotation)) {
                BlockVector3 chestPos = origin.add(chestOffset);

                // エメラルドブロック（マーカー）を空気にする
                removeMarker(world, chestPos, Material.EMERALD_BLOCK);

                // Chest設置場所を Location に変換
                Location loc = new Location(world, chestPos.getX(), chestPos.getY(), chestPos.getZ());

                // LootChestManager を使用して設置 (生成時に即設置)
                Deepwither.getInstance().getLootChestManager().placeDungeonLootChest(loc, lootChestId);

                Deepwither.getInstance().getLogger().info("Placed LootChest: " + lootChestId + " at " + chestPos);
            }

            // 成功としてリストに追加し、メソッドを終了
            placedParts.add(candidate);
            Deepwither.getInstance().getLogger().info("Placed " + part.getType() + " at " + origin);
            return true;

        } catch (Exception e) {
            Deepwither.getInstance().getLogger().severe("Failed to paste part: " + part.getFileName());
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

    private void startSpawnerMonitor() {
        if (isMonitoring) return;
        isMonitoring = true;

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (pendingSpawners.isEmpty()) {
                    this.cancel();
                    isMonitoring = false;
                    return;
                }

                // --- 負荷対策パラメータ ---
                int spawnedThisTick = 0;
                final int MAX_SPAWNS_PER_CHECK = 3; // 1回のチェック(1秒)で最大3体まで
                long delayBetweenMobs = 0; // 同時湧きの中での時間差(Tick)
                // -----------------------

                java.util.Iterator<PendingSpawner> iterator = pendingSpawners.iterator();
                while (iterator.hasNext() && spawnedThisTick < MAX_SPAWNS_PER_CHECK) {
                    PendingSpawner spawner = iterator.next();

                    boolean playerNearby = spawner.location.getWorld().getNearbyEntities(spawner.location, 12, 12, 12).stream()
                            .anyMatch(entity -> entity instanceof org.bukkit.entity.Player);

                    if (playerNearby) {
                        spawnedThisTick++;

                        // 1体ごとに数Tickずつずらして召喚する (さらに負荷を分散)
                        new org.bukkit.scheduler.BukkitRunnable() {
                            @Override
                            public void run() {
                                Deepwither.getInstance().getMobSpawnManager().spawnDungeonMob(spawner.location, spawner.mobId, mobLevel);
                                spawner.location.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, spawner.location, 20, 0.5, 1, 0.5, 0.1);
                            }
                        }.runTaskLater(Deepwither.getInstance(), delayBetweenMobs);

                        delayBetweenMobs += 5L; // 次のモブは0.25秒後に召喚
                        iterator.remove();
                    }
                }
            }
        }.runTaskTimer(Deepwither.getInstance(), 20L, 20L);
    }
}