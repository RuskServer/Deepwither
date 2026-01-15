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
    ATTACK_DAMAGE("攻撃力", "§c", "➸"),
    ATTACK_SPEED("攻撃速度", "&f", "➸"),
    PROJECTILE_DAMAGE("発射体ダメージ", "&f", "➸"),
    MAGIC_DAMAGE("魔法攻撃力", "§b", "■"),
    MAGIC_AOE_DAMAGE("魔法AoE攻撃力", "§b", "■"),
    MAGIC_BURST_DAMAGE("魔法バースト攻撃力", "§b", "■"),
    DEFENSE("防御力", "§a", "✠"),
    MAGIC_RESIST("魔法耐性", "§9", "✠"),
    MAGIC_PENETRATION("魔法貫通", "§9", "■"),
    CRIT_CHANCE("クリティカル率", "§e", "■"),
    CRIT_DAMAGE("クリティカルダメージ", "§e", "■"),
    MAX_HEALTH("最大HP", "§4", "❤"),
    HP_REGEN("HP回復", "§4", "❤"),
    MOVE_SPEED("移動速度", "§d", "■"),
    SKILL_POWER("スキル威力", "§b", "■"),
    WEAR("損耗率", "§b", "■"),
    REACH("リーチ増加", "§b", "■"),
    REDUCES_MOVEMENT_SPEED_DECREASE("移動速度低下軽減", "§b", "■"),
    MASTERY("マスタリー", "§6", "■"),
    MAX_MANA("最大マナ", "§b", "☆"),
    COOLDOWN_REDUCTION("クールダウン短縮", "§8", "⌛"),
    SHIELD_BLOCK_RATE("盾の減衰率", "§d", "■"),
    STR("筋力", "§c", "❖"),
    VIT("体力", "§a", "❤"),
    MND("精神力", "§b", "✦"),
    INT("知性", "§d", "✎"),
    AGI("素早さ", "§e", "➤"),
    SCYTHE_DAMAGE("鎌ダメージ", "§c", "⚔"),
    GREATSWORD_DAMAGE("大剣ダメージ", "§c", "⚔"),
    SPEAR_DAMAGE("槍ダメージ", "§c", "⚔"),
    AXE_DAMAGE("斧ダメージ", "§c", "⚔"),
    MACE_DAMAGE("メイスダメージ", "§c", "⚔"),
    SWORD_DAMAGE("剣ダメージ", "§c", "⚔"),
    MACHETE_DAMAGE("マチェットダメージ", "§c", "⚔"),
    HAMMER_DAMAGE("ハンマーダメージ", "§c", "⚔"),
    HAMMER_DAMAGE("ハンマーダメージ", "§c", "⚔"),
    HALBERD_DAMAGE("ハルバードダメージ", "§c", "⚔"),
    BLEED_CHANCE("出血付与", "§4", "🩸"),
    LIFESTEAL("ドレイン", "§c", "❤"),
    FREEZE_CHANCE("凍結付与", "§b", "❄"),
    AOE_CHANCE("拡散攻撃", "§e", "💥");

    private final String displayName;
    private final String colorCode;
    private final String icon;

    StatType(String displayName, String colorCode, String icon) {
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
 * Lore表示を生成するビルダー。
 */
class LoreBuilder {

    private static final String WEAR_LORE_PREFIX = "§7損耗率: ";
    private static final String MASTERY_LORE_PREFIX = "§7マスタリー: ";

    /**
     * 既存のアイテムのLoreを読み込み、提供されたStatMapと修理ステータス（損耗率、マスタリー）
     * に基づいて部分的に更新または行を追加する。
     * * @param item アイテムスタック
     * 
     * @param newStats     新しいカスタムステータス (StatMap)
     * @param wearRate     損耗率
     * @param masteryLevel マスタリーレベル
     * @return 更新されたLoreのリスト
     */
    public static List<String> updateExistingLore(ItemStack item, StatMap newStats, double wearRate, int masteryLevel) {
        ItemMeta meta = item.getItemMeta();
        // Metaがない、またはLoreがない場合は新規作成（build）へ
        if (meta == null || !meta.hasLore()) {
            return build(newStats, false, null, null, null, null, null, null);
        }

        List<String> existingLore = meta.getLore();
        List<String> newLore = new ArrayList<>();
        String separator = "§7§m----------------------------";

        // --- 1. 区切り線の位置をすべて特定する ---
        List<Integer> separatorIndices = new ArrayList<>();
        for (int i = 0; i < existingLore.size(); i++) {
            if (existingLore.get(i).equals(separator)) {
                separatorIndices.add(i);
            }
        }

        // 区切り線が2つ未満の場合は構造が特殊なため、安全策として既存buildを呼ぶか、
        // あるいは構造を維持できないため新規作成する
        if (separatorIndices.size() < 2) {
            return build(newStats, false, null, null, null, null, null, null);
        }

        // --- 2. セクションの特定 ---
        // 統計(Stats)セクションは「最後から2番目の区切り線」から「最後の区切り線」の間にあると定義
        int lastSepIndex = separatorIndices.get(separatorIndices.size() - 1);
        int secondLastSepIndex = separatorIndices.get(separatorIndices.size() - 2);

        // --- 3. 前半部分（ヘッダー、フレーバー、モディファイア等）をそのままコピー ---
        // secondLastSepIndex (Statsの前の区切り線) までをコピー
        for (int i = 0; i <= secondLastSepIndex; i++) {
            newLore.add(existingLore.get(i));
        }

        // --- 4. 新しい Stats セクションを挿入 ---
        for (StatType type : newStats.getAllTypes()) {
            double flat = newStats.getFlat(type);
            double percent = newStats.getPercent(type);
            if (flat != 0 || percent != 0) {
                newLore.add(formatStat(type, flat, percent, false));
            }
        }

        // 最後の区切り線を追加
        newLore.add(separator);

        // --- 5. 修理ステータス（損耗率とマスタリー）の追加 ---
        if (wearRate > 0) {
            String wearLine = WEAR_LORE_PREFIX + ChatColor.RESET + String.format("%.0f", wearRate) + "%";
            newLore.add(wearLine);
        }
        if (masteryLevel > 0) {
            String masteryLine = MASTERY_LORE_PREFIX + ChatColor.AQUA + masteryLevel;
            newLore.add(masteryLine);
        }

        // --- 6. 既存ロアの「最後の方」にあるかもしれない独自行を保持 ---
        // ただし、損耗率、マスタリー、および既に処理したStats行は除外する
        for (int i = lastSepIndex + 1; i < existingLore.size(); i++) {
            String line = existingLore.get(i);

            // 修理ステータス行は新しく追加済みなのでスキップ
            if (line.startsWith(WEAR_LORE_PREFIX) || line.startsWith(MASTERY_LORE_PREFIX)) {
                continue;
            }

            // その他、もし何か別のプラグインや機能が末尾に文字列を足していた場合はそれを保持
            newLore.add(line);
        }

        return newLore;
    }

    public static List<String> build(StatMap stats, boolean compact, String itemType, List<String> flavorText,
            ItemLoader.RandomStatTracker tracker, String rarity, Map<StatType, Double> appliedModifiers,
            FabricationGrade grade) {
        List<String> lore = new ArrayList<>();

        // ★ FG表示を追加 (最上部)
        // grade が null または STANDARD(FG-1) の場合は表示しない、という仕様も可能ですが
        // 「別物レベル」のシステムなら FG-1 も表示したほうが統一感が出ます。
        if (grade != null) {
            lore.add(grade.getDisplayName());
            // lore.add(""); // 必要なら空行
        }

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
            if (ratio >= 90)
                color = "§6";
            else if (ratio >= 70)
                color = "§e";
            else if (ratio >= 50)
                color = "§a";
            else
                color = "§7";
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

            if (flat == 0 && percent == 0)
                continue;

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
            if (item == null || item.getType().isAir())
                continue;
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
