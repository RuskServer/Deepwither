package com.lunar_prototype.deepwither.dungeon;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;

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

        // List<? extends Map> を受け取れるようにする
        List<Map<?, ?>> maps = config.getMapList("parts");

        for (Map<?, ?> rawMap : maps) {
            // ここで一旦 String, Object のマップとして扱う
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;

            // getOrDefault が使えるようになります
            int length = ((Number) map.getOrDefault("length", 10)).intValue();

            String fileName = (String) map.get("file");
            String type = (String) map.get("type");

            if (fileName != null && type != null) {
                partList.add(new DungeonPart(
                        fileName,
                        type.toUpperCase(),
                        length
                ));
            }
        }
    }

    /**
     * 一直線のダンジョンを生成する
     * @param world 生成対象のインスタンスワールド
     * @param hallwayCount 通路をいくつ繋げるか
     */
    public void generateStraight(World world, int hallwayCount) {
        // 開始地点 (インスタンスワールドの真ん中あたり)
        Location currentLoc = new Location(world, 0, 64, 0);

        // 1. Entrance を配置
        DungeonPart entrance = findPartByType("ENTRANCE");
        if (entrance != null) {
            paste(currentLoc, entrance);
            // 次のパーツの開始地点をズラす (Z軸方向に伸ばすと仮定)
            currentLoc.add(0, 0, entrance.getLength());
        }

        // 2. Hallway を指定数分ループで配置
        DungeonPart hallway = findPartByType("HALLWAY");
        if (hallway != null) {
            for (int i = 0; i < hallwayCount; i++) {
                paste(currentLoc, hallway);
                currentLoc.add(0, 0, hallway.getLength());
            }
        }

        Deepwither.getInstance().getLogger().info(dungeonName + " の生成が完了しました。");
    }

    private void paste(Location loc, DungeonPart part) {
        File file = new File(dungeonFolder, part.getFileName());
        if (!file.exists()) return;

        // 前述したFAWEの貼り付けメソッドを呼び出す（別クラスにユーティリティ化しておくと便利）
        SchematicUtil.paste(loc, file);
    }

    private DungeonPart findPartByType(String type) {
        return partList.stream().filter(p -> p.getType().equals(type)).findFirst().orElse(null);
    }
}