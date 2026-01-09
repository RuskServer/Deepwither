package com.lunar_prototype.deepwither.dungeon.instance;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.dungeon.DungeonGenerator;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DungeonInstanceManager implements IManager {

    private final Deepwither plugin;
    private final Map<String, DungeonInstance> activeInstances = new HashMap<>(); // Key: World Name (Instance ID)
    private final Map<UUID, String> playerInstanceMap = new HashMap<>(); // Key: Player UUID, Value: World Name

    private BukkitTask cleanupTask;
    private static DungeonInstanceManager instance;

    // 定数: インスタンスが空になってから削除するまでの時間 (ミリ秒) - 例: 5分
    private static final long REMOVE_THRESHOLD_MS = 5 * 60 * 1000;

    public DungeonInstanceManager(Deepwither plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static DungeonInstanceManager getInstance() {
        return instance;
    }

    @Override
    public void init() {
        // 1分ごとにクリーンアップタスクを実行
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupInactiveInstances, 1200L, 1200L);
        plugin.getLogger().info("DungeonInstanceManager initialized.");
    }

    @Override
    public void shutdown() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        // 全インスタンスを強制削除
        Set<String> keys = new HashSet<>(activeInstances.keySet());
        for (String key : keys) {
            unloadInstance(key);
        }
    }

    /**
     * 新しいダンジョンインスタンスを生成し、ホストプレイヤーを転送する
     */
    public void createInstance(Player host, String dungeonType) {
        UUID hostId = host.getUniqueId();
        // インスタンスID生成 (ワールド名)
        String worldName = "dw_inst_" + hostId.toString().substring(0, 8) + "_" + System.currentTimeMillis();

        plugin.getLogger().info("Generating dungeon instance: " + worldName + " type: " + dungeonType);

        // 既存のジェネレーターロジックを使用してワールド生成
        // 注意: DungeonGeneratorは既存のワールドに対して生成を行う設計になっているため、
        // 先に空のワールドを作成する必要がある。

        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generatorSettings(
                "{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\"}"); // Void
                                                                                                               // world
        creator.generateStructures(false);

        World world = creator.createWorld();
        if (world == null) {
            host.sendMessage(ChatColor.RED + "ワールド生成に失敗しました。");
            return;
        }

        // Gamerule設定
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false); // ダンジョン独自のスポーンを使うならFalse
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(18000); // Midnight

        // ダンジョン生成実行
        DungeonGenerator generator = new DungeonGenerator(dungeonType);
        // 生成設定: depth 10, startRotation 0 (仮)
        generator.generateBranching(world, 10, 0);

        // インスタンス管理に追加
        DungeonInstance dInstance = new DungeonInstance(worldName, world);
        activeInstances.put(worldName, dInstance);

        // ホストを参加させる
        joinDungeon(host, worldName);
    }

    /**
     * プレイヤーをダンジョンに参加させる
     */
    public void joinDungeon(Player player, String instanceId) {
        DungeonInstance dInstance = activeInstances.get(instanceId);
        if (dInstance == null) {
            player.sendMessage(ChatColor.RED + "そのダンジョンインスタンスは存在しません。");
            return;
        }

        // 既存のインスタンスから退出処理が必要ならここで行う
        if (playerInstanceMap.containsKey(player.getUniqueId())) {
            leaveDungeon(player);
        }

        // テレポート (Start: 0, 64, 0)
        Location startLoc = new Location(dInstance.getWorld(), 0.5, 64, 0.5);
        player.teleport(startLoc);

        // 管理データ更新
        dInstance.addPlayer(player.getUniqueId());
        playerInstanceMap.put(player.getUniqueId(), instanceId);

        player.sendMessage(ChatColor.GREEN + "ダンジョンに参加しました！");
    }

    /**
     * プレイヤーをダンジョンから退出させる
     */
    public void leaveDungeon(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerInstanceMap.containsKey(uuid))
            return;

        String instanceId = playerInstanceMap.get(uuid);
        DungeonInstance dInstance = activeInstances.get(instanceId);

        if (dInstance != null) {
            dInstance.removePlayer(uuid);
        }
        playerInstanceMap.remove(uuid);

        // SafeZone (Hub) へ転送
        Location safeSpawn = plugin.getSafeZoneSpawn(uuid);
        if (safeSpawn != null) {
            player.teleport(safeSpawn);
        } else {
            // デフォルトのスポーン地点 (server.properties or plugin default)
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        player.sendMessage(ChatColor.YELLOW + "ダンジョンから離脱しました。");
    }

    /**
     * 指定UUIDのプレイヤーが現在いるダンジョンインスタンスを取得
     */
    public DungeonInstance getPlayerInstance(UUID uuid) {
        String instanceId = playerInstanceMap.get(uuid);
        if (instanceId == null)
            return null;
        return activeInstances.get(instanceId);
    }

    /**
     * 定期クリーンアップタスク
     */
    private void cleanupInactiveInstances() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (DungeonInstance instance : activeInstances.values()) {
            if (instance.isEmpty()) {
                long emptyTime = instance.getLastEmptyTime();
                if (emptyTime != -1 && (now - emptyTime) > REMOVE_THRESHOLD_MS) {
                    toRemove.add(instance.getInstanceId());
                }
            }
        }

        for (String instanceId : toRemove) {
            plugin.getLogger().info("Cleaning up inactive dungeon instance: " + instanceId);
            unloadInstance(instanceId);
        }
    }

    /**
     * インスタンスのアンロードとワールド削除
     */
    public void unloadInstance(String worldName) {
        DungeonInstance dInstance = activeInstances.get(worldName);
        if (dInstance == null)
            return;

        // まだプレイヤーが残っている場合は強制退出
        for (UUID uuid : new HashSet<>(dInstance.getPlayers())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(ChatColor.RED + "ダンジョンインスタンスが閉鎖されました。");
                leaveDungeon(p);
            }
        }

        World world = dInstance.getWorld();
        if (world != null) {
            File worldFolder = world.getWorldFolder();
            Bukkit.unloadWorld(world, false); // Save false

            // フォルダ削除 (非同期推奨だが、ここでは簡易実装)
            // BukkitのUnload直後はロックが残る場合があるため、少し遅延させるか、FileUtilsを使う
            try {
                FileUtils.deleteDirectory(worldFolder);
                plugin.getLogger().info("Deleted world folder: " + worldFolder.getName());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to delete world folder: " + worldName);
                e.printStackTrace();
            }
        }

        activeInstances.remove(worldName);
    }
}
