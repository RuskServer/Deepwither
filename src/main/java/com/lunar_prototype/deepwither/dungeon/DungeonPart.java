package com.lunar_prototype.deepwither.dungeon;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockTypes;

public class DungeonPart {
    private final String fileName;
    private final String type;
    private final int length; // 予備（マーカーがない場合のフォールバック用）

    // マーカーからの相対座標 (Originからのオフセット)
    private BlockVector3 entryOffset; // 金ブロック(入口)
    private BlockVector3 exitOffset;  // 鉄ブロック(出口)

    public DungeonPart(String fileName, String type, int length) {
        this.fileName = fileName;
        this.type = type;
        this.length = length;
    }

    /**
     * Schematicの内容をスキャンして、金ブロックと鉄ブロックの位置を記録する
     */
    public void scanMarkers(Clipboard clipboard) {
        BlockVector3 origin = clipboard.getOrigin();

        for (BlockVector3 pos : clipboard.getRegion()) {
            var block = clipboard.getFullBlock(pos);

            // 金ブロックを入口として認識
            if (block.getBlockType().equals(BlockTypes.GOLD_BLOCK)) {
                this.entryOffset = pos.subtract(origin);
            }
            // 鉄ブロックを出口として認識
            else if (block.getBlockType().equals(BlockTypes.IRON_BLOCK)) {
                this.exitOffset = pos.subtract(origin);
            }
        }
    }

    // Getter
    public String getFileName() { return fileName; }
    public String getType() { return type; }
    public int getLength() { return length; }
    public BlockVector3 getEntryOffset() { return entryOffset; }
    public BlockVector3 getExitOffset() { return exitOffset; }

    /**
     * 入口から出口までのベクトル差分を返す
     * これにより、入口をどこに合わせれば出口がどこに来るか計算できる
     */
    public BlockVector3 getConnectionVector() {
        if (entryOffset == null || exitOffset == null) {
            // マーカーがない場合は、従来のlength(Z軸方向)を返す
            return BlockVector3.at(0, 0, length);
        }
        return exitOffset.subtract(entryOffset);
    }
}
