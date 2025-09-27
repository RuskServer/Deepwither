package com.lunar_prototype.deepwither;

import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * ステータスタイプの列挙。
 */
public enum StatType {
    ATTACK_DAMAGE("攻撃力", "§c","➸"),
    ATTACK_SPEED("攻撃速度","&f","■"),
    MAGIC_DAMAGE("魔法攻撃力", "§b","■"),
    DEFENSE("防御力", "§a","■"),
    MAGIC_RESIST("魔法耐性", "§9","■"),
    MAGIC_PENETRATION("魔法貫通", "§9","■"),
    CRIT_CHANCE("クリティカル率", "§e","■"),
    CRIT_DAMAGE("クリティカルダメージ", "§e","■"),
    MAX_HEALTH("最大HP", "§4","■"),
    MOVE_SPEED("移動速度", "§d","■"),
    SKILL_POWER("スキル威力", "§b","■"),
    MAX_MANA("最大マナ", "§b","■"),
    COOLDOWN_REDUCTION("クールダウン短縮", "§8","■"),
    STR("筋力", "§c", "❖"),
    VIT("体力", "§a", "❤"),
    MND("精神力", "§b", "✦"),
    INT("知性", "§d", "✎"),
    AGI("素早さ", "§e", "➤");

    private final String displayName;
    private final String colorCode;
    private final String icon;

    StatType(String displayName, String colorCode,String icon) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return colorCode;
    }

    public String getIcon() {
        return icon;
    }
}

/**
 * プレイヤーやアイテムのステータスを表す。
 */
class StatMap {
    private final Map<StatType, Double> flatValues = new EnumMap<>(StatType.class);
    private final Map<StatType, Double> percentValues = new EnumMap<>(StatType.class);

    public void setFlat(StatType type, double value) {
        flatValues.put(type, round(value));
    }

    public void setPercent(StatType type, double value) {
        percentValues.put(type, round(value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public double getFlat(StatType type) {
        return flatValues.getOrDefault(type, 0.0);
    }

    public double getPercent(StatType type) {
        return percentValues.getOrDefault(type, 0.0);
    }

    public double getFinal(StatType type) {
        double flat = getFlat(type);
        double percent = getPercent(type);
        return Math.round((flat * (1 + percent / 100.0)) * 100.0) / 100.0;
    }

    public void add(StatMap other) {
        for (StatType type : other.getAllTypes()) {
            double flat = this.getFlat(type) + other.getFlat(type);
            double percent = this.getPercent(type) + other.getPercent(type);
            this.setFlat(type, flat);
            this.setPercent(type, percent);
        }
    }

    public Set<StatType> getAllTypes() {
        Set<StatType> types = new HashSet<>();
        types.addAll(flatValues.keySet());
        types.addAll(percentValues.keySet());
        return types;
    }
}

/**
 * Lore表示を生成するビルダー。
 */
class LoreBuilder {

    public static List<String> build(StatMap stats, boolean compact, String itemType, List<String> flavorText, ItemLoader.RandomStatTracker tracker,String rarity) {
        List<String> lore = new ArrayList<>();

        // 上部: タイプ表示
        if (itemType != null && !itemType.isEmpty()) {
            // &6などのコードを§6に変換
            String formattedItemType = itemType.replace("&", "§");
            lore.add("§7カテゴリ:§f" + formattedItemType);
        }

        if (rarity != null && !rarity.isEmpty()) {
            // 同様にレアリティも変換
            String formattedRarity = rarity.replace("&", "§");
            lore.add("§7レアリティ:§f" + formattedRarity);
        }

        // 空行 + フレーバー（存在する場合）
        if (flavorText != null && !flavorText.isEmpty()) {
            lore.add(""); // 空行
            for (String line : flavorText) {
                // フレーバーも変換
                String formattedLine = line.replace("&", "§");
                lore.add("§8" + formattedLine); // フレーバーは薄い灰色で表示
            }
        }

        // 空行 + ステータスヘッダー
        lore.add("");
        lore.add("§7§m----------------------------");

        // 達成率（RandomStatTrackerがある場合のみ）
        if (tracker != null) {
            double ratio = tracker.getRatio() * 100.0;
            String color;
            if (ratio >= 90) color = "§6";
            else if (ratio >= 70) color = "§e";
            else if (ratio >= 50) color = "§a";
            else color = "§7";
            lore.add(" §f• 品質: " + color + Math.round(ratio) + "%");
        }

        for (StatType type : stats.getAllTypes()) {
            double flat = stats.getFlat(type);
            double percent = stats.getPercent(type);

            if (flat == 0 && percent == 0) continue;

            String line = formatStat(type, flat, percent, compact);
            lore.add(line);
        }

        lore.add("§7§m----------------------------");
        return lore;
    }

    private static String formatStat(StatType type, double flat, double percent, boolean compact) {
        String label = type.getIcon() + " " + type.getDisplayName();

        if (compact) {
            return " §f• " + label + ": §f" + flat + (percent != 0 ? " (§a+" + percent + "%§f)" : "");
        } else {
            return " §f• " + label + ": §f" + flat + (percent != 0 ? "（§a+" + percent + "%§f）" : "");
        }
    }
}

/**
 * プレイヤーの装備から合計ステータスを算出するユーティリティ。
 */
class StatUtils {
    public static StatMap calculateTotalStats(Player player) {
        StatMap total = new StatMap();

        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null || item.getType().isAir()) continue;
            StatMap itemStats = getStatsFromItem(item);
            total.add(itemStats);
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && !mainHand.getType().isAir()) {
            StatMap mainStats = getStatsFromItem(mainHand);
            total.add(mainStats);
        }

        // 拡張スロットがある場合はここで追加処理する

        return total;
    }

    // 仮の実装。実際にはNBTやPDCから読み取る必要がある
    private static StatMap getStatsFromItem(ItemStack item) {
        StatMap stats = new StatMap();
        // TODO: NBTやPersistentDataContainerからステータスを読み取る処理を実装
        return stats;
    }
}
