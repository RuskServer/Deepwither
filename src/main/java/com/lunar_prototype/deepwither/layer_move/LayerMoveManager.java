package com.lunar_prototype.deepwither.layer_move;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
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

        ConfigurationSection section = config.getConfigurationSection("warps");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            WarpData data = new WarpData();
            data.id = key;
            data.displayName = section.getString(key + ".display_name");

            // 座標読み込み (world,x,y,z,yaw,pitch形式を想定)
            String locStr = section.getString(key + ".location");
            if (locStr != null) {
                data.destination = parseLocation(locStr);
            }

            data.bossRequired = section.getBoolean(key + ".boss_check.required", false);
            data.bossMythicId = section.getString(key + ".boss_check.mythic_mob_id");
            data.dungeonCommand = section.getString(key + ".boss_check.dungeon_command");

            data.isAlphaLocked = section.getBoolean(key + ".open_alpha_lock.enabled", false);
            data.alphaMessage = section.getString(key + ".open_alpha_lock.message", "&c開発中のため移動できません。");

            warps.put(key, data);
        }
    }

    public void tryWarp(Player player, String warpId) {
        WarpData warp = warps.get(warpId);
        if (warp == null) {
            player.sendMessage(ChatColor.RED + "移動先設定が存在しません: " + warpId);
            return;
        }

        if (warp.destination == null) {
            player.sendMessage(ChatColor.RED + "移動先座標が設定されていません。");
            return;
        }

        // 1. ボス撃破チェック
        if (warp.bossRequired && warp.bossMythicId != null) {
            NamespacedKey key = new NamespacedKey(Deepwither.getInstance(), "boss_killed_" + warp.bossMythicId.toLowerCase());
            if (!player.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                // まだ倒していない -> ダンジョンコマンド実行
                if (warp.dungeonCommand != null) {
                    String cmd = warp.dungeonCommand.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    // player.sendMessage(ChatColor.YELLOW + "ボス部屋へ移動します...");
                } else {
                    player.sendMessage(ChatColor.RED + "この先に進むにはボス討伐が必要です。");
                }
                return; // ここで処理終了（移動させない）
            }
        }

        // 2. Open Alpha ロックチェック (ボスを倒していてもロックされていれば通さない)
        if (warp.isAlphaLocked) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', warp.alphaMessage));
            return;
        }

        // 3. 移動実行
        player.teleport(warp.destination);
        player.sendMessage(ChatColor.GREEN + "移動しました: " + warp.displayName);
    }

    // 座標保存用コマンドなどで使用
    public void setWarpLocation(String warpId, Location loc) {
        String locStr = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
        config.set("warps." + warpId + ".location", locStr);
        try {
            config.save(file);
            load(file.getParentFile()); // リロード
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 全てのワープデータを取得するメソッド
    public Collection<WarpData> getAllWarpData() {
        return warps.values();
    }

    // 文字列 -> Location
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

    // データクラス
    public static class WarpData {
        String id;
        String displayName;
        Location destination;
        boolean bossRequired;
        String bossMythicId;
        String dungeonCommand;
        boolean isAlphaLocked;
        String alphaMessage;
    }

    public WarpData getWarpData(String id) {
        return warps.get(id);
    }
}