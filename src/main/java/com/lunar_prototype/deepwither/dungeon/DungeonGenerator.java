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
        if (!configFile.exists()) {
            Deepwither.getInstance().getLogger().severe("Config not found: " + configFile.getAbsolutePath());
            return;
        }
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

                File schemFile = new File(dungeonFolder, fileName);
                if (schemFile.exists()) {
                    scanPartMarkers(part, schemFile);
                } else {
                    Deepwither.getInstance().getLogger().warning("Schematic file not found: " + fileName);
                }

                partList.add(part);
            }
        }
    }

    private void scanPartMarkers(DungeonPart part, File file) {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null)
            return;

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            part.scanMarkers(clipboard);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 生成のメイン処理 (一本道)
     * Start(Entrance) -> Hallway * count -> End(Entrance)
     */
    public void generateStraight(World world, int hallwayCount, int rotation) {
        Deepwither.getInstance().getLogger().info("=== 生成開始: Straight Dungeon (Rot:" + rotation + ") ===");

        // 最初の基準点 (ここに入口のGold Blockが重なるように配置される)
        Location currentAnchor = new Location(world, 0, 64, 0);

        // --- 1. START (Entrance) ---
        DungeonPart entrancePart = findPartByType("ENTRANCE");
        if (entrancePart != null) {
            Deepwither.getInstance().getLogger().info("> Placing Start (ENTRANCE)");
            currentAnchor = pastePart(world, currentAnchor, entrancePart, rotation);
        } else {
            Deepwither.getInstance().getLogger().warning("Type 'ENTRANCE' not found! Skipping start.");
        }

        // --- 2. MIDDLE (Hallways) ---
        DungeonPart hallwayPart = findPartByType("HALLWAY");
        if (hallwayPart != null) {
            for (int i = 0; i < hallwayCount; i++) {
                Deepwither.getInstance().getLogger().info("> Placing Hallway #" + (i + 1));
                currentAnchor = pastePart(world, currentAnchor, hallwayPart, rotation);
            }
        } else {
            Deepwither.getInstance().getLogger().warning("Type 'HALLWAY' not found! Skipping hallways.");
        }

        // --- 3. END (Entrance as Exit) ---
        // 終端としてもう一度 ENTRANCE を置く (あるいは EXIT タイプがあればそれを使う)
        if (entrancePart != null) {
            Deepwither.getInstance().getLogger().info("> Placing End (ENTRANCE)");
            // 最後の戻り値(Anchor)は使わないので無視してOK
            // End Entrance should be rotated 180 degrees relative to the path
            pastePart(world, currentAnchor, entrancePart, rotation + 180);
        }

        Deepwither.getInstance().getLogger().info("=== 生成完了 ===");
    }

    /**
     * パーツを貼り付けて、次の接続点(Anchor)を返す
     * * ロジック:
     * PreviousAnchor == PasteOrigin + RotatedEntryOffset
     * よって、
     * PasteOrigin = PreviousAnchor - RotatedEntryOffset
     * * NextAnchor = PasteOrigin + RotatedExitOffset
     */
    private Location pastePart(World world, Location anchor, DungeonPart part, int rotation) {
        File schemFile = new File(dungeonFolder, part.getFileName());
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);

        if (format == null) {
            Deepwither.getInstance().getLogger().severe("Format invalid: " + part.getFileName());
            return anchor;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(schemFile))) {
            Clipboard clipboard = reader.read();

            // 1. オフセット計算
            BlockVector3 rotatedEntry = part.getRotatedEntryOffset(rotation);
            BlockVector3 rotatedExit = part.getRotatedExitOffset(rotation);

            Deepwither.getInstance().getLogger()
                    .info(String.format("[%s] (ObjID:%d) Rot:%d | EntryOffset:%s -> Rotated:%s",
                            part.getFileName(), System.identityHashCode(part), rotation, part.getEntryOffset(),
                            rotatedEntry));

            // 2. 貼り付け基準点 (Paste Origin) の計算
            // アンカー位置に、このパーツの「入口(Gold)」が重なるように座標を引く
            double pasteX = anchor.getX() - rotatedEntry.getX();
            double pasteY = anchor.getY() - rotatedEntry.getY();
            double pasteZ = anchor.getZ() - rotatedEntry.getZ();

            BlockVector3 pasteVector = BlockVector3.at(pasteX, pasteY, pasteZ);
            Deepwither.getInstance().getLogger().info(String.format("  -> Anchor:%s | PasteOrigin:%s",
                    anchor.toVector(), pasteVector));

            // 3. WorldEdit で貼り付け
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);

                // ここでY軸回転を指定
                holder.setTransform(new AffineTransform().rotateY(rotation));

                Operation operation = holder
                        .createPaste(editSession)
                        .to(pasteVector)
                        .ignoreAirBlocks(true)
                        .build();
                Operations.complete(operation);
            }

            // Remove marker blocks (Gold/Iron)
            // Calculate world Key positions
            BlockVector3 worldEntryPos = pasteVector.add(rotatedEntry);
            BlockVector3 worldExitPos = pasteVector.add(rotatedExit);

            removeMarker(world, worldEntryPos, org.bukkit.Material.GOLD_BLOCK);
            removeMarker(world, worldExitPos, org.bukkit.Material.IRON_BLOCK);

            // 4. 次の接続点 (Next Anchor) の計算
            // 貼り付け基準点(Origin) + 出口オフセット(Iron)
            Location nextAnchor = new Location(world,
                    pasteX + rotatedExit.getX(),
                    pasteY + rotatedExit.getY(),
                    pasteZ + rotatedExit.getZ());

            Deepwither.getInstance().getLogger().info(String.format("  -> ExitOffset:%s -> Rotated:%s",
                    part.getExitOffset(), rotatedExit));
            Deepwither.getInstance().getLogger().info("  -> Next Anchor: " + nextAnchor.toVector());

            return nextAnchor;

        } catch (Exception e) {
            e.printStackTrace();
            return anchor; // エラー時は動かさない
        }
    }

    private void removeMarker(World world, BlockVector3 pos, org.bukkit.Material expectedType) {
        Location loc = new Location(world, pos.getX(), pos.getY(), pos.getZ());
        if (loc.getBlock().getType() == expectedType) {
            loc.getBlock().setType(org.bukkit.Material.AIR);
            // Deepwither.getInstance().getLogger().info("Removed marker at " + pos);
        }
    }

    private DungeonPart findPartByType(String type) {
        return partList.stream()
                .filter(p -> p.getType().equals(type))
                .findFirst()
                .orElse(null);
    }
}