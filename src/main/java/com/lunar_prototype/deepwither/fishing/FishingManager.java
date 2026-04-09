package com.lunar_prototype.deepwither.fishing;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

@DependsOn({ProfessionManager.class, ItemFactory.class})
public class FishingManager implements IManager {

    private final Deepwither plugin;
    private final Map<String, RaritySettings> rarityMap = new LinkedHashMap<>(); // 順序保持のためLinkedHashMap
    private final Map<String, List<LootEntry>> lootTable = new HashMap<>();
    private final Map<UUID, Integer> comboMap = new HashMap<>();
    private final Map<UUID, Long> lastCatchTimeMap = new HashMap<>();
    private final Random random = new Random();

    // コンボ有効期間 (ミリ秒) - 60秒
    private static final long COMBO_EXPIRY = 60 * 1000L;
    // 最大コンボ（ボーナス上限）
    private static final int MAX_COMBO = 20;

    // 抽選順序を固定するためのリスト
    private static final List<String> RARITY_ORDER = List.of("LEGENDARY", "EPIC", "RARE", "UNCOMMON");

    public FishingManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        loadConfig();
    }

    @Override
    public void shutdown() {
        comboMap.clear();
        lastCatchTimeMap.clear();
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
                String rawName = raritySection.getString(key + ".name", key);
                // アンパサンド形式をセクション形式(§)のStringに変換
                String name = LegacyComponentSerializer.legacySection().serialize(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(rawName)
                );
                double baseChance = raritySection.getDouble(key + ".base_chance", 0.0);
                double levelBonus = raritySection.getDouble(key + ".level_bonus", 0.0);
                int baseExp = raritySection.getInt(key + ".base_exp", 20);
                rarityMap.put(key, new RaritySettings(name, baseChance, levelBonus, baseExp));
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
    public FishingResult catchFish(Player player) {
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
        ItemStack item = plugin.getItemFactory().getCustomItemStack(wonEntry.id);

        if (item == null) {
            plugin.getLogger().log(Level.WARNING, "Fishing Error: Item ID '" + wonEntry.id + "' not found in ItemFactory.");
            return null;
        }

        return new FishingResult(item, selectedRarity);
    }

    public int updateCombo(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastCatchTimeMap.getOrDefault(uuid, 0L);

        int currentCombo = comboMap.getOrDefault(uuid, 0);

        if (now - last > COMBO_EXPIRY) {
            currentCombo = 1;
        } else {
            currentCombo++;
        }

        comboMap.put(uuid, currentCombo);
        lastCatchTimeMap.put(uuid, now);
        return currentCombo;
    }

    public void resetCombo(Player player) {
        comboMap.remove(player.getUniqueId());
        lastCatchTimeMap.remove(player.getUniqueId());
    }

    public double getComboBonus(int combo) {
        // 1コンボにつき5%アップ、最大20コンボで+100% (2倍)
        int cappedCombo = Math.min(combo, MAX_COMBO);
        return 1.0 + (cappedCombo * 0.05);
    }

    public RaritySettings getRaritySettings(String rarityKey) {
        return rarityMap.get(rarityKey);
    }

    public Component buildStatusTooltip(Player player) {
        int level = plugin.getProfessionManager().getLevel(
                plugin.getProfessionManager().getData(player).getExp(ProfessionType.FISHING)
        );

        Component tooltip = Component.text("釣りスキル", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("Lv." + level, NamedTextColor.YELLOW))
                .append(Component.newline());

        double remaining = 1.0D;
        for (String rarityKey : RARITY_ORDER) {
            RaritySettings settings = rarityMap.get(rarityKey);
            if (settings == null) {
                continue;
            }

            double rawChance = clamp(settings.baseChance + (level * settings.levelBonus));
            double actualChance = remaining * rawChance;
            remaining *= (1.0D - rawChance);

            tooltip = tooltip
                    .append(Component.text(formatRarityName(rarityKey), rarityColor(rarityKey), TextDecoration.BOLD))
                    .append(Component.text(": " + formatPercent(actualChance)
                            + " (判定 " + formatPercent(rawChance) + ")", NamedTextColor.GRAY))
                    .append(Component.newline());
        }

        if (remaining > 0.0D) {
            tooltip = tooltip
                    .append(Component.text("COMMON", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(": " + formatPercent(remaining), NamedTextColor.GRAY));
        }

        return tooltip;
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

    private double clamp(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    private String formatRarityName(String rarityKey) {
        return switch (rarityKey) {
            case "LEGENDARY" -> "レジェンダリー";
            case "EPIC" -> "エピック";
            case "RARE" -> "レア";
            case "UNCOMMON" -> "アンコモン";
            default -> rarityKey;
        };
    }

    private NamedTextColor rarityColor(String rarityKey) {
        return switch (rarityKey) {
            case "LEGENDARY" -> NamedTextColor.GOLD;
            case "EPIC" -> NamedTextColor.LIGHT_PURPLE;
            case "RARE" -> NamedTextColor.AQUA;
            case "UNCOMMON" -> NamedTextColor.GREEN;
            default -> NamedTextColor.WHITE;
        };
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value * 100.0D);
    }

    // --- 内部クラス ---
    public record FishingResult(ItemStack item, String rarityKey) {}

    public static class RaritySettings {
        final String displayName;
        final double baseChance;
        final double levelBonus;
        final int baseExp;

        RaritySettings(String displayName, double baseChance, double levelBonus, int baseExp) {
            this.displayName = displayName;
            this.baseChance = baseChance;
            this.levelBonus = levelBonus;
            this.baseExp = baseExp;
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
