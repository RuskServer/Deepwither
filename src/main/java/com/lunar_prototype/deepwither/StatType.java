package com.lunar_prototype.deepwither;

import java.util.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * ステータスタイプの列挙。
 */
public enum StatType {
    ATTACK_DAMAGE("攻撃力", NamedTextColor.RED, "➸"),
    ATTACK_SPEED("攻撃速度", NamedTextColor.WHITE, "➸"),
    PROJECTILE_DAMAGE("発射体ダメージ", NamedTextColor.WHITE, "➸"),
    PROJECTILE_SPEED("弾速", NamedTextColor.WHITE, "➸"),
    MAGIC_DAMAGE("魔法攻撃力", NamedTextColor.AQUA, "■"),
    MAGIC_AOE_BONUS("魔法AoE強化", NamedTextColor.AQUA, "■"),
    MAGIC_BURST_BONUS("魔法バースト強化", NamedTextColor.AQUA, "■"),
    DEFENSE("防御力", NamedTextColor.GREEN, "✠"),
    MAGIC_RESIST("魔法耐性", NamedTextColor.BLUE, "✠"),
    MAGIC_PENETRATION("魔法貫通", NamedTextColor.BLUE, "■"),
    CRIT_CHANCE("会心率", NamedTextColor.YELLOW, "■"),
    CRIT_DAMAGE("会心ダメージ", NamedTextColor.YELLOW, "■"),
    MAX_HEALTH("最大HP", NamedTextColor.DARK_RED, "❤"),
    HP_REGEN("HP回復", NamedTextColor.DARK_RED, "❤"),
    MOVE_SPEED("移動速度", NamedTextColor.LIGHT_PURPLE, "■"),
    KNOCKBACK_RESISTANCE("ノックバック耐性", NamedTextColor.GRAY, "■"),
    SKILL_POWER("スキル威力", NamedTextColor.AQUA, "■"),
    REACH("リーチ増加", NamedTextColor.AQUA, "■"),
    REDUCES_MOVEMENT_SPEED_DECREASE("移動速度低下軽減", NamedTextColor.AQUA, "■"),
    DROP_RESISTANCE("落下耐性", NamedTextColor.AQUA, "■"),
    MAX_MANA("最大マナ", NamedTextColor.AQUA, "☆"),
    COOLDOWN_REDUCTION("クールダウン短縮", NamedTextColor.DARK_GRAY, "⌛"),
    SHIELD_BLOCK_RATE("盾の減衰率", NamedTextColor.LIGHT_PURPLE, "■"),
    STR("筋力", NamedTextColor.RED, "❖"),
    VIT("体力", NamedTextColor.GREEN, "❤"),
    MND("精神力", NamedTextColor.AQUA, "✦"),
    INT("知性", NamedTextColor.LIGHT_PURPLE, "✎"),
    AGI("素早さ", NamedTextColor.YELLOW, "➤"),
    SCYTHE_DAMAGE("鎌ダメージ", NamedTextColor.RED, "⚔"),
    GREATSWORD_DAMAGE("大剣ダメージ", NamedTextColor.RED, "⚔"),
    SPEAR_DAMAGE("槍ダメージ", NamedTextColor.RED, "⚔"),
    AXE_DAMAGE("斧ダメージ", NamedTextColor.RED, "⚔"),
    MACE_DAMAGE("メイスダメージ", NamedTextColor.RED, "⚔"),
    SWORD_DAMAGE("剣ダメージ", NamedTextColor.RED, "⚔"),
    MACHETE_DAMAGE("マチェットダメージ", NamedTextColor.RED, "⚔"),
    HAMMER_DAMAGE("ハンマーダメージ", NamedTextColor.RED, "⚔"),
    HALBERD_DAMAGE("ハルバードダメージ", NamedTextColor.RED, "⚔"),
    BLEED_CHANCE("出血付与", NamedTextColor.DARK_RED, "🩸"),
    LIFESTEAL("ドレイン", NamedTextColor.RED, "❤"),
    FREEZE_CHANCE("凍結付与", NamedTextColor.AQUA, "❄"),
    AOE_CHANCE("拡散攻撃", NamedTextColor.YELLOW, "💥"),
    MANA_REGEN("マナ回復", NamedTextColor.AQUA, "✦");

    private final String displayName;
    private final NamedTextColor color;
    private final String icon;

    StatType(String displayName, NamedTextColor color, String icon) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }
    
    public boolean isCoreAttribute() {
        return this == STR || this == VIT || this == AGI || this == INT || this == MND;
    }

    public static java.util.List<StatType> getCoreAttributeTypes() {
        return java.util.Arrays.asList(STR, VIT, AGI, INT, MND);
    }
}

/**
 * Lore表示を生成するビルダー。
 */
class LoreBuilder {

    private static final Component SEPARATOR = Component.text("----------------------------", NamedTextColor.GRAY)
            .decoration(TextDecoration.STRIKETHROUGH, true)
            .decoration(TextDecoration.ITALIC, false);



    /**
     * 2列レイアウト用のメインビルドロジック（修正版）
     */
    public static List<Component> build(StatMap stats, boolean compact, String itemType, String artifactFullsetType,
            List<String> flavorText,
            ItemLoader.RandomStatTracker tracker, String rarity, Map<StatType, Double> appliedModifiers,
            FabricationGrade grade, List<Component> runeLore) {
        List<Component> lore = new ArrayList<>();
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();

        // --- ヘッダー ---
        if (grade != null) {
            lore.add(serializer.deserialize(grade.getDisplayName()));
        }

        Component infoLine = Component.empty();
        if (rarity != null) {
            infoLine = infoLine.append(LegacyComponentSerializer.legacyAmpersand().deserialize(rarity));
        }
        if (itemType != null) {
            if (rarity != null) {
                infoLine = infoLine.append(Component.text(" | ", NamedTextColor.WHITE));
            }
            infoLine = infoLine.append(Component.text(itemType, NamedTextColor.GRAY));
        }
        if (!infoLine.equals(Component.empty()))
            lore.add(infoLine.decoration(TextDecoration.ITALIC, false));

        if (artifactFullsetType != null && !artifactFullsetType.isBlank()) {
            lore.add(Component.text("フルセット種別: ", NamedTextColor.GRAY)
                    .append(ItemFactory.getArtifactSetDisplayName(artifactFullsetType))
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> fullsetNotes = ItemFactory.getArtifactSetLoreLines(artifactFullsetType);
            if (!fullsetNotes.isEmpty()) {
                lore.add(Component.text("セット効果: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                for (Component line : fullsetNotes) {
                    lore.add(Component.text("  - ", NamedTextColor.DARK_GRAY)
                            .append(line)
                            .decoration(TextDecoration.ITALIC, false));
                }
            }
        }

        if (tracker != null) {
            double ratio = tracker.getRatio() * 100.0;
            NamedTextColor color = (ratio >= 90) ? NamedTextColor.GOLD
                    : (ratio >= 70) ? NamedTextColor.YELLOW
                            : (ratio >= 50) ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            lore.add(Component.text("品質: ", NamedTextColor.WHITE).append(Component.text(Math.round(ratio) + "%", color))
                    .decoration(TextDecoration.ITALIC, false));
        }

        // --- フレーバー ---
        if (flavorText != null && !flavorText.isEmpty()) {
            lore.add(Component.empty());
            for (String line : flavorText) {
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&8&o" + line)
                        .decoration(TextDecoration.ITALIC, true)); // Keep flavor italic but controlled
            }
        }

        lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.STRIKETHROUGH, true).decoration(TextDecoration.ITALIC, false));

        // --- [ルーン] セクション ---
        if (runeLore != null && !runeLore.isEmpty()) {
            lore.add(Component.text(" [ソケット]", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            for (Component rune : runeLore) {
                lore.add(Component.text(" ").append(rune).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
        }

        // --- [付加能力] セクション ---
        if (appliedModifiers != null && !appliedModifiers.isEmpty()) {
            lore.add(Component.text(" [付加能力]", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> mods = new ArrayList<>();
            for (Map.Entry<StatType, Double> entry : appliedModifiers.entrySet()) {
                mods.add(formatModifierStat(entry.getKey(), entry.getValue()));
            }
            // 中身だけを2列化
            addTwoColumnLore(lore, mods);
            lore.add(Component.empty()); // セクション間に少し隙間
        }

        // --- [基礎ステータス] セクション ---
        lore.add(Component.text(" [基礎ステータス]", NamedTextColor.WHITE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> baseStats = new ArrayList<>();
        for (StatType type : stats.getAllTypes()) {
            double flat = stats.getFlat(type);
            double percent = stats.getPercent(type);
            if (flat == 0 && percent == 0)
                continue;
            baseStats.add(formatStat(type, flat, percent, compact));
        }
        // 中身だけを2列化
        addTwoColumnLore(lore, baseStats);

        lore.add(Component.text("-----------------------------", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.STRIKETHROUGH, true));
        return lore;
    }

    // 2列レイアウト生成メソッドの微調整
    private static void addTwoColumnLore(List<Component> mainLore, List<Component> items) {
        if (items.isEmpty())
            return;

        int maxLeftWidth = 0;
        List<String> legacyItems = new ArrayList<>();
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();

        // Convert to legacy strings for width calculation
        for (Component item : items) {
            String legacy = serializer.serialize(item);
            legacyItems.add(legacy);
        }

        // まず左側にくる要素の中で「最大の幅」を計算
        for (int i = 0; i < items.size(); i += 2) {
            int width = getMinecraftStringWidth(legacyItems.get(i));
            if (width > maxLeftWidth)
                maxLeftWidth = width;
        }

        // 目標幅の設定
        int rawTarget = maxLeftWidth + 8;
        int targetWidth = (rawTarget % 4 == 0) ? rawTarget : (rawTarget + (4 - (rawTarget % 4)));

        for (int i = 0; i < items.size(); i += 2) {
            Component left = items.get(i);
            String leftLegacy = legacyItems.get(i);

            if (i + 1 >= items.size()) {
                mainLore.add(Component.text(" ").append(left).decoration(TextDecoration.ITALIC, false));
                break;
            }
            Component right = items.get(i + 1);

            // 生成
            Component paddedLeft = padToWidth(left, leftLegacy, targetWidth);
            mainLore.add(Component.text(" ").append(paddedLeft).append(right).decoration(TextDecoration.ITALIC, false));
        }
    }

    /**
     * パディング生成メソッド（安定化版）
     */
    private static Component padToWidth(Component textComponent, String textLegacy, int targetPx) {
        int currentPx = getMinecraftStringWidth(textLegacy);

        // 現在の幅がすでに目標を超えている、または差が小さすぎる場合
        if (currentPx >= targetPx) {
            return textComponent.append(Component.text("  "));
        }

        int neededPx = targetPx - currentPx;

        // 必要なピクセル数をスペースの幅(4px)で割り、切り上げる
        int spacesNeeded = (int) Math.ceil(neededPx / 4.0);

        // 念のため最低1つは入れる
        if (spacesNeeded < 1)
            spacesNeeded = 1;

        return textComponent.append(Component.text(" ".repeat(spacesNeeded)));
    }

    /**
     * 文字幅計算メソッド（調整版）
     */
    private static int getCharWidth(char c) {
        // 1. 特殊アイコン (ここがズレの原因になりやすいので少し大きめに見積もる)
        if (c == '❤')
            return 9;
        if (c == '➸')
            return 11; // 10->11
        if (c == '✠')
            return 11; // 10->11
        if (c == '☆')
            return 9;
        if (c == '■')
            return 8;
        if (c == '⌛')
            return 10; // 9->10 (ここが怪しい)
        if (c == '•')
            return 5;
        if (c == '»')
            return 9;

        // 2. 特殊な幅の半角記号
        if ("i.:,;|!".indexOf(c) != -1)
            return 2;
        if ("l'".indexOf(c) != -1)
            return 3;
        if ("I[]t".indexOf(c) != -1)
            return 4;
        if ("<>\\\"()*".indexOf(c) != -1)
            return 5;
        if (c == ' ')
            return 4;

        // 3. 全角文字
        if (c > 255)
            return 13;

        // 4. 標準
        return 6;
    }

    // getMinecraftStringWidth メソッドは変更不要ですが、念のため記載
    private static int getMinecraftStringWidth(String text) {
        if (text == null || text.isEmpty())
            return 0;
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

    private static Component formatModifierStat(StatType type, double value) {
        return Component.text("• ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(type.getIcon() + " " + type.getDisplayName() + ": "))
                .append(Component.text("+" + String.format("%.1f", value), NamedTextColor.LIGHT_PURPLE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component formatStat(StatType type, double flat, double percent, boolean compact) {
        Component valComp;
        if (percent != 0) {
            valComp = Component.text(String.format("%.1f", flat))
                    .append(Component.text(" ("))
                    .append(Component.text("+" + Math.round(percent) + "%", NamedTextColor.GREEN))
                    .append(Component.text(")", NamedTextColor.WHITE));
        } else {
            valComp = Component.text(String.valueOf(flat));
        }

        return Component.text("• ", NamedTextColor.WHITE)
                .append(Component.text(type.getIcon() + " " + type.getDisplayName() + ": "))
                .append(valComp.color(NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
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
