package com.lunar_prototype.deepwither.dungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DungeonPart {
    private final String fileName;
    private final String type;
    private final int length;

    // Origin(Schematic保存時の立ち位置)からの相対座標
    // private BlockVector3 entryOffset = BlockVector3.ZERO;
    private int entryX, entryY, entryZ;

    // Multiple exits support
    private final List<BlockVector3> exitOffsets = new ArrayList<>();

    private final List<BlockVector3> mobSpawnerOffsets = new ArrayList<>();

    private final List<BlockVector3> lootChestOffsets = new ArrayList<>();

    private final List<BlockVector3> playerSpawnOffsets = new ArrayList<>();

    private final List<BlockVector3> bossSpawnOffsets = new ArrayList<>();

    // Bounding Box relative to Origin
    private BlockVector3 minPoint;
    private BlockVector3 maxPoint;

    public DungeonPart(String fileName, String type, int length) {
        this.fileName = fileName;
        this.type = type;
        this.length = length;
    }

    // Flow direction (Yaw) from Entry to Exit
    private int intrinsicYaw = 0;

    public void scanMarkers(Clipboard clipboard) {
        BlockVector3 origin = clipboard.getOrigin();

        // Calculate Bounding Box relative to Origin
        this.minPoint = clipboard.getRegion().getMinimumPoint().subtract(origin);
        this.maxPoint = clipboard.getRegion().getMaximumPoint().subtract(origin);

        Deepwither.getInstance().getLogger()
                .info(String.format("[%s] Scanning Part (ID:%d). Origin:%s | Bounds Rel:[%s, %s]",
                        fileName, System.identityHashCode(this), origin, minPoint, maxPoint));

        boolean foundEntry = false;

        for (BlockVector3 pos : clipboard.getRegion()) {
            var block = clipboard.getFullBlock(pos);

            // 金ブロック (入口) -> 接続元を受け入れる場所
            if (block.getBlockType().equals(BlockTypes.GOLD_BLOCK)) {
                this.entryX = pos.x() - origin.x();
                this.entryY = pos.y() - origin.y();
                this.entryZ = pos.z() - origin.z();

                Deepwither.getInstance().getLogger().info(String.format(
                        "[%s] Found ENTRY(Gold). Pos:%s - Origin:%s = %d,%d,%d",
                        fileName, pos, origin, entryX, entryY, entryZ));
                foundEntry = true;
            }
            // 鉄ブロック (出口) -> 次のパーツへ接続する場所
            else if (block.getBlockType().equals(BlockTypes.IRON_BLOCK)) {
                // Force Flat: Use Entry Y for Exit Y to prevent climbing
                // Assuming flat dungeon design as per user request
                BlockVector3 exitVec = BlockVector3.at(
                        pos.x() - origin.x(),
                        this.entryY, // Force Y to match Entry
                        pos.z() - origin.z());

                this.exitOffsets.add(exitVec);

                Deepwither.getInstance().getLogger().info(String.format(
                        "[%s] Found EXIT(Iron). Pos:%s - Origin:%s = %s (Forced Flat Y=%d)",
                        fileName, pos, origin, exitVec, entryY));
            }
            // レッドストーンブロック (モブスポナー)
            if (block.getBlockType().equals(BlockTypes.REDSTONE_BLOCK)) {
                BlockVector3 spawnerVec = BlockVector3.at(
                        pos.x() - origin.x(),
                        pos.y() - origin.y(),
                        pos.z() - origin.z());
                this.mobSpawnerOffsets.add(spawnerVec);

                Deepwither.getInstance().getLogger().info(String.format(
                        "[%s] Found MOB_MARKER(Redstone). Pos:%s", fileName, spawnerVec));
            }
            if (block.getBlockType().equals(BlockTypes.EMERALD_BLOCK)) {
                BlockVector3 chestVec = BlockVector3.at(
                        pos.x() - origin.x(),
                        pos.y() - origin.y(),
                        pos.z() - origin.z());
                this.lootChestOffsets.add(chestVec);

                Deepwither.getInstance().getLogger().info(String.format(
                        "[%s] Found LOOT_MARKER(Emerald). Offset: %s", fileName, chestVec));
            }
            if (block.getBlockType().equals(BlockTypes.LAPIS_BLOCK)) {
                BlockVector3 spawnVec = BlockVector3.at(
                        pos.x() - origin.x(),
                        pos.y() - origin.y(),
                        pos.z() - origin.z());
                this.playerSpawnOffsets.add(spawnVec);

                Deepwither.getInstance().getLogger().info(String.format(
                        "[%s] Found LOOT_MARKER(Emerald). Offset: %s", fileName, spawnVec));
            }
            // [追加] ダイヤモンドブロック (ボススポナー)
            if (block.getBlockType().equals(BlockTypes.DIAMOND_BLOCK)) {
                BlockVector3 bossVec = BlockVector3.at(
                        pos.x() - origin.x(),
                        pos.y() - origin.y(),
                        pos.z() - origin.z());
                this.bossSpawnOffsets.add(bossVec);

                Deepwither.getInstance().getLogger().info(String.format(
                        "[%s] Found BOSS_MARKER(Diamond). Offset: %s", fileName, bossVec));
            }
        }

        if (!foundEntry) {
            Deepwither.getInstance().getLogger()
                    .warning("[" + fileName + "] Warning: No Gold Block (Entry) found. Assuming (0,0,0).");
        }

        if (exitOffsets.isEmpty() && !type.equals("ROOM")) {
            Deepwither.getInstance().getLogger()
                    .info("[" + fileName + "] Info: No Iron Block (Exit) found. (Normal for dead-ends)");
        }

        calculateIntrinsicYaw();
    }

    public List<BlockVector3> getRotatedMobSpawnerOffsets(int rotation) {
        return mobSpawnerOffsets.stream()
                .map(vec -> transformVector(vec, rotation))
                .collect(Collectors.toList());
    }

    /**
     * 回転後のルートチェスト（エメラルドブロック）の座標リストを取得
     */
    public List<BlockVector3> getRotatedLootChestOffsets(int rotation) {
        return lootChestOffsets.stream()
                .map(vec -> transformVector(vec, rotation))
                .collect(Collectors.toList());
    }

    public List<BlockVector3> getRotatedPlayerSpawnOffsets(int rotation) {
        return playerSpawnOffsets.stream()
                .map(vec -> transformVector(vec, rotation))
                .collect(Collectors.toList());
    }

    /**
     * 回転後のボススポナー座標リストを取得
     */
    public List<BlockVector3> getRotatedBossSpawnOffsets(int rotation) {
        return bossSpawnOffsets.stream()
                .map(vec -> transformVector(vec, rotation))
                .collect(Collectors.toList());
    }

    private void calculateIntrinsicYaw() {
        if (exitOffsets.isEmpty()) {
            this.intrinsicYaw = 0;
            return;
        }

        BlockVector3 primExit = exitOffsets.get(0);
        int dx = primExit.x() - entryX;
        int dz = primExit.z() - entryZ;

        if (Math.abs(dx) > Math.abs(dz)) {
            this.intrinsicYaw = (dx > 0) ? 270 : 90;
        } else {
            this.intrinsicYaw = (dz > 0) ? 0 : 180;
        }
        Deepwither.getInstance().getLogger().info("[" + fileName + "] Intrinsic Yaw: " + intrinsicYaw);
    }

    public int getIntrinsicYaw() {
        return intrinsicYaw;
    }

    /**
     * 回転後の「入口」オフセットを取得
     */
    public BlockVector3 getRotatedEntryOffset(int rotation) {
        return transformVector(getEntryOffset(), rotation);
    }

    /**
     * 回転後の「出口」オフセットリストを取得
     */
    public List<BlockVector3> getRotatedExitOffsets(int rotation) {
        return exitOffsets.stream()
                .map(vec -> transformVector(vec, rotation))
                .collect(Collectors.toList());
    }

    /**
     * Y軸周りの回転 (WorldEdit仕様)
     */
    private BlockVector3 transformVector(BlockVector3 vec, int angle) {
        if (vec == null)
            return BlockVector3.ZERO;

        int normalizedAngle = angle % 360;
        if (normalizedAngle < 0)
            normalizedAngle += 360;

        AffineTransform transform = new AffineTransform().rotateY(normalizedAngle);
        var v3 = transform.apply(vec.toVector3());
        return BlockVector3.at(Math.round(v3.x()), Math.round(v3.y()), Math.round(v3.z()));
    }

    public BlockVector3 getEntryOffset() {
        return BlockVector3.at(entryX, entryY, entryZ);
    }

    // Deprecated or used for primary exit if needed
    public BlockVector3 getFirstExitOffset() {
        return exitOffsets.isEmpty() ? BlockVector3.ZERO : exitOffsets.get(0);
    }

    public List<BlockVector3> getExitOffsets() {
        return exitOffsets;
    }

    public BlockVector3 getMinPoint() {
        return minPoint;
    }

    public BlockVector3 getMaxPoint() {
        return maxPoint;
    }

    public int getExitDirection(BlockVector3 exit) {
        // Determine direction based on which face the exit is on
        int tolerance = 1;

        // Check Z faces
        if (Math.abs(exit.z() - maxPoint.z()) <= tolerance)
            return 0; // South (+Z)
        if (Math.abs(exit.z() - minPoint.z()) <= tolerance)
            return 180; // North (-Z)

        // Check X faces
        if (Math.abs(exit.x() - maxPoint.x()) <= tolerance)
            return 270; // East (+X)
        if (Math.abs(exit.x() - minPoint.x()) <= tolerance)
            return 90; // West (-X)

        // Fallback to vector heuristic if not on face (should rarely happen for valid
        // dungeons)
        Deepwither.getInstance().getLogger().warning("Exit not on bounding box face! Using vector heuristic: " + exit);
        int dx = exit.x() - entryX;
        int dz = exit.z() - entryZ;
        if (Math.abs(dx) > Math.abs(dz)) {
            return (dx > 0) ? 270 : 90;
        } else {
            return (dz > 0) ? 0 : 180;
        }
    }

    public String getFileName() {
        return fileName;
    }

    public String getType() {
        return type;
    }
}