package com.lunar_prototype.deepwither.layer_move;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@DependsOn({})
public class LayerMoveManager implements IManager {

    private final Map<String, WarpData> warps = new HashMap<>();
    private File file;
    private YamlConfiguration config;
    private final Deepwither plugin;

    public LayerMoveManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        load(plugin.getDataFolder());
    }

    @Override
    public void shutdown() {}

    public void load(File dataFolder) {
        warps.clear();
        file = new File(dataFolder, "layer_move.yml");
        if (!file.exists()) {
            plugin.saveResource("layer_move.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        if (!config.contains("version")) {
            migrateConfig();
        }

        ConfigurationSection section = config.getConfigurationSection("warps");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            WarpData data = new WarpData();
            data.id = key;
            data.floorName = section.getString(key + ".floor_name");
            data.subTitle = section.getString(key + ".sub_title");

            String originStr = section.getString(key + ".origin");
            if (originStr != null) {
                data.origin = parseLocation(originStr);
            }

            data.linkedId = section.getString(key + ".linked_id");

            data.bossRequired = section.getBoolean(key + ".boss_check.required", false);
            data.bossMythicId = section.getString(key + ".boss_check.mythic_mob_id");
            data.dungeonCommand = section.getString(key + ".boss_check.dungeon_command");

            data.isAlphaLocked = section.getBoolean(key + ".open_alpha_lock.enabled", false);
            data.alphaMessage = section.getString(key + ".open_alpha_lock.message", "&c開発中のため移動できません。");

            warps.put(key, data);
        }
    }

    private void migrateConfig() {
        ConfigurationSection section = config.getConfigurationSection("warps");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (config.contains("warps." + key + ".location") && !config.contains("warps." + key + ".origin")) {
                    config.set("warps." + key + ".origin", config.get("warps." + key + ".location"));
                    config.set("warps." + key + ".location", null);
                }
                if (config.contains("warps." + key + ".display_name") && !config.contains("warps." + key + ".sub_title")) {
                    config.set("warps." + key + ".sub_title", config.get("warps." + key + ".display_name"));
                    config.set("warps." + key + ".display_name", null);
                }
            }
        }
        config.set("version", 2);
        save();
    }

    public void tryWarp(Player player, String warpId) {
        WarpData warp = warps.get(warpId);
        if (warp == null) {
            player.sendMessage(Component.text("移動ポイントが存在しません: " + warpId, NamedTextColor.RED));
            return;
        }

        Location dest = null;
        WarpData targetWarp = warp;

        if (warp.linkedId != null && warps.containsKey(warp.linkedId)) {
            targetWarp = warps.get(warp.linkedId);
            dest = targetWarp.origin;
        } else {
            dest = warp.origin;
        }

        if (dest == null) {
            player.sendMessage(Component.text("移動先の座標が設定されていません。", NamedTextColor.RED));
            return;
        }

        if (warp.bossRequired && warp.bossMythicId != null) {
            NamespacedKey key = new NamespacedKey(Deepwither.getInstance(), "boss_killed_" + warp.bossMythicId.toLowerCase());
            if (!player.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                if (warp.dungeonCommand != null) {
                    String cmd = warp.dungeonCommand.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } else {
                    player.sendMessage(Component.text("この先に進むにはボス討伐が必要です。", NamedTextColor.RED));
                }
                return;
            }
        }

        if (warp.isAlphaLocked) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(warp.alphaMessage));
            return;
        }

        player.teleport(dest);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        if (targetWarp.floorName != null || targetWarp.subTitle != null) {
            Component mainTitle = Component.text(targetWarp.floorName != null ? targetWarp.floorName : "", NamedTextColor.GOLD);
            Component subTitle = Component.text(targetWarp.subTitle != null ? "〜 " + targetWarp.subTitle + " 〜" : "", NamedTextColor.WHITE);

            Title title = Title.title(
                    mainTitle,
                    subTitle,
                    Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500))
            );
            player.showTitle(title);
        }
    }

    public void setWarpOrigin(String warpId, Location loc) {
        String locStr = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
        config.set("warps." + warpId + ".origin", locStr);
        save();
        load(file.getParentFile());
    }

    public void linkWarps(String id1, String id2) {
        config.set("warps." + id1 + ".linked_id", id2);
        config.set("warps." + id2 + ".linked_id", id1);
        save();
        load(file.getParentFile());
    }

    public void createWarp(String id, String floorName, String subTitle) {
        config.set("warps." + id + ".floor_name", floorName);
        config.set("warps." + id + ".sub_title", subTitle);
        save();
        load(file.getParentFile());
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Collection<WarpData> getAllWarpData() {
        return warps.values();
    }

    private Location parseLocation(String s) {
        try {
            String[] parts = s.split(",");
            return new Location(
                    Bukkit.getWorld(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5])
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static class WarpData {
        public String id;
        public String floorName;
        public String subTitle;
        public Location origin;
        public String linkedId;
        public boolean bossRequired;
        public String bossMythicId;
        public String dungeonCommand;
        public boolean isAlphaLocked;
        public String alphaMessage;
    }

    public WarpData getWarpData(String id) {
        return warps.get(id);
    }
}
