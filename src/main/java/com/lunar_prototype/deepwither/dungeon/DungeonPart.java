package com.lunar_prototype.deepwither.dungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.world.block.BlockTypes;

public class DungeonPart {
    private final String fileName;
    private final String type;
    private final int length;

    // Origin(保存時の立ち位置)からの相対座標
    private BlockVector3 entryOffset = BlockVector3.ZERO;
    private BlockVector3 exitOffset = BlockVector3.ZERO;

    public DungeonPart(String fileName, String type, int length) {
        this.fileName = fileName;
        this.type = type;
        this.length = length;
    }

    public void scanMarkers(Clipboard clipboard) {
        BlockVector3 origin = clipboard.getOrigin();
        boolean foundEntry = false;
        boolean foundExit = false;

        Deepwither.getInstance().getLogger().info("[" + fileName + "] Scanning markers... Origin: " + origin);

        for (BlockVector3 pos : clipboard.getRegion()) {
            var block = clipboard.getFullBlock(pos);

            // 金ブロック (入口)
            if (block.getBlockType().equals(BlockTypes.GOLD_BLOCK)) {
                this.entryOffset = pos.subtract(origin);
                foundEntry = true;
                Deepwither.getInstance().getLogger().info("  -> Found ENTRY (Gold). RelOffset: " + entryOffset);
            }
            // 鉄ブロック (出口)
            else if (block.getBlockType().equals(BlockTypes.IRON_BLOCK)) {
                this.exitOffset = pos.subtract(origin);
                foundExit = true;
                Deepwither.getInstance().getLogger().info("  -> Found EXIT (Iron). RelOffset: " + exitOffset);
            }
        }

        if (!foundEntry) {
            Deepwither.getInstance().getLogger().warning("  [!] WARNING: No ENTRY (Gold Block) found in " + fileName + ". Using (0,0,0).");
        }
        if (!foundExit && !type.equals("ROOM")) { // ROOMタイプ等は出口がなくてもいいかも
            Deepwither.getInstance().getLogger().warning("  [!] WARNING: No EXIT (Iron Block) found in " + fileName + ". Using (0,0,0).");
        }
    }

    /**
     * 回転後の「入口」オフセットを取得
     */
    public BlockVector3 getRotatedEntryOffset(int rotation) {
        return transformVector(entryOffset, rotation);
    }

    /**
     * 回転後の「出口」オフセットを取得
     */
    public BlockVector3 getRotatedExitOffset(int rotation) {
        return transformVector(exitOffset, rotation);
    }

    private BlockVector3 transformVector(BlockVector3 vec, int angle) {
        if (vec == null) return BlockVector3.ZERO;
        if (angle == 0) return vec;

        AffineTransform transform = new AffineTransform().rotateY(angle);
        com.sk89q.worldedit.math.Vector3 result = transform.apply(vec.toVector3());

        // 四捨五入して整数座標へ
        return BlockVector3.at(
                Math.round(result.getX()),
                Math.round(result.getY()),
                Math.round(result.getZ())
        );
    }

    public String getFileName() { return fileName; }
    public String getType() { return type; }
}