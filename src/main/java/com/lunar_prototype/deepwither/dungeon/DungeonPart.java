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
                this.entryX = pos.getX() - origin.getX();
                this.entryY = pos.getY() - origin.getY();
                this.entryZ = pos.getZ() - origin.getZ();

                Deepwither.getInstance().getLogger().info(String.format(
                        "[%s] Found ENTRY(Gold). Pos:%s - Origin:%s = %d,%d,%d",
                        fileName, pos, origin, entryX, entryY, entryZ));
                foundEntry = true;
            }
            // 鉄ブロック (出口) -> 次のパーツへ接続する場所
            else if (block.getBlockType().equals(BlockTypes.IRON_BLOCK)) {
                BlockVector3 exitVec = pos.subtract(origin);
                this.exitOffsets.add(exitVec);

                Deepwither.getInstance().getLogger().info(String.format(
                        "[%s] Found EXIT(Iron). Pos:%s - Origin:%s = %s",
                        fileName, pos, origin, exitVec));
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

    private void calculateIntrinsicYaw() {
        if (exitOffsets.isEmpty()) {
            // Default to South (0 deg) or whatever
            this.intrinsicYaw = 0;
            return;
        }

        // Use first exit for primary flow
        BlockVector3 primExit = exitOffsets.get(0);
        int dx = primExit.getX() - entryX;
        int dz = primExit.getZ() - entryZ;

        // Calculate Yaw
        if (Math.abs(dx) > Math.abs(dz)) {
            // East (+X) -> 270 (WE Rot? No let's stick to standard WE 90 deg steps)
            // If we want "Direction", let's map to:
            // +Z (South) -> 0
            // -X (West) -> 90
            // -Z (North) -> 180
            // +X (East) -> 270
            // Wait, WE rotateY(90): x->-z, z->x.
            // If vector is (0,1) (+Z South). Rot 90 -> (-1,0) (West).
            // So Rot 90 turns South to West.
            // Rot 180 turns South to North.
            // Rot 270 turns South to East.

            // WE Coordinates:
            // +X = East? No, +X is usually East in MC.
            // +Z = South.

            // Mapping vector to Rot 90 steps from South(0):
            // South (+Z) -> 0.
            // West (-X) -> 90.
            // North (-Z) -> 180.
            // East (+X) -> 270.

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
        return BlockVector3.at(Math.round(v3.getX()), Math.round(v3.getY()), Math.round(v3.getZ()));
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

    public String getFileName() {
        return fileName;
    }

    public String getType() {
        return type;
    }
}