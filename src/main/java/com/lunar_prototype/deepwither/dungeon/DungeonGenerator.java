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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DungeonGenerator {
    private final String dungeonName;
    private final List<DungeonPart> partList = new ArrayList<>();
    private final File dungeonFolder;
    private final String difficulty;
    private int mobLevel;
    private final Random random = new Random();

    // Store placed parts for collision detection (Used during async calculation)
    private final List<PlacedPart> placedParts = new ArrayList<>();
    private final List<Location> validSpawnLocations = new ArrayList<>();

    private final List<String> dungeonMobList = new ArrayList<>();
    // pendingSpawners populated during Sync Paste phase
    private final List<PendingSpawner> pendingSpawners = new ArrayList<>();
    private boolean isMonitoring = false;

    private final List<Endpoint> potentialEndpoints = new ArrayList<>();

    private int maxDepth = 10;
    private String bossMobId;
    private String lootChestId;

    // --- Async Generation Queue ---
    private final Queue<PendingPaste> pasteQueue = new ConcurrentLinkedQueue<>();
    private int totalPartsToPaste = 0;

    private static class Endpoint {
        final BlockVector3 connectionPoint;
        final int exitWorldYaw;
        final BlockVector3 parentOrigin;

        Endpoint(BlockVector3 cp, int yaw, BlockVector3 po) {
            this.connectionPoint = cp;
            this.exitWorldYaw = yaw;
            this.parentOrigin = po;
        }
    }

    private static class PendingPaste {
        final DungeonPart part;
        final BlockVector3 origin;
        final int rotation;
        final Clipboard clipboard;

        public PendingPaste(DungeonPart part, BlockVector3 origin, int rotation, Clipboard clipboard) {
            this.part = part;
            this.origin = origin;
            this.rotation = rotation;
            this.clipboard = clipboard;
        }
    }
    // ------------------------------

    public static class PendingSpawner {
        private final Location location;
        private final String mobId;
        private final int level;
        private final boolean isBoss;

        public PendingSpawner(Location location, String mobId, int level, boolean isBoss) {
            this.location = location;
            this.mobId = mobId;
            this.level = level;
            this.isBoss = isBoss;
        }

        public Location getLocation() {
            return location;
        }

        public String getMobId() {
            return mobId;
        }

        public int getLevel() {
            return level;
        }

        public boolean isBoss() { return isBoss; }
    }

    public List<Location> getValidSpawnLocations() {
        return validSpawnLocations;
    }

    public List<PendingSpawner> getPendingSpawners() {
        return pendingSpawners;
    }

    private static class PlacedPart {
        private final DungeonPart part;
        private final BlockVector3 origin;
        private final int rotation;
        private final BlockVector3 minBound;
        private final BlockVector3 maxBound;

        public PlacedPart(DungeonPart part, BlockVector3 origin, int rotation) {
            this.part = part;
            this.origin = origin;
            this.rotation = rotation;

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
        this.bossMobId = config.getString("boss_mob_id", "DUNGEON_BOSS_DEFAULT");

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
     * 非同期でダンジョンレイアウトを計算し、その後Syncタスクで設置を行う
     */
    public void generateBranchingAsync(World world, int startRotation, Consumer<List<Location>> callback) {
        placedParts.clear();
        pasteQueue.clear();
        validSpawnLocations.clear();
        pendingSpawners.clear();

        Deepwither.getInstance().getLogger()
                .info("=== [Async] Generating Dungeon Layout (MaxDepth:" + maxDepth + ") ===");

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // レイアウト計算とクリップボード読み込み（重い処理）
                    calculateLayout(startRotation);

                    Deepwither.getInstance().getLogger()
                            .info("=== [Async] Layout Calculated. Total Parts: " + pasteQueue.size() + " ===");

                    // Syncタスクで設置開始
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            startPasteTask(world, callback);
                        }
                    }.runTask(Deepwither.getInstance());

                } catch (Exception e) {
                    e.printStackTrace();
                    // エラー時もコールバックは呼んでおくと安全かも（空リスト等で）
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            callback.accept(Collections.emptyList());
                        }
                    }.runTask(Deepwither.getInstance());
                }
            }
        }.runTaskAsynchronously(Deepwither.getInstance());
    }

    // --- Async Layout Calculation Logic ---

    private void calculateLayout(int startRotation) {
        BlockVector3 startOrigin = BlockVector3.at(0, 64, 0);
        DungeonPart startPart = findPartByType("ENTRANCE");

        if (startPart == null) {
            Deepwither.getInstance().getLogger().warning("No ENTRANCE part found!");
            return;
        }

        int finalStartRotation = startRotation + 180;

        if (planPartPlacement(startPart, startOrigin, finalStartRotation, null)) {
            calculateRecursive(startPart, startOrigin, finalStartRotation, 1, maxDepth, 0);
        }
    }

    private void calculateRecursive(DungeonPart currentPart, BlockVector3 currentOrigin, int currentRot,
            int depth, int maxDepth, int chainLength) {
        if (depth >= maxDepth) {
            capExits(currentPart, currentOrigin, currentRot);
            return;
        }

        List<BlockVector3> rotatedExits = currentPart.getRotatedExitOffsets(currentRot);

        for (int i = 0; i < rotatedExits.size(); i++) {
            BlockVector3 exitOffset = rotatedExits.get(i);
            BlockVector3 connectionPoint = currentOrigin.add(exitOffset);

            BlockVector3 originalExit = currentPart.getExitOffsets().get(i);
            int localExitYaw = currentPart.getExitDirection(originalExit);
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
                        int nextRotation = (nextPart.getIntrinsicYaw() - exitWorldYaw + 360) % 360;
                        BlockVector3 nextEntryRotated = nextPart.getRotatedEntryOffset(nextRotation);
                        BlockVector3 nextOrigin = connectionPoint.subtract(nextEntryRotated);

                        if (planPartPlacement(nextPart, nextOrigin, nextRotation, currentOrigin)) {
                            int newChain = type.equals("HALLWAY") ? chainLength + 1 : 0;
                            calculateRecursive(nextPart, nextOrigin, nextRotation, depth + 1, maxDepth, newChain);
                            placedInfo = true;
                            break;
                        }
                    }
                    if (placedInfo)
                        break;
                }
            }

            if (!placedInfo) {
                potentialEndpoints.add(new Endpoint(connectionPoint, exitWorldYaw, currentOrigin));;
            }
        }
    }

    private void capExits(DungeonPart currentPart, BlockVector3 currentOrigin, int currentRot) {
        List<BlockVector3> rotatedExits = currentPart.getRotatedExitOffsets(currentRot);
        for (int i = 0; i < rotatedExits.size(); i++) {
            BlockVector3 exitOffset = rotatedExits.get(i);
            BlockVector3 connectionPoint = currentOrigin.add(exitOffset);
            BlockVector3 originalExit = currentPart.getExitOffsets().get(i);
            int localExitYaw = currentPart.getExitDirection(originalExit);
            int exitWorldYaw = (localExitYaw - currentRot + 360) % 360;

            // 【変更】直接配置せず、候補リストに入れる
            potentialEndpoints.add(new Endpoint(connectionPoint, exitWorldYaw, currentOrigin));
        }
    }

    private void placeCap(BlockVector3 connectionPoint, int exitWorldYaw, BlockVector3 parentOrigin) {
        List<String> capTypes = Arrays.asList("CAP", "ENTRANCE");
        for (String type : capTypes) {
            List<DungeonPart> candidates = partList.stream()
                    .filter(p -> p.getType().equals(type))
                    .collect(Collectors.toList());
            if (candidates.isEmpty())
                continue;
            Collections.shuffle(candidates);

            for (DungeonPart capPart : candidates) {
                int baseRotation = (capPart.getIntrinsicYaw() - exitWorldYaw + 360) % 360;
                int nextRotation = (baseRotation + 180) % 360;
                BlockVector3 nextEntryRotated = capPart.getRotatedEntryOffset(nextRotation);
                BlockVector3 nextOrigin = connectionPoint.subtract(nextEntryRotated);

                if (planPartPlacement(capPart, nextOrigin, nextRotation, parentOrigin)) {
                    return;
                }
            }
        }
    }

    private void finalizeLayout() {
        Collections.shuffle(potentialEndpoints);
        boolean bossPlaced = false;

        for (Endpoint ep : potentialEndpoints) {
            if (!bossPlaced) {
                // TYPE: BOSS のパーツを配置試行
                DungeonPart bossPart = findPartByType("BOSS");
                if (bossPart != null) {
                    int nextRotation = (bossPart.getIntrinsicYaw() - ep.exitWorldYaw + 360) % 360;
                    BlockVector3 nextEntryRotated = bossPart.getRotatedEntryOffset(nextRotation);
                    BlockVector3 nextOrigin = ep.connectionPoint.subtract(nextEntryRotated);

                    if (planPartPlacement(bossPart, nextOrigin, nextRotation, ep.parentOrigin)) {
                        bossPlaced = true;
                        continue;
                    }
                }
            }
            // ボスが既に置かれたか、配置に失敗した場合は通常のCAPを置く
            placeCap(ep.connectionPoint, ep.exitWorldYaw, ep.parentOrigin);
        }
        potentialEndpoints.clear();
    }

    /**
     * レイアウトの有効性を確認し、キューに追加する（IO含む）
     * Main Threadではないので安全にIO可能
     */
    private boolean planPartPlacement(DungeonPart part, BlockVector3 origin, int rotation, BlockVector3 ignoreOrigin) {
        // 1. Collision Check (In-Memory)
        PlacedPart candidate = new PlacedPart(part, origin, rotation);
        for (PlacedPart existing : placedParts) {
            if (ignoreOrigin != null && existing.origin.equals(ignoreOrigin))
                continue;
            if (candidate.intersects(existing))
                return false;
        }

        // 2. Load Clipboard (IO)
        File schemFile = new File(dungeonFolder, part.getFileName());
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
        if (format == null)
            return false;

        try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
            Clipboard clipboard = reader.read();
            // 成功したらキューに追加
            placedParts.add(candidate);
            pasteQueue.add(new PendingPaste(part, origin, rotation, clipboard));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Sync Paste Logic ---

    private void startPasteTask(World world, Consumer<List<Location>> callback) {
        totalPartsToPaste = pasteQueue.size();

        new BukkitRunnable() {
            @Override
            public void run() {
                // 1tickあたり複数個処理して速度を稼ぐ（でも重くならない程度に）
                int processPerTick = 2;

                for (int i = 0; i < processPerTick; i++) {
                    PendingPaste task = pasteQueue.poll();
                    if (task == null) {
                        // 全て完了
                        this.cancel();
                        finishGeneration(callback);
                        return;
                    }
                    realPaste(world, task);
                }
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        finalizeLayout();
    }

    private void realPaste(World world, PendingPaste task) {
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            ClipboardHolder holder = new ClipboardHolder(task.clipboard);
            holder.setTransform(new AffineTransform().rotateY(task.rotation));

            Operation operation = holder
                    .createPaste(editSession)
                    .to(task.origin)
                    .ignoreAirBlocks(true)
                    .build();
            Operations.complete(operation);
        } catch (Exception e) {
            Deepwither.getInstance().getLogger().severe("[AsyncGen] Paste failed for " + task.part.getFileName());
            e.printStackTrace();
        }

        // --- マーカー処理 (Sync) ---
        BlockVector3 origin = task.origin;
        DungeonPart part = task.part;
        int rotation = task.rotation;

        BlockVector3 rotatedEntry = part.getRotatedEntryOffset(rotation);
        removeMarker(world, origin.add(rotatedEntry), Material.GOLD_BLOCK);

        for (BlockVector3 exit : part.getRotatedExitOffsets(rotation)) {
            removeMarker(world, origin.add(exit), Material.IRON_BLOCK);
        }

        if (!dungeonMobList.isEmpty()) {
            for (BlockVector3 spawnerOffset : part.getRotatedMobSpawnerOffsets(rotation)) {
                BlockVector3 spawnPos = origin.add(spawnerOffset);
                removeMarker(world, spawnPos, Material.REDSTONE_BLOCK);

                String mobId = dungeonMobList.get(random.nextInt(dungeonMobList.size()));
                Location loc = new Location(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                pendingSpawners.add(new PendingSpawner(loc, mobId, 1,false));
            }
        }

        for (BlockVector3 chestOffset : part.getRotatedLootChestOffsets(rotation)) {
            BlockVector3 chestPos = origin.add(chestOffset);
            removeMarker(world, chestPos, Material.EMERALD_BLOCK);
            Location loc = new Location(world, chestPos.getX(), chestPos.getY(), chestPos.getZ());
            Deepwither.getInstance().getLootChestManager().placeDungeonLootChest(loc, lootChestId);
        }

        for (BlockVector3 spawnOffset : part.getRotatedPlayerSpawnOffsets(rotation)) {
            BlockVector3 realPos = origin.add(spawnOffset);
            removeMarker(world, realPos, Material.LAPIS_BLOCK);
            Location loc = new Location(world, realPos.getX() + 0.5, realPos.getY(), realPos.getZ() + 0.5);
            loc.setYaw((float) rotation);
            validSpawnLocations.add(loc);
        }

        for (BlockVector3 bossOffset : part.getRotatedBossSpawnOffsets(rotation)) {
            BlockVector3 spawnPos = origin.add(bossOffset);
            removeMarker(world, spawnPos, Material.DIAMOND_BLOCK); // ダイヤモンドブロックを除去

            // ボス用モブIDを取得 (configで定義するか、専用のロジックで)
            Location loc = new Location(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            pendingSpawners.add(new PendingSpawner(loc, this.bossMobId, this.mobLevel, true)); // isBoss = true
        }
    }

    private void finishGeneration(Consumer<List<Location>> callback) {
        Deepwither.getInstance().getLogger()
                .info("=== [AsyncGen] Pasting Complete! Total parts: " + placedParts.size() + " ===");
        if (!pendingSpawners.isEmpty()) {
            startSpawnerMonitor();
        }
        callback.accept(validSpawnLocations);
    }

    // --- Utility Methods ---

    private void removeMarker(World world, BlockVector3 pos, Material expectedType) {
        Location loc = new Location(world, pos.getX(), pos.getY(), pos.getZ());
        if (loc.getBlock().getType() == expectedType) {
            loc.getBlock().setType(Material.AIR);
        }
    }

    private DungeonPart findPartByType(String type) {
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

        new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingSpawners.isEmpty()) {
                    this.cancel();
                    isMonitoring = false;
                    return;
                }

                // --- 修正: List型でのワールド有効性チェック ---
                // 最初の要素からワールドの状態を確認
                PendingSpawner first = pendingSpawners.get(0);
                World worldObj = first.location.getWorld();

                // ワールドがアンロードされているか、既に無効な場合は全クリアして終了
                if (worldObj == null || !Bukkit.getWorlds().contains(worldObj)) {
                    pendingSpawners.clear();
                    this.cancel();
                    isMonitoring = false;
                    return;
                }

                int spawnedThisTick = 0;
                final int MAX_SPAWNS_PER_CHECK = 3;

                Iterator<PendingSpawner> iterator = pendingSpawners.iterator();
                while (iterator.hasNext() && spawnedThisTick < MAX_SPAWNS_PER_CHECK) {
                    PendingSpawner spawner = iterator.next();

                    // spawnerごとのワールド取得（安全のため）
                    World currentWorld = spawner.location.getWorld();
                    if (currentWorld == null) {
                        iterator.remove();
                        continue;
                    }

                    // 1. 付近のプレイヤーを探す（バフ数を確認するため）
                    Player nearbyPlayer = (Player) spawner.location.getWorld().getNearbyEntities(spawner.location, 5, 5, 5)
                            .stream()
                            .filter(entity -> entity instanceof Player)
                            .findFirst() // 最初に見つかったプレイヤーを基準にする
                            .orElse(null);

                    if (nearbyPlayer != null) {
                        spawnedThisTick++;

                        // 2. バフ数に応じたレベルのスケーリング計算
                        // 基本レベル(mobLevel)にバフ数に応じた補正を加える
                        int buffCount = Deepwither.getInstance().getRoguelikeBuffManager().getBuffCount(nearbyPlayer);

                        // 例: 最終レベル = 基本レベル + (バフ数 * 1.5)
                        // 計算式は適宜調整してください
                        int scaledLevel = mobLevel + (int) Math.floor(buffCount * 1.2);

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                Deepwither.getInstance().getMobSpawnManager().spawnDungeonMob(
                                        spawner.location,
                                        spawner.mobId,
                                        scaledLevel // スケーリング後のレベルを適用
                                );

                                spawner.location.getWorld().spawnParticle(
                                        org.bukkit.Particle.CLOUD,
                                        spawner.location,
                                        20, 0.5, 1, 0.5, 0.1
                                );
                            }
                        }.runTaskLater(Deepwither.getInstance(), 0L);

                        iterator.remove();
                    }
                }
            }
        }.runTaskTimer(Deepwither.getInstance(), 20L, 20L);
    }

    // Keep for potential legacy support but warn
    public void generateBranching(World world, int startRotation) {
        Deepwither.getInstance().getLogger()
                .warning("Blocking generateBranching called! Use generateBranchingAsync if possible.");
        // Fallback or just forward to async with no-op callback?
        // Better to force users to update. keeping mostly empty implementation to avoid
        // crash if called
        generateBranchingAsync(world, startRotation, (spawns) -> {
        });
    }
}