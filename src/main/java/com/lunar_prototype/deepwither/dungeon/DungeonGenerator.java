package com.lunar_prototype.deepwither.dungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
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
     * マーカー座標を利用して一直線のダンジョンを生成する
     */
    public void generateStraight(World world, int hallwayCount) {
        // 次のパーツの「入口」を繋ぐべき座標 (アンカー)
        Location nextAnchor = new Location(world, 0, 64, 0);

        // 1. Entrance を配置
        DungeonPart entrance = findPartByType("ENTRANCE");
        if (entrance != null) {
            // 配置実行
            nextAnchor = pasteAndGetNextAnchor(nextAnchor, entrance);
        }

        // 2. Hallway を指定数分ループで配置
        DungeonPart hallway = findPartByType("HALLWAY");
        if (hallway != null) {
            for (int i = 0; i < hallwayCount; i++) {
                nextAnchor = pasteAndGetNextAnchor(nextAnchor, hallway);
            }
        }

        Deepwither.getInstance().getLogger().info(dungeonName + " の生成が完了しました。");
    }

    /**
     * 指定したアンカー地点に入口を合わせて貼り付け、次の出口アンカーを返す
     */
    private Location pasteAndGetNextAnchor(Location anchor, DungeonPart part) {
        File file = new File(dungeonFolder, part.getFileName());

        // 貼り付け位置(Origin)の計算:
        // アンカー地点から、パーツ自体の入口オフセット分を差し引いた場所
        Location pasteLoc;
        if (part.getEntryOffset() != null) {
            pasteLoc = anchor.clone().subtract(
                    part.getEntryOffset().getX(),
                    part.getEntryOffset().getY(),
                    part.getEntryOffset().getZ()
            );
        } else {
            // マーカーがない場合のフォールバック
            pasteLoc = anchor.clone();
        }

        // 貼り付け実行
        SchematicUtil.paste(pasteLoc, file);

        // 次のアンカー地点を計算して返す
        if (part.getExitOffset() != null) {
            // 貼り付けた基準点に、出口オフセットを足した場所
            return pasteLoc.clone().add(
                    part.getExitOffset().getX(),
                    part.getExitOffset().getY(),
                    part.getExitOffset().getZ()
            );
        } else {
            // マーカーがない場合は従来のZ軸加算
            return anchor.clone().add(0, 0, part.getLength());
        }
    }

    private DungeonPart findPartByType(String type) {
        return partList.stream().filter(p -> p.getType().equals(type)).findFirst().orElse(null);
    }
}