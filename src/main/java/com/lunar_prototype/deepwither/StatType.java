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
    CRIT_CHANCE("会心率", "§e", "■"),
    CRIT_DAMAGE("会心ダメージ", "§e", "■"),
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

        // --- [付加能力] セクション ---
        if (appliedModifiers != null && !appliedModifiers.isEmpty()) {
            lore.add(" §d§l[付加能力]"); // 見出しは単独で1行

            List<String> mods = new ArrayList<>();
            for (Map.Entry<StatType, Double> entry : appliedModifiers.entrySet()) {
                mods.add(formatModifierStat(entry.getKey(), entry.getValue()));
            }
            // 中身だけを2列化
            addTwoColumnLore(lore, mods);
            lore.add(""); // セクション間に少し隙間
        }

        // --- [基礎ステータス] セクション ---
        lore.add(" §f§l[基礎ステータス]"); // 見出しは単独で1行

        List<String> baseStats = new ArrayList<>();
        for (StatType type : stats.getAllTypes()) {
            double flat = stats.getFlat(type);
            double percent = stats.getPercent(type);
            if (flat == 0 && percent == 0) continue;
            baseStats.add(formatStat(type, flat, percent, compact));
        }
        // 中身だけを2列化
        addTwoColumnLore(lore, baseStats);

        lore.add("§8§m-----------------------------");
        return lore;
    }

    /**
     * 2列レイアウト生成メソッド（修正版）
     * 目標幅を4pxグリッドに合わせることで安定させます
     */
    private static void addTwoColumnLore(List<String> mainLore, List<String> items) {
        if (items.isEmpty()) return;

        int maxLeftWidth = 0;
        // 左側に来る要素の最大幅を計算
        for (int i = 0; i < items.size(); i += 2) {
            int width = getMinecraftStringWidth(items.get(i));
            if (width > maxLeftWidth) maxLeftWidth = width;
        }

        // 目標幅の決定：最大幅 + 余白(12px)
        // 【重要】目標幅自体を「4の倍数」に切り上げることで、スペースのみでの調整を綺麗にします
        int padding = 12;
        int rawTarget = maxLeftWidth + padding;
        int targetWidth = (rawTarget % 4 == 0) ? rawTarget : (rawTarget + (4 - (rawTarget % 4)));

        for (int i = 0; i < items.size(); i += 2) {
            String left = items.get(i);
            // 右側の要素がない場合は左だけ追加して終了
            if (i + 1 >= items.size()) {
                mainLore.add(" " + left);
                break;
            }
            String right = items.get(i + 1);

            // パディング処理
            // デバッグ用ログが必要ならここにSystem.out.printlnを挟んでください
            mainLore.add(" " + padToWidth(left, targetWidth) + "§r" + right);
        }
    }

    /**
     * パディング生成メソッド（安定化版）
     * 太字スペース(5px)を使わず、通常スペース(4px)のみで調整します。
     * これにより1-3px程度の誤差は出ますが、列がガタつく現象（ジッター）を防げます。
     */
    private static String padToWidth(String text, int targetPx) {
        int currentPx = getMinecraftStringWidth(text);
        int neededPx = targetPx - currentPx;

        // 既に幅を超えている、または差が小さい場合でも最低1つのスペースを入れる
        if (neededPx <= 0) return text + "    ";

        StringBuilder sb = new StringBuilder(text);

        // 足りないピクセル数を4(スペースの幅)で割って、切り上げで個数を決定
        // (neededPx + 3) / 4 は Math.ceil(needed / 4.0) と同じ整数のテクニックです
        int spacesNeeded = (neededPx + 3) / 4;

        // 通常のスペースを追加
        // §rを入れることで、前のテキストの色や太字設定をリセットしてからスペースを入れます
        sb.append("§r");
        sb.append(" ".repeat(Math.max(0, spacesNeeded)));

        return sb.toString();
    }

    /**
     * 文字幅計算メソッド（調整版）
     * スクリーンショットで「右にズレる（＝幅を過小評価している）」傾向があるため
     * 一部の数値を安全側に修正しています。
     */
    private static int getCharWidth(char c) {
        // 1. 特殊アイコン
        // 矢印などが実際にはもう少し広い可能性があるため、12pxに統一して過小評価を防ぎます
        if (c == '❤') return 9;
        if (c == '➸') return 12; // 10 -> 12 に変更（ここがズレの主因の可能性大）
        if (c == '✠') return 12; // 10 -> 12 に変更
        if (c == '☆') return 9;
        if (c == '■') return 8;
        if (c == '⌛') return 9;
        if (c == '•') return 5;
        if (c == '»') return 9;

        // 2. 特殊な幅の半角記号
        if ("i.:,;|!".indexOf(c) != -1) return 2;
        if ("l'".indexOf(c) != -1) return 3;
        if ("I[]t".indexOf(c) != -1) return 4;
        if ("<>\"()*".indexOf(c) != -1) return 5;
        if (c == ' ') return 4;

        // 3. 全角文字 (日本語)
        // Minecraftの標準的な日本語フォントは描画12px+影1pxで13pxとするのが一般的ですが、
        // ズレる場合は安全策として「12」で見積もるか、逆に広めに取るか調整が必要です。
        // 現状維持の「13」で、上記のpadToWidth修正と組み合わせれば安定するはずです。
        if (c > 255) return 13;

        // 4. 標準的な英数字
        return 6;
    }

    // getMinecraftStringWidth メソッドは変更不要ですが、念のため記載
    private static int getMinecraftStringWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        int length = 0;
        boolean isBold = false;
        boolean nextIsColor = false;

        for (char c : text.toCharArray()) {
            if (c == '§') {
                nextIsColor = true;
                continue;
            }
            if (nextIsColor) {
                char lower = Character.toLowerCase(c);
                if (lower == 'l') {
                    isBold = true;
                } else if ("0123456789abcdefr".indexOf(lower) != -1) {
                    isBold = false;
                }
                nextIsColor = false;
                continue;
            }

            int charWidth = getCharWidth(c);
            if (isBold) {
                length += (c == ' ') ? 5 : (charWidth + 1);
            } else {
                length += charWidth;
            }
        }
        return length;
    }

    private static String formatModifierStat(StatType type, double value) {
        // アイコンとテキストの間に余計な空白を入れず、padToWidthに計算させる
        return "§d• " + type.getIcon() + " " + type.getDisplayName() + ": §d+" + String.format("%.1f", value);
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
