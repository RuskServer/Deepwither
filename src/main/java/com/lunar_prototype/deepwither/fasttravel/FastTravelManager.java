package com.lunar_prototype.deepwither.fasttravel;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@DependsOn({DatabaseManager.class})
public class FastTravelManager implements IManager, Listener {

    private final Deepwither plugin;
    private final Map<UUID, Map<String, FastTravelPoint>> unlockedPoints = new ConcurrentHashMap<>();
    private final Map<String, PointMetadata> metadataMap = new HashMap<>();
    
    private File configFile;
    private YamlConfiguration config;

    public FastTravelManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws Exception {
        this.configFile = new File(plugin.getDataFolder(), "fast_travel_points.yml");
        if (!configFile.exists()) {
            plugin.saveResource("fast_travel_points.yml", false);
        }
        loadMetadata();
        Bukkit.getPluginManager().registerEvents(this, plugin);

        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerData(player.getUniqueId());
        }
    }

    private void loadMetadata() {
        config = YamlConfiguration.loadConfiguration(configFile);
        metadataMap.clear();
        ConfigurationSection section = config.getConfigurationSection("points");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String name = section.getString(id + ".name", id);
            Material icon = Material.valueOf(section.getString(id + ".icon", "MAP").toUpperCase());
            metadataMap.put(id, new PointMetadata(name, icon));
        }
    }

    public void unlock(Player player, String regionId, Location location) {
        Map<String, FastTravelPoint> playerPoints = unlockedPoints.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        if (playerPoints.containsKey(regionId)) return;

        PointMetadata meta = metadataMap.getOrDefault(regionId, new PointMetadata(regionId, Material.MAP));
        FastTravelPoint point = new FastTravelPoint(regionId, meta.name, location.clone(), meta.icon);
        playerPoints.put(regionId, point);

        // DBに保存 (座標含む)
        plugin.get(DatabaseManager.class).runAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO player_fast_travel (uuid, point_id, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, regionId);
                ps.setString(3, location.getWorld().getName());
                ps.setDouble(4, location.getX());
                ps.setDouble(5, location.getY());
                ps.setDouble(6, location.getZ());
                ps.setFloat(7, location.getYaw());
                ps.setFloat(8, location.getPitch());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // 通知
        player.sendMessage(Component.text(">> ", NamedTextColor.AQUA)
                .append(Component.text("ファストトラベル地点 [", NamedTextColor.WHITE))
                .append(Component.text(point.getDisplayName(), NamedTextColor.YELLOW))
                .append(Component.text("] を解放しました！", NamedTextColor.WHITE)));
        
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.showTitle(Title.title(Component.empty(), Component.text("ファストトラベル地点を解放しました", NamedTextColor.YELLOW)));
    }

    public Collection<FastTravelPoint> getPlayerPoints(Player player) {
        Map<String, FastTravelPoint> map = unlockedPoints.get(player.getUniqueId());
        return map != null ? map.values() : Collections.emptyList();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayerData(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        unlockedPoints.remove(event.getPlayer().getUniqueId());
    }

    private void loadPlayerData(UUID uuid) {
        plugin.get(DatabaseManager.class).supplyAsync(conn -> {
            Map<String, FastTravelPoint> map = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM player_fast_travel WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("point_id");
                        String world = rs.getString("world");
                        double x = rs.getDouble("x");
                        double y = rs.getDouble("y");
                        double z = rs.getDouble("z");
                        float yaw = rs.getFloat("yaw");
                        float pitch = rs.getFloat("pitch");

                        Location loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
                        PointMetadata meta = metadataMap.getOrDefault(id, new PointMetadata(id, Material.MAP));
                        map.put(id, new FastTravelPoint(id, meta.name, loc, meta.icon));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return map;
        }).thenAccept(map -> {
            if (map != null) unlockedPoints.put(uuid, map);
        });
    }

    private static class PointMetadata {
        final String name;
        final Material icon;
        PointMetadata(String name, Material icon) {
            this.name = name;
            this.icon = icon;
        }
    }
}
