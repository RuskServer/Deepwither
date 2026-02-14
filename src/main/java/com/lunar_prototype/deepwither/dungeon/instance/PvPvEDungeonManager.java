package com.lunar_prototype.deepwither.dungeon.instance;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.dungeon.DungeonGenerator;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

@DependsOn({DungeonInstanceManager.class})
public class PvPvEDungeonManager implements IManager {

    private final Deepwither plugin;
    private static final int MAX_PLAYERS_PER_INSTANCE = 3;

    public PvPvEDungeonManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    private final Map<String, List<SpawnPoint>> instanceSpawnPoints = new HashMap<>();

    private static class SpawnPoint {
        Location location;
        long lastUsedTime;
        int useCount;

        SpawnPoint(Location loc) {
            this.location = loc;
            this.lastUsedTime = 0;
            this.useCount = 0;
        }
    }

    public void enterPvPvEDungeon(Player player, String type, String difficulty) {
        DungeonInstance existing = findMatch(type, difficulty, 1);
        if (existing != null) {
            spawnPlayerInInstance(player, existing);
        } else {
            createNewMatch(player, type, difficulty);
        }
    }

    private DungeonInstance findMatch(String type, String difficulty, int teamSize) {
        Map<String, DungeonInstance> activeInstances = DungeonInstanceManager.getInstance().getActiveInstances();
        for (DungeonInstance inst : activeInstances.values()) {
            if (!inst.getType().equalsIgnoreCase(type) || !inst.getDifficulty().equalsIgnoreCase(difficulty)) {
                continue;
            }
            int currentPlayers = inst.getPlayers().size();
            if (currentPlayers + teamSize <= MAX_PLAYERS_PER_INSTANCE) {
                return inst;
            }
        }
        return null;
    }

    private void createNewMatch(Player host, String type, String difficulty) {
        String worldName = "pvpve_" + System.currentTimeMillis();

        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generatorSettings(
                "{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\"}");
        creator.generateStructures(false);

        World world = creator.createWorld();
        if (world == null) {
            host.sendMessage(Component.text("ワールド生成に失敗しました。", NamedTextColor.RED));
            return;
        }

        setWorldGuardPvP(world, true);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setTime(18000);

        DungeonGenerator generator = new DungeonGenerator(type, difficulty);
        host.sendMessage(Component.text("ダンジョン生成中... (数秒お待ちください)", NamedTextColor.YELLOW));

        generator.generateBranchingAsync(world, 0, (spawns) -> {
            if (spawns.isEmpty()) {
                host.sendMessage(Component.text("エラー: ダンジョン生成に失敗しました (スポーン地点なし)", NamedTextColor.RED));
                return;
            }

            DungeonInstance inst = new DungeonInstance(worldName, world, type, difficulty);
            DungeonInstanceManager.getInstance().registerInstance(inst);
            inst.startLifeCycle();
            registerSpawns(inst.getInstanceId(), spawns);
            Deepwither.getInstance().getDungeonExtractionManager().registerExtractionTask(inst.getInstanceId(), spawns);
            spawnPlayerInInstance(host, inst);
        });
    }

    private void setWorldGuardPvP(World world, boolean allow) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(world));

            if (regions != null) {
                ProtectedRegion globalRegion = regions.getRegion("__global__");
                if (globalRegion != null) {
                    globalRegion.setFlag(Flags.PVP, allow ? StateFlag.State.ALLOW : StateFlag.State.DENY);
                    plugin.getLogger().info("[WorldGuard] Set PvP to " + (allow ? "ALLOW" : "DENY") + " in " + world.getName());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("WorldGuardのフラグ設定中にエラーが発生しました: " + e.getMessage());
        }
    }

    private void spawnPlayerInInstance(Player player, DungeonInstance inst) {
        spawnInOptimalLocation(player, inst);
        DungeonInstanceManager.getInstance().registerPlayer(player, inst.getInstanceId());
        player.sendMessage(Component.text("警告: 他の探索者が付近にいる可能性があります。", NamedTextColor.RED));
    }

    private void spawnInOptimalLocation(Player player, DungeonInstance inst) {
        List<SpawnPoint> points = instanceSpawnPoints.get(inst.getInstanceId());
        if (points == null || points.isEmpty())
            return;

        List<SpawnPoint> safePoints = points.stream()
                .filter(p -> p.location.getWorld().getNearbyEntities(p.location, 64, 64, 64).stream()
                        .noneMatch(e -> e instanceof Player))
                .collect(Collectors.toList());

        SpawnPoint selected;

        if (!safePoints.isEmpty()) {
            selected = safePoints.stream()
                    .min(Comparator.comparingInt(p -> p.useCount))
                    .orElse(safePoints.get(0));
        } else {
            selected = points.stream()
                    .min(Comparator.comparingInt((SpawnPoint p) -> p.useCount)
                            .thenComparingLong(p -> p.lastUsedTime))
                    .orElse(points.get(0));
        }

        selected.useCount++;
        selected.lastUsedTime = System.currentTimeMillis();
        player.teleport(selected.location);
    }

    public void registerSpawns(String instanceId, List<Location> locations) {
        List<SpawnPoint> spawnPoints = locations.stream()
                .map(SpawnPoint::new)
                .collect(Collectors.toList());
        Collections.shuffle(spawnPoints);
        instanceSpawnPoints.put(instanceId, spawnPoints);
    }
}
