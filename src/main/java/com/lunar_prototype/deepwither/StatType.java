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
    PROJECTILE_SPEED("弾速","&f", "➸"),
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

    /**
     * 2列レイアウト用のメインビルドロジック（修正版）
     */
    public static List<String> build(StatMap stats, boolean compact, String itemType, List<String> flavorText,
                                     ItemLoader.RandomStatTracker tracker, String rarity, Map<StatType, Double> appliedModifiers,
                                     FabricationGrade grade) {
        List<String> lore = new ArrayList<>();

        // --- ヘッダー ---
        if (grade != null) lore.add(grade.getDisplayName());

        StringBuilder infoLine = new StringBuilder();
        if (rarity != null) infoLine.append(rarity.replace("&", "§"));
        if (itemType != null) infoLine.append(" §f| §7").append(itemType.replace("&", "§"));
        if (infoLine.length() > 0) lore.add(infoLine.toString());

        if (tracker != null) {
            double ratio = tracker.getRatio() * 100.0;
            String color = (ratio >= 90) ? "§6" : (ratio >= 70) ? "§e" : (ratio >= 50) ? "§a" : "§7";
            lore.add("§f品質: " + color + Math.round(ratio) + "%");
        }

        // --- フレーバー ---
        if (flavorText != null && !flavorText.isEmpty()) {
            lore.add("");
            for (String line : flavorText) lore.add("§8§o" + line.replace("&", "§"));
        }

        lore.add("§8§m-----------------------------");

        // --- ステータス集計用リスト ---
        List<String> statLines = new ArrayList<>();

        // モディファイアー（あれば最上部へ）
        if (appliedModifiers != null && !appliedModifiers.isEmpty()) {
            // 見出しを追加し、右側を空けるために空文字とセット
            statLines.add("§f§l[付加能力]");
            statLines.add("");
            for (Map.Entry<StatType, Double> entry : appliedModifiers.entrySet()) {
                statLines.add(formatModifierStat(entry.getKey(), entry.getValue()));
            }
        }

        // 基礎ステータス
        lore.add("§f§l[基礎ステータス]"); // 見出しは1列で表示
        statLines.add("");
        for (StatType type : stats.getAllTypes()) {
            double flat = stats.getFlat(type);
            double percent = stats.getPercent(type);
            if (flat == 0 && percent == 0) continue;
            statLines.add(formatStat(type, flat, percent, compact));
        }

        // --- 2列整列の実行 ---
        // 日本語が長いので170px程度確保すると安全です
        int leftColumnWidthPx = 170;

        for (int i = 0; i < statLines.size(); i += 2) {
            String left = statLines.get(i);
            if (i + 1 >= statLines.size()) {
                lore.add(" " + left);
                break;
            }
            String right = statLines.get(i + 1);

            // 片方がセクション見出し、もう片方が空文字の場合の調整
            if (right.isEmpty()) {
                lore.add(" " + left);
            } else {
                // ここでピクセルパディングを実行
                lore.add(" " + padToWidth(left, leftColumnWidthPx) + right);
            }
        }

        lore.add("§8§m-----------------------------");
        return lore;
    }

    private static String padToWidth(String text, int targetPx) {
        int currentPx = getMinecraftStringWidth(text);
        int neededPx = targetPx - currentPx;

        if (neededPx <= 0) return text + "  "; // 最低限の隙間

        int boldSpaces = 0;
        int normalSpaces = 0;

        // 4px と 5px(太字) を使って 1px 単位で隙間を埋める
        for (int b = 0; b < 5; b++) {
            int remainder = neededPx - (b * 5);
            if (remainder >= 0 && remainder % 4 == 0) {
                boldSpaces = b;
                normalSpaces = remainder / 4;
                break;
            }
        }
        // ループで見つからない場合(neededPxが小さい場合)のフォールバック
        if (boldSpaces == 0 && normalSpaces == 0) {
            normalSpaces = neededPx / 4;
        }

        StringBuilder sb = new StringBuilder(text);
        if (boldSpaces > 0) sb.append("§r§l").append(" ".repeat(boldSpaces));
        if (normalSpaces > 0) sb.append("§r").append(" ".repeat(normalSpaces));
        return sb.toString() + "§r";
    }

    private static int getMinecraftStringWidth(String text) {
        if (text == null) return 0;
        int length = 0;
        boolean isBold = false;
        boolean nextIsColor = false;

        for (char c : text.toCharArray()) {
            if (c == '§') { nextIsColor = true; continue; }
            if (nextIsColor) {
                if (c == 'l' || c == 'L') isBold = true;
                else if (c == 'r' || c == 'R') isBold = false;
                nextIsColor = false;
                continue;
            }
            int charWidth = getCharWidth(c);
            if (isBold) {
                if (c == ' ') length += 5;
                else length += charWidth + 1;
            } else {
                length += charWidth;
            }
        }
        return length;
    }

    private static int getCharWidth(char c) {
        // --- 特殊アイコンの幅 (重要) ---
        if (c == '➸') return 9; // 矢印アイコン
        if (c == '✠') return 9; // 追加: 十字/紋章アイコン (通常9px)
        if (c == '■') return 8; // 四角アイコン
        if (c == '⌛') return 8; // 砂時計アイコン
        if (c == '•') return 7; // ドットアイコン
        if (c == '»') return 7; // 引用符アイコン

        // 記号類
        if ("i.,l|!:;".indexOf(c) != -1) return 2;
        if ("' ".indexOf(c) != -1) return 4;
        if ("t[]()".indexOf(c) != -1) return 5;
        if ("*\"<>".indexOf(c) != -1) return 5;

        // 日本語 (Unicode)
        // Minecraftの全角文字はグリッド状に配置され、実質的な幅は12pxです。
        if (Character.toString(c).matches("[^\\x00-\\x7F]")) return 12;

        // 基本英数字
        return 6;
    }

    private static String formatModifierStat(StatType type, double value) {
        // アイコンとテキストの間に余計な空白を入れず、padToWidthに計算させる
        return "§d» " + type.getIcon() + " " + type.getDisplayName() + ": §d+" + String.format("%.1f", value);
    }

    private static String formatStat(StatType type, double flat, double percent, boolean compact) {
        String valStr = (percent != 0) ? String.format("%.1f", flat) + " (§a+" + Math.round(percent) + "%§f)" : String.valueOf(flat);
        return "§f• " + type.getIcon() + " " + type.getDisplayName() + ": §f" + valStr;
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
