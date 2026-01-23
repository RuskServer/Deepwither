package com.lunar_prototype.deepwither;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerSettingsManager {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;
    private final Map<UUID, Map<SettingType, Boolean>> cache = new HashMap<>();
    private final Map<UUID, String> rarityFilterCache = new HashMap<>();

    public enum SettingType {
        SHOW_GIVEN_DAMAGE("与ダメージログ", true),     // 自分が与えたダメージ
        SHOW_TAKEN_DAMAGE("被ダメージログ", true),     // 自分が受けたダメージ
        SHOW_MITIGATION("防御・軽減ログ", true),       // 盾防御やシールド等のログ
        SHOW_SPECIAL_LOG("特殊・スキルログ", true),    // クリティカル、スキル発動、回復など
        SHOW_PICKUP_LOG("アイテム拾得ログ", true);     // 追加

        private final String displayName;
        private final boolean defValue;

        SettingType(String displayName, boolean defValue) {
            this.displayName = displayName;
            this.defValue = defValue;
        }

        public String getDisplayName() { return displayName; }
        public boolean getDefault() { return defValue; }
    }

    public PlayerSettingsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "settings.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 設定の取得 (キャッシュ -> ファイル -> デフォルト)
    public boolean isEnabled(Player player, SettingType type) {
        return isEnabled(player.getUniqueId(), type);
    }

    public boolean isEnabled(UUID uuid, SettingType type) {
        if (cache.containsKey(uuid) && cache.get(uuid).containsKey(type)) {
            return cache.get(uuid).get(type);
        }

        // config path: <uuid>.<SettingType>
        String path = uuid.toString() + "." + type.name();
        boolean val = config.getBoolean(path, type.getDefault());

        // キャッシュに保存
        cache.computeIfAbsent(uuid, k -> new HashMap<>()).put(type, val);
        return val;
    }

    // 設定の切り替え
    public void toggle(Player player, SettingType type) {
        UUID uuid = player.getUniqueId();
        boolean current = isEnabled(uuid, type);
        boolean currentNew = !current;

        cache.computeIfAbsent(uuid, k -> new HashMap<>()).put(type, currentNew);
        config.set(uuid.toString() + "." + type.name(), currentNew);
        save();

        // 簡易フィードバック不要なら削除可
        // player.sendMessage("§7[設定] " + type.getDisplayName() + ": " + (currentNew ? "§aON" : "§cOFF"));
    }

    // レアリティフィルター設定の取得
    public String getRarityFilter(Player player) {
        return getRarityFilter(player.getUniqueId());
    }

    public String getRarityFilter(UUID uuid) {
        if (rarityFilterCache.containsKey(uuid)) {
            return rarityFilterCache.get(uuid);
        }

        String path = uuid.toString() + ".rarity_filter";
        String rarity = config.getString(path, "&f&lコモン"); // デフォルトはコモン

        rarityFilterCache.put(uuid, rarity);
        return rarity;
    }

    // レアリティフィルター設定の変更
    public void setRarityFilter(Player player, String rarity) {
        UUID uuid = player.getUniqueId();
        rarityFilterCache.put(uuid, rarity);
        config.set(uuid.toString() + ".rarity_filter", rarity);
        save();
    }
}