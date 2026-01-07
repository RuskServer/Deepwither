package com.lunar_prototype.deepwither.dungeon;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class SchematicUtil {

    /**
     * 指定した場所にSchematicファイルを貼り付けます。
     * (空気ブロックは無視して貼り付けます)
     *
     * @param loc  貼り付けの基準位置 (Bukkit Location)
     * @param file 読み込む .schem ファイル
     */
    public static void paste(Location loc, File file) {
        paste(loc, file, 0); // 回転なしで呼び出し
    }

    /**
     * 回転を指定してSchematicを貼り付けます。
     *
     * @param loc      貼り付けの基準位置
     * @param file     .schem ファイル
     * @param rotation 回転角度 (0, 90, 180, 270)
     */
    public static void paste(Location loc, File file, int rotation) {
        if (!file.exists()) {
            System.err.println("[SchematicUtil] File not found: " + file.getAbsolutePath());
            return;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            System.err.println("[SchematicUtil] Unknown format for file: " + file.getName());
            return;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();

            // FAWEのEditSessionを作成 (高速処理用)
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(loc.getWorld()))) {

                // クリップボードホルダーを作成し、必要なら回転させる
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                if (rotation != 0) {
                    holder.setTransform(new AffineTransform().rotateY(rotation));
                }

                // 貼り付け操作の構築
                Operation operation = holder
                        .createPaste(editSession)
                        .to(BlockVector3.at(loc.getX(), loc.getY(), loc.getZ()))
                        .ignoreAirBlocks(true) // 既存ブロックを空気で消さない設定 (必要に応じてfalse)
                        .build();

                // 実行
                Operations.complete(operation);
            }

        } catch (IOException | WorldEditException e) {
            e.printStackTrace();
        }
    }
}