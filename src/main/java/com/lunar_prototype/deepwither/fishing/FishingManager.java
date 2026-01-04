package com.lunar_prototype.deepwither.fishing;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class FishingManager {

    private final Deepwither plugin;
    private final Map<String, RaritySettings> rarityMap = new LinkedHashMap<>(); // 順序保持のためLinkedHashMap
    private final Map<String, List<LootEntry>> lootTable = new HashMap<>();
    private final Random random = new Random();

    // 抽選順序を固定するためのリスト
    private static final List<String> RARITY_ORDER = List.of("LEGENDARY", "EPIC", "RARE", "UNCOMMON");

    public FishingManager(Deepwither plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        rarityMap.clear();
        lootTable.clear();

        File file = new File(plugin.getDataFolder(), "fishing.yml");
        if (!file.exists()) plugin.saveResource("fishing.yml", false);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // 1. レアリティ設定の読み込み
        ConfigurationSection raritySection = config.getConfigurationSection("rarity_settings");
        if (raritySection != null) {
            for (String key : raritySection.getKeys(false)) {
                String name = ChatColor.translateAlternateColorCodes('&', raritySection.getString(key + ".name", key));
                double baseChance = raritySection.getDouble(key + ".base_chance", 0.0);
                double levelBonus = raritySection.getDouble(key + ".level_bonus", 0.0);
                rarityMap.put(key, new RaritySettings(name, baseChance, levelBonus));
            }
        }

        // 2. ドロップテーブルの読み込み
        ConfigurationSection lootSection = config.getConfigurationSection("loot_tables");
        if (lootSection != null) {
            for (String rarityKey : lootSection.getKeys(false)) {
                List<Map<?, ?>> list = lootSection.getMapList(rarityKey);
                List<LootEntry> entries = new ArrayList<>();
                for (Map<?, ?> map : list) {
                    String id = (String) map.get("id");
                    int weight = (Integer) map.get("weight");
                    entries.add(new LootEntry(id, weight));
                }
                lootTable.put(rarityKey, entries);
            }
        }
        plugin.getLogger().info("Fishing tables loaded.");
    }

    /**
     * プレイヤーのレベルに基づいてアイテムを選出する
     */
    public ItemStack catchFish(Player player) {
        // 1. 釣りレベルの取得
        int level = plugin.getProfessionManager().getLevel(
                plugin.getProfessionManager().getData(player).getExp(ProfessionType.FISHING)
        );

        // 2. レアリティの抽選 (高レアリティから順に判定)
        String selectedRarity = "COMMON"; // デフォルト

        for (String rarityKey : RARITY_ORDER) {
            RaritySettings settings = rarityMap.get(rarityKey);
            if (settings == null) continue;

            // 確率 = 基礎値 + (レベル * 成長係数)
            // 例: Base 0.1% + (Lv50 * 0.05%) = 2.6%
            double chance = settings.baseChance + (level * settings.levelBonus);

            // 確率キャップ（念のため100%を超えないように）
            if (chance > 1.0) chance = 1.0;

            if (random.nextDouble() < chance) {
                selectedRarity = rarityKey;
                break; // 当選したらループを抜ける
            }
        }

        // 3. レアリティ内のアイテムをウェイト抽選
        List<LootEntry> entries = lootTable.get(selectedRarity);
        if (entries == null || entries.isEmpty()) {
            // 設定ミスなどでリストがない場合、COMMONにフォールバック、それでもなければnull
            if (!selectedRarity.equals("COMMON")) {
                entries = lootTable.get("COMMON");
            }
            if (entries == null || entries.isEmpty()) return null;
        }

        LootEntry wonEntry = getWeightedRandom(entries);

        // 4. ItemFactoryからアイテム生成
        // ItemFactory内のメソッドを利用してアイテムスタックを取得
        ItemStack item = plugin.getItemFactory().getCustomItemStack(wonEntry.id);

        if (item == null) {
            plugin.getLogger().log(Level.WARNING, "Fishing Error: Item ID '" + wonEntry.id + "' not found in ItemFactory.");
        }

        return item;
    }

    private LootEntry getWeightedRandom(List<LootEntry> entries) {
        int totalWeight = entries.stream().mapToInt(e -> e.weight).sum();
        int randomVal = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (LootEntry entry : entries) {
            currentWeight += entry.weight;
            if (randomVal < currentWeight) {
                return entry;
            }
        }
        return entries.get(0); // 安全策
    }

    // --- 内部クラス ---
    private static class RaritySettings {
        final String displayName;
        final double baseChance;
        final double levelBonus;

        RaritySettings(String displayName, double baseChance, double levelBonus) {
            this.displayName = displayName;
            this.baseChance = baseChance;
            this.levelBonus = levelBonus;
        }
    }

    private static class LootEntry {
        final String id;
        final int weight;

        LootEntry(String id, int weight) {
            this.id = id;
            this.weight = weight;
        }
    }
}