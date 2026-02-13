package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({DungeonInstanceManager.class})
public class SafeZoneListener implements Listener, IManager {

    private final Deepwither plugin;
    private final Map<UUID, Location> safeZoneSpawns = new HashMap<>();
    private File safeZoneSpawnsFile;
    private FileConfiguration safeZoneSpawnsConfig;

    public SafeZoneListener(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        loadSafeZoneSpawns();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
        saveSafeZoneSpawns();
    }

    // --- Data Management Methods ---

    public Location getSafeZoneSpawn(UUID playerUUID) {
        return safeZoneSpawns.get(playerUUID);
    }

    public void setSafeZoneSpawn(UUID playerUUID, Location location) {
        safeZoneSpawns.put(playerUUID, location);
    }

    private void loadSafeZoneSpawns() {
        safeZoneSpawnsFile = new File(plugin.getDataFolder(), "safeZoneSpawns.yml");
        if (!safeZoneSpawnsFile.exists()) {
            safeZoneSpawnsFile.getParentFile().mkdirs();
            try {
                safeZoneSpawnsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        safeZoneSpawnsConfig = YamlConfiguration.loadConfiguration(safeZoneSpawnsFile);

        for (String key : safeZoneSpawnsConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                Location loc = safeZoneSpawnsConfig.getLocation(key);
                if (loc != null) {
                    safeZoneSpawns.put(uuid, loc);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in safeZoneSpawns.yml: " + key);
            }
        }
    }

    public void saveSafeZoneSpawns() {
        if (safeZoneSpawnsConfig == null || safeZoneSpawnsFile == null) return;

        for (Map.Entry<UUID, Location> entry : safeZoneSpawns.entrySet()) {
            safeZoneSpawnsConfig.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            safeZoneSpawnsConfig.save(safeZoneSpawnsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save safeZoneSpawns.yml!");
            e.printStackTrace();
        }
    }

    // プレイヤーがブロックを跨いでいない移動は無視する
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return;
        }

        boolean isOldInSafeZone = isSafeZone(from);
        boolean isNewInSafeZone = isSafeZone(to);

        // ----------------------------------------------------
        // ★ 1. セーフゾーンへの侵入をチェック
        // ----------------------------------------------------
        if (isNewInSafeZone && !isOldInSafeZone) {
            player.sendTitle(
                    ChatColor.AQUA + "セーフゾーン",
                    ChatColor.GRAY + "リスポーン地点を更新しました",
                    10, 70, 20
            );
            player.sendMessage(ChatColor.AQUA + ">> セーフゾーンに侵入しました。**リスポーン地点が現在地に設定されました。**");

            setSafeZoneSpawn(player.getUniqueId(), to);
            saveSafeZoneSpawns(); // 即座にファイルに保存
        }
        // ----------------------------------------------------
        // ★ 2. セーフゾーンからの退出をチェック (オプション)
        // ----------------------------------------------------
        else if (!isNewInSafeZone && isOldInSafeZone) {
            player.sendTitle(
                    ChatColor.RED + "危険区域",
                    ChatColor.GRAY + "",
                    10, 70, 20);
            player.sendMessage(ChatColor.RED + ">> 危険区域へ移動しました。");
        }
    }

    /**
     * 指定されたLocationが、名前に「safezone」を含むリージョン内にあるかを判定します。
     */
    private boolean isSafeZone(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        for (ProtectedRegion region : set) {
            if (region.getId().toLowerCase().contains("safezone")) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        Location safeZoneSpawn = getSafeZoneSpawn(playerUUID);

        com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager dim =
                com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager.getInstance();

        if (dim != null) {
            com.lunar_prototype.deepwither.dungeon.instance.DungeonInstance dInstance = dim.getPlayerInstance(playerUUID);

            if (dInstance != null) {
                if (dInstance.getWorld().getName().startsWith("pvpve_")) {
                    if (safeZoneSpawn != null) {
                        event.setRespawnLocation(safeZoneSpawn);
                    }
                    dInstance.removePlayer(player.getUniqueId());
                    Deepwither.getInstance().getRoguelikeBuffManager().clearBuffs(player);
                    player.sendMessage("§c§l[Dungeon] §r§c死亡したため、ダンジョンから追放されました。");
                    return;
                }

                if (player.getWorld().equals(dInstance.getWorld())) {
                    event.setRespawnLocation(new Location(dInstance.getWorld(), 0.5, 64, 0.5));
                    return;
                }
            }
        }

        if (safeZoneSpawn != null) {
            event.setRespawnLocation(safeZoneSpawn);
        }
    }
}