package com.lunar_prototype.deepwither.dungeon.instance;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DeepwitherPartyAPI;
import com.lunar_prototype.deepwither.dungeon.DungeonGenerator;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PvPvEDungeonManager implements IManager {

    private final Deepwither plugin;
    // 1つのインスタンスに収容する最大人数
    private static final int MAX_PLAYERS_PER_INSTANCE = 3;

    public PvPvEDungeonManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
    }

    @Override
    public void shutdown() {
    }

    // インスタンスIDごとのスポーン地点データ
    private final Map<String, List<SpawnPoint>> instanceSpawnPoints = new HashMap<>();

    /**
     * スポーン地点の優先度を管理する内部クラス
     */
    private static class SpawnPoint {
        Location location;
        long lastUsedTime; // 最後に使われた時間
        int useCount; // 使用回数（優先度の判定に使用）

        SpawnPoint(Location loc) {
            this.location = loc;
            this.lastUsedTime = 0;
            this.useCount = 0;
        }
    }

    public void enterPvPvEDungeon(Player player, String type, String difficulty) {
        // 1. 既存のワールドを探すロジック (省略)
        DungeonInstance existing = findMatch(type, difficulty, 1);

        if (existing != null) {
            // 2. 既存のワールドへ乱入
            spawnPlayerInInstance(player, existing);
        } else {
            // 3. 新規ワールド作成
            createNewMatch(player, type, difficulty);
        }
    }

    /**
     * 条件に合う既存のダンジョンインスタンスを検索する
     * * @param type ダンジョンの種類 (例: "catacombs")
     * 
     * @param difficulty 難易度 (例: "hard")
     * @param teamSize   参入しようとしているパーティーの人数
     * @return 合致するインスタンス。見つからない場合は null
     */
    private DungeonInstance findMatch(String type, String difficulty, int teamSize) {
        // DungeonInstanceManagerから現在稼働中の全インスタンスを取得
        // (DungeonInstanceManagerに getActiveInstances() メソッドが必要)
        Map<String, DungeonInstance> activeInstances = DungeonInstanceManager.getInstance().getActiveInstances();

        for (DungeonInstance inst : activeInstances.values()) {
            // 1. ダンジョンの種類と難易度が一致するかチェック
            // ※ DungeonInstanceに getType() と getDifficulty() を実装しておく必要があります
            if (!inst.getType().equalsIgnoreCase(type) || !inst.getDifficulty().equalsIgnoreCase(difficulty)) {
                continue;
            }

            // 2. 空き容量のチェック
            // 現在の人数 + 新しく入る人数 が 最大収容人数(例: 12人)以下か
            int currentPlayers = inst.getPlayers().size();
            if (currentPlayers + teamSize <= MAX_PLAYERS_PER_INSTANCE) { // 12は定数化を推奨
                return inst;
            }
        }

        // 適切なインスタンスが見つからなかった
        return null;
    }

    private void createNewMatch(Player host, String type, String difficulty) {
        // DungeonInstanceManagerのインフラ機能だけを借りる
        String worldName = "pvpve_" + System.currentTimeMillis();

        // 1. 空のワールド（Void）を作成
        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generatorSettings(
                "{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\"}");
        creator.generateStructures(false);

        World world = creator.createWorld();
        if (world == null) {
            host.sendMessage(ChatColor.RED + "ワールド生成に失敗しました。");
            return;
        }

        // ゲームルールの設定
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, false); // Keep Inventory有効
        world.setTime(18000); // 深夜

        // Generatorを回す (非同期)
        DungeonGenerator generator = new DungeonGenerator(type, difficulty);

        host.sendMessage(ChatColor.YELLOW + "ダンジョン生成中... (数秒お待ちください)");

        // 非同期生成のコールバックで処理を続行
        generator.generateBranchingAsync(world, 0, (spawns) -> {
            if (spawns.isEmpty()) {
                host.sendMessage(ChatColor.RED + "エラー: ダンジョン生成に失敗しました (スポーン地点なし)");
                // 失敗時のワールド削除処理なども必要であれば追加
                return;
            }

            // インスタンスを登録
            DungeonInstance inst = new DungeonInstance(worldName, world, type, difficulty);

            // ★修正: DungeonInstanceManager にも登録する (これがないと ExtractionManager が見つけられない)
            DungeonInstanceManager.getInstance().registerInstance(inst);

            // Spawner情報を渡してライフサイクル(リスポーン)開始
            inst.setSpawners(generator.getPendingSpawners());
            inst.startLifeCycle();

            registerSpawns(inst.getInstanceId(), spawns);

            Deepwither.getInstance().getDungeonExtractionManager().registerExtractionTask(inst.getInstanceId(), spawns);

            spawnPlayerInInstance(host, inst);
        });
    }

    private void spawnPlayerInInstance(Player player, DungeonInstance inst) {
        // ランダムな地点を選んで、使用済みとしてリストから消す（あるいは優先度を下げる）
        spawnInOptimalLocation(player, inst);

        // ★修正: プレイヤーをInstanceManagerに登録する (leaveDungeonなどで必要)
        DungeonInstanceManager.getInstance().registerPlayer(player, inst.getInstanceId());

        player.sendMessage(ChatColor.RED + "警告: 他の探索者が付近にいる可能性があります。");
    }

    /**
     * 最適なスポーン地点を選定してテレポートさせる
     */
    private void spawnInOptimalLocation(Player player, DungeonInstance inst) {
        List<SpawnPoint> points = instanceSpawnPoints.get(inst.getInstanceId());
        if (points == null || points.isEmpty())
            return;

        // 1. 半径64ブロック以内にプレイヤーがいない地点をフィルタリング
        List<SpawnPoint> safePoints = points.stream()
                .filter(p -> p.location.getWorld().getNearbyEntities(p.location, 64, 64, 64).stream()
                        .noneMatch(e -> e instanceof Player))
                .collect(Collectors.toList());

        SpawnPoint selected;

        if (!safePoints.isEmpty()) {
            // 2. 安全な地点がある場合：その中で「使用回数が最も少ない」ものを選択
            selected = safePoints.stream()
                    .min(Comparator.comparingInt(p -> p.useCount))
                    .orElse(safePoints.get(0));
        } else {
            // 3. 安全な地点がない（全員が密集している）場合：
            // 全地点の中から「最も使用回数が少なく、かつ最後に使われてから時間が経っている」ものを選択
            selected = points.stream()
                    .min(Comparator.comparingInt((SpawnPoint p) -> p.useCount)
                            .thenComparingLong(p -> p.lastUsedTime))
                    .orElse(points.get(0));
        }

        // 使用データの更新（優先度を実質的に下げる処理）
        selected.useCount++;
        selected.lastUsedTime = System.currentTimeMillis();

        player.teleport(selected.location);
    }

    /**
     * ダンジョン生成時にスポーン地点を初期登録するメソッド
     */
    public void registerSpawns(String instanceId, List<Location> locations) {
        List<SpawnPoint> spawnPoints = locations.stream()
                .map(SpawnPoint::new)
                .collect(Collectors.toList());
        // 最初の選択が偏らないようにシャッフルしておく
        Collections.shuffle(spawnPoints);
        instanceSpawnPoints.put(instanceId, spawnPoints);
    }
}