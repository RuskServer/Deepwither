package com.lunar_prototype.deepwither;

import java.util.*;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * ステータスタイプの列挙。
 */
public enum StatType {
    ATTACK_DAMAGE("攻撃力", "§c","➸"),
    ATTACK_SPEED("攻撃速度","&f","■"),
    PROJECTILE_DAMAGE("発射体ダメージ","&f","➸"),
    MAGIC_DAMAGE("魔法攻撃力", "§b","■"),
    MAGIC_AOE_DAMAGE("魔法AoE攻撃力", "§b","■"),
    MAGIC_BURST_DAMAGE("魔法バースト攻撃力", "§b","■"),
    DEFENSE("防御力", "§a","■"),
    MAGIC_RESIST("魔法耐性", "§9","■"),
    MAGIC_PENETRATION("魔法貫通", "§9","■"),
    CRIT_CHANCE("クリティカル率", "§e","■"),
    CRIT_DAMAGE("クリティカルダメージ", "§e","■"),
    MAX_HEALTH("最大HP", "§4","■"),
    HP_REGEN("HP回復","§4","■"),
    MOVE_SPEED("移動速度", "§d","■"),
    SKILL_POWER("スキル威力", "§b","■"),
    WEAR("損耗率", "§b","■"),
    MASTERY("マスタリー", "§6","■"),
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

    /**
     * このStatMapに含まれる全てのStatTypeのFlat値とPercent値を指定された乗数で更新します。
     * * @param multiplier 乗数 (例: 1.10 for +10% boost)
     */
    public void multiplyAll(double multiplier) {
        for (StatType type : getAllTypes()) {
            double currentFlat = getFlat(type);
            double currentPercent = getPercent(type);

            // Flat値を更新
            setFlat(type, currentFlat * multiplier);

            // Percent値を更新
            setPercent(type, currentPercent * multiplier);
        }
    }
}

/**
 * Lore表示を生成するビルダー。
 */
class LoreBuilder {

    private static final String WEAR_LORE_PREFIX = "§7損耗率: ";
    private static final String MASTERY_LORE_PREFIX = "§7マスタリー: ";

    /**
     * 既存のアイテムのLoreを読み込み、提供されたStatMapと修理ステータス（損耗率、マスタリー）
     * に基づいて部分的に更新または行を追加する。
     * * @param item アイテムスタック
     * @param newStats 新しいカスタムステータス (StatMap)
     * @param wearRate 損耗率
     * @param masteryLevel マスタリーレベル
     * @return 更新されたLoreのリスト
     */
    public static List<String> updateExistingLore(ItemStack item, StatMap newStats, double wearRate, int masteryLevel) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return build(newStats, false, null, null, null, null, null);
        }

        List<String> existingLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        List<String> newLore = new ArrayList<>();

        // 区切り線
        String separator = "§7§m----------------------------";

        // --- 1. StatMapセクションの開始と終了、および特殊行のインデックスを探す ---
        int statsStartIndex = -1;
        int statsEndIndex = -1;

        // ロア全体を解析し、セクションを特定
        for (int i = 0; i < existingLore.size(); i++) {
            String line = existingLore.get(i);

            // 最初の区切り線（モディファイアセクションの終わり）をStatsセクション開始と仮定
            if (statsStartIndex == -1 && line.equals(separator)) {
                statsStartIndex = i; // 区切り線自体をセクション開始として含む
                continue;
            }

            // 2番目の区切り線（Statsセクションの終わり）を見つける
            if (statsStartIndex != -1 && statsEndIndex == -1 && line.equals(separator)) {
                statsEndIndex = i; // 2番目の区切り線はセクション終了行
                break;
            }
        }

        // Statsセクションが存在しない場合、全体を上書きする
        if (statsStartIndex == -1 || statsEndIndex == -1 || statsEndIndex <= statsStartIndex) {
            // Statsセクションが存在しない場合は、新規ビルドに任せる
            return build(newStats, false, null, null, null, null, null);
        }

        // --- 2. 既存のLoreの非Statsセクションをコピー（モディファイアセクションを保護）---
        // statsStartIndex（最初の区切り線）までをコピー
        for (int i = 0; i < statsStartIndex; i++) {
            newLore.add(existingLore.get(i));
        }

        // --- 3. 新しいStatsセクションをゼロからビルドし、挿入 ---

        // 最初の区切り線を新しいロアに追加
        newLore.add(separator);

        // StatMapの内容から新しいStats行を生成
        for (StatType type : newStats.getAllTypes()) {
            double flat = newStats.getFlat(type);
            double percent = newStats.getPercent(type);
            if (flat != 0 || percent != 0) {
                newLore.add(formatStat(type, flat, percent, false));
            }
        }

        // Statsセクションの終わりの区切り線を新しいロアに追加
        newLore.add(separator);

        // --- 4. 修理ステータス（損耗率とマスタリー）の行を追加/更新 ---

        // 損耗率
        if (wearRate > 0) {
            String wearLine = WEAR_LORE_PREFIX + ChatColor.RESET + String.format("%.0f", wearRate) + "%";
            newLore.add(wearLine);
        }

        // マスタリー
        if (masteryLevel > 0) {
            // ★修正案: %ではなく単なる数値として出力する
            String masteryLine = MASTERY_LORE_PREFIX + ChatColor.AQUA + masteryLevel;
            newLore.add(masteryLine);
        }

        // --- 5. Statsセクションの終了行以降（既存のロアの残り）をコピー ---
        // statsEndIndex（2番目の区切り線）の次から最後までをコピー
        for (int i = statsEndIndex + 1; i < existingLore.size(); i++) {
            String line = existingLore.get(i);

            // ★注意: ロアの最後に損耗率/マスタリーの古い行が残っている可能性があるため、それらはスキップ
            if (line.startsWith(WEAR_LORE_PREFIX) || line.startsWith(MASTERY_LORE_PREFIX)) {
                continue;
            }
            newLore.add(line);
        }

        return newLore;
    }

    public static List<String> build(StatMap stats, boolean compact, String itemType, List<String> flavorText, ItemLoader.RandomStatTracker tracker,String rarity,Map<StatType, Double> appliedModifiers) {
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
            lore.add("");
        }

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

        if (appliedModifiers != null && !appliedModifiers.isEmpty()) {
            lore.add(" §5§l- モディファイアー -"); // モディファイアー専用ヘッダー

            for (Map.Entry<StatType, Double> entry : appliedModifiers.entrySet()) {
                StatType type = entry.getKey();
                double value = entry.getValue();

                // モディファイアー専用のフォーマット
                String label = type.getIcon() + " " + type.getDisplayName();

                // モディファイアーは flat 値として扱われるため、formatStatを流用するか、専用フォーマットを使う
                String line = formatModifierStat(type, value);
                lore.add(line);
            }
            lore.add("§7§m----------------------------"); // モディファイアーセクションの区切り
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

    private static String formatModifierStat(StatType type, double value) {
        String label = type.getIcon() + " " + type.getDisplayName();
        // モディファイアーは§d（マゼンタ）で表示し、ボーナスであることを強調
        return " §d» " + label + ": §d+" + String.format("%.1f", value);
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
