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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DungeonGenerator {
    private final String dungeonName;
    private final List<DungeonPart> partList = new ArrayList<>();
    private final File dungeonFolder;

    public DungeonGenerator(String dungeonName) {
        this.dungeonName = dungeonName;
        this.dungeonFolder = new File(Deepwither.getInstance().getDataFolder(), "dungeons/" + dungeonName);
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(Deepwither.getInstance().getDataFolder(), "dungeons/" + dungeonName + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        List<Map<?, ?>> maps = config.getMapList("parts");

        for (Map<?, ?> rawMap : maps) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;

            int length = ((Number) map.getOrDefault("length", 10)).intValue();
            String fileName = (String) map.get("file");
            String type = (String) map.get("type");

            if (fileName != null && type != null) {
                DungeonPart part = new DungeonPart(fileName, type.toUpperCase(), length);

                // --- 追加: マーカーのスキャン ---
                File schemFile = new File(dungeonFolder, fileName);
                if (schemFile.exists()) {
                    scanPartMarkers(part, schemFile);
                }

                partList.add(part);
            }
        }
    }

    /**
     * Schematicファイルを一時的に読み込んでマーカーをスキャンする
     */
    private void scanPartMarkers(DungeonPart part, File file) {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) return;

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            part.scanMarkers(clipboard); // 前のステップで作ったscanMarkersを呼び出し
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 生成のメイン処理
     */
    public void generateStraight(World world, int hallwayCount, int rotation) {
        // 最初の基準点 (ここに入口が来る)
        Location currentAnchor = new Location(world, 0, 64, 0);

        // 1. Entrance
        DungeonPart entrance = findPartByType("ENTRANCE");
        if (entrance != null) {
            currentAnchor = pasteAndGetNextAnchor(world,currentAnchor, entrance, rotation);
        }

        // 2. Hallways
        DungeonPart hallway = findPartByType("HALLWAY");
        if (hallway != null) {
            for (int i = 0; i < hallwayCount; i++) {
                currentAnchor = pasteAndGetNextAnchor(world,currentAnchor, hallway, rotation);
            }
        }

        Deepwither.getInstance().getLogger().info("生成完了");
    }

    /**
     * パーツを貼り付けて、次の接続点(Anchor)を返す
     */
    private Location pasteAndGetNextAnchor(World world, Location anchor, DungeonPart part, int rotation) {
        File schemFile = new File(dungeonFolder, part.getFileName());
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);

        if (format == null) return anchor;

        try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
            Clipboard clipboard = reader.read();

            // 1. パーツ側で計算された「回転後の入口オフセット」を取得
            //    (DungeonPart内でAffineTransformを使って計算されるので正確です)
            BlockVector3 rotatedEntry = part.getRotatedEntryOffset(rotation);
            BlockVector3 rotatedExit = part.getRotatedExitOffset(rotation);

            // 2. 貼り付け位置(Originを置く場所)を決定
            //    「現在のアンカー」から「回転後の入口分」を引き算することで、
            //    アンカーの位置にちょうど入口が重なるようにします。
            Location pasteLoc = anchor.clone().subtract(
                    rotatedEntry.getX(),
                    rotatedEntry.getY(),
                    rotatedEntry.getZ()
            );

            // 3. 実際の貼り付け処理
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);

                // ★重要: ここでも同じ角度で回転変換をセットする
                holder.setTransform(new AffineTransform().rotateY(rotation));

                Operation operation = holder
                        .createPaste(editSession)
                        .to(BlockVector3.at(pasteLoc.getX(), pasteLoc.getY(), pasteLoc.getZ()))
                        .ignoreAirBlocks(true)
                        .build();
                Operations.complete(operation);
            }

            // 4. 次のアンカー(出口)を計算
            //    「貼り付け基準点」に「回転後の出口分」を足す
            return pasteLoc.clone().add(
                    rotatedExit.getX(),
                    rotatedExit.getY(),
                    rotatedExit.getZ()
            );

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) { // FAWE/WorldEdit exception
            e.printStackTrace();
        }

        return anchor;
    }

    private DungeonPart findPartByType(String type) {
        return partList.stream().filter(p -> p.getType().equals(type)).findFirst().orElse(null);
    }
}