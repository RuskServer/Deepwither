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

    // List of exits, mobs, and loot relative to the Entry (Gold Block)

    // Multiple exits support
    private final List<BlockVector3> exitOffsets = new ArrayList<>();

    // Spawn Markers
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

    // Schematic Origin relative to Entry (rotated along with other vectors)
    private BlockVector3 originRelToEntry = BlockVector3.at(0, 0, 0);

    public void scanMarkers(Clipboard clipboard) {
        BlockVector3 origin = clipboard.getOrigin();

        // Clear existing data to prevent accumulation
        this.exitOffsets.clear();
        this.mobMarkers.clear();
        this.lootMarkers.clear();

        // 1. First pass: Find Entry (Gold Block)
        boolean foundEntry = false;
        BlockVector3 entryPosLocal = BlockVector3.at(0, 0, 0);

        for (BlockVector3 pos : clipboard.getRegion()) {
            if (clipboard.getFullBlock(pos).getBlockType().equals(BlockTypes.GOLD_BLOCK)) {
                entryPosLocal = pos;
                foundEntry = true;
                break;
            }
        }

        if (!foundEntry) {
            entryPosLocal = origin;
            Deepwither.getInstance().getLogger()
                    .warning("[" + fileName + "] No Gold Block (Entry) found. Using Origin as Entry.");
        }

        // originRelToEntry = origin - entryPosLocal
        this.originRelToEntry = origin.subtract(entryPosLocal);

        // 2. Second pass: Collect everything else, making them relative to ENTRY
        for (BlockVector3 pos : clipboard.getRegion()) {
            var block = clipboard.getFullBlock(pos);
            BlockVector3 currentPos = BlockVector3.at(pos.getX(), pos.getY(), pos.getZ());
            BlockVector3 posRelToEntry = currentPos.subtract(entryPosLocal);

            // Exit (Iron Block)
            if (block.getBlockType().equals(BlockTypes.IRON_BLOCK)) {
                // Force Flat Y relative to Entry
                BlockVector3 exitVec = BlockVector3.at(posRelToEntry.getX(), 0, posRelToEntry.getZ());
                this.exitOffsets.add(exitVec);
                Deepwither.getInstance().getLogger()
                        .info(String.format("[%s] Found EXIT at %s (Rel to Entry)", fileName, exitVec));
            }
            // Mob Marker (Redstone)
            else if (block.getBlockType().equals(BlockTypes.REDSTONE_BLOCK)) {
                this.mobMarkers.add(posRelToEntry);
                Deepwither.getInstance().getLogger()
                        .info(String.format("[%s] Found MOB at %s (Rel to Entry)", fileName, posRelToEntry));
            }
            // Loot Marker (Emerald)
            else if (block.getBlockType().equals(BlockTypes.EMERALD_BLOCK)) {
                this.lootMarkers.add(posRelToEntry);
                Deepwither.getInstance().getLogger()
                        .info(String.format("[%s] Found LOOT at %s (Rel to Entry)", fileName, posRelToEntry));
            }
        }

        // 3. Normalize Bounding Box relative to Entry
        this.minPoint = clipboard.getRegion().getMinimumPoint().subtract(entryPosLocal);
        this.maxPoint = clipboard.getRegion().getMaximumPoint().subtract(entryPosLocal);

        Deepwither.getInstance().getLogger().info(String.format(
                "[%s] Scan Complete: %d exits, %d mob, %d loot. Origin relative to Entry: %s",
                fileName, exitOffsets.size(), mobMarkers.size(), lootMarkers.size(), originRelToEntry));

        calculateIntrinsicYaw();
    }

    private void calculateIntrinsicYaw() {
        if (exitOffsets.isEmpty()) {
            this.intrinsicYaw = 0;
            return;
        }

        // primExit is already relative to Entry (0,0,0)
        BlockVector3 primExit = exitOffsets.get(0);
        int dx = primExit.getX();
        int dz = primExit.getZ();

        if (Math.abs(dx) >= Math.abs(dz)) {
            this.intrinsicYaw = (dx > 0) ? 270 : 90;
        } else {
            this.intrinsicYaw = (dz > 0) ? 0 : 180;
        }
        Deepwither.getInstance().getLogger()
                .info("[" + fileName + "] Intrinsic Yaw: " + intrinsicYaw + " (Primary Exit: " + primExit + ")");
    }

    public int getIntrinsicYaw() {
        return intrinsicYaw;
    }

    /**
     * Get the rotated Schematic Origin relative to the Entry.
     */
    public BlockVector3 getRotatedOriginOffset(int rotation) {
        return transformVector(originRelToEntry, rotation);
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
     * Y-axis Rotation. Positive angle = Clockwise (Minecraft Yaw).
     */
    public BlockVector3 transformVector(BlockVector3 vec, int angle) {
        if (vec == null)
            return BlockVector3.at(0, 0, 0);

        int normalizedAngle = angle % 360;
        if (normalizedAngle < 0)
            normalizedAngle += 360;

        // WorldEdit AffineTransform.rotateY is Counter-Clockwise.
        // Minecraft Yaw is Clockwise.
        // rotateY(-angle) converts CW to CCW.
        AffineTransform transform = new AffineTransform().rotateY(-normalizedAngle);
        var v3 = transform.apply(vec.toVector3());
        return BlockVector3.at(Math.round(v3.getX()), Math.round(v3.getY()), Math.round(v3.getZ()));
    }

    public BlockVector3 getOriginRelToEntry() {
        return originRelToEntry;
    }

    // Deprecated or used for primary exit if needed
    public BlockVector3 getFirstExitOffset() {
        return exitOffsets.isEmpty() ? BlockVector3.ZERO : exitOffsets.get(0);
    }

    public List<BlockVector3> getExitOffsets() {
        return exitOffsets;
    }

    public List<BlockVector3> getMobMarkers() {
        return mobMarkers;
    }

    public List<BlockVector3> getLootMarkers() {
        return lootMarkers;
    }

    public BlockVector3 getMinPoint() {
        return minPoint;
    }

    public BlockVector3 getMaxPoint() {
        return maxPoint;
    }

    public int getExitDirection(BlockVector3 exit) {
        // 'exit' is already relative to Entry (0,0,0).
        int dx = exit.getX();
        int dz = exit.getZ();

        if (Math.abs(dx) >= Math.abs(dz)) {
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