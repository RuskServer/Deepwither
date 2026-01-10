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
    private int entryX, entryY, entryZ;

    // Multiple exits support
    private final List<BlockVector3> exitOffsets = new ArrayList<>();

    // Spawn Markers (Preserved feature)
    private final List<BlockVector3> mobMarkers = new ArrayList<>();
    private final List<BlockVector3> lootMarkers = new ArrayList<>();

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
                .info(String.format("[%s] Scanning Part. Origin:%s | Bounds Rel:[%s, %s]",
                        fileName, origin, minPoint, maxPoint));

        boolean foundEntry = false;
        this.exitOffsets.clear();
        this.mobMarkers.clear();
        this.lootMarkers.clear();

        for (BlockVector3 pos : clipboard.getRegion()) {
            var block = clipboard.getFullBlock(pos);

            // 金ブロック (入口)
            if (foundEntry == false && block.getBlockType().equals(BlockTypes.GOLD_BLOCK)) {
                this.entryX = pos.getX() - origin.getX();
                this.entryY = pos.getY() - origin.getY();
                this.entryZ = pos.getZ() - origin.getZ();
                foundEntry = true;
            }
            // 鉄ブロック (出口)
            else if (block.getBlockType().equals(BlockTypes.IRON_BLOCK)) {
                // Force Flat Y
                BlockVector3 exitVec = BlockVector3.at(
                        pos.getX() - origin.getX(),
                        this.entryY,
                        pos.getZ() - origin.getZ());
                this.exitOffsets.add(exitVec);
            }
            // Mob Marker (Redstone)
            else if (block.getBlockType().equals(BlockTypes.REDSTONE_BLOCK)) {
                this.mobMarkers.add(pos.subtract(origin));
            }
            // Loot Marker (Emerald)
            else if (block.getBlockType().equals(BlockTypes.EMERALD_BLOCK)) {
                this.lootMarkers.add(pos.subtract(origin));
            }
        }

        if (!foundEntry) {
            Deepwither.getInstance().getLogger()
                    .warning("[" + fileName + "] No Gold Block found. Assuming (0,0,0).");
        }

        calculateIntrinsicYaw();
    }

    private void calculateIntrinsicYaw() {
        if (exitOffsets.isEmpty()) {
            this.intrinsicYaw = 0;
            return;
        }

        BlockVector3 primExit = exitOffsets.get(0);
        int dx = primExit.getX() - entryX;
        int dz = primExit.getZ() - entryZ;

        if (Math.abs(dx) > Math.abs(dz)) {
            this.intrinsicYaw = (dx > 0) ? 270 : 90;
        } else {
            this.intrinsicYaw = (dz > 0) ? 0 : 180;
        }
    }

    public int getIntrinsicYaw() {
        return intrinsicYaw;
    }

    public BlockVector3 getRotatedEntryOffset(int rotation) {
        return transformVector(getEntryOffset(), rotation);
    }

    public List<BlockVector3> getRotatedExitOffsets(int rotation) {
        return exitOffsets.stream()
                .map(vec -> transformVector(vec, rotation))
                .collect(Collectors.toList());
    }

    private BlockVector3 transformVector(BlockVector3 vec, int angle) {
        if (vec == null)
            return BlockVector3.at(0, 0, 0);
        int normalizedAngle = (angle % 360 + 360) % 360;
        if (normalizedAngle == 0)
            return vec;

        AffineTransform transform = new AffineTransform().rotateY(normalizedAngle);
        var v3 = transform.apply(vec.toVector3());
        return BlockVector3.at(Math.round(v3.getX()), Math.round(v3.getY()), Math.round(v3.getZ()));
    }

    public BlockVector3 getEntryOffset() {
        return BlockVector3.at(entryX, entryY, entryZ);
    }

    public List<BlockVector3> getMobMarkers() {
        return mobMarkers;
    }

    public List<BlockVector3> getLootMarkers() {
        return lootMarkers;
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
        int dx = exit.getX() - entryX;
        int dz = exit.getZ() - entryZ;
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
