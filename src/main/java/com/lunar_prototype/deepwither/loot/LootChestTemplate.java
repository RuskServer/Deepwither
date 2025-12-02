package com.lunar_prototype.deepwither.loot;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 特定のルートチェストの種類（例: Common_Aether_Chest）のテンプレート。
 */
public class LootChestTemplate {
    private final String name;
    private final int minItems;
    private final int maxItems;
    private final List<LootEntry> entries;
    private final double totalChance; // 抽選効率化のための合計確率

    public LootChestTemplate(String name, int minItems, int maxItems, List<LootEntry> entries) {
        this.name = name;
        this.minItems = minItems;
        this.maxItems = maxItems;
        this.entries = entries;
        this.totalChance = entries.stream().mapToDouble(LootEntry::getChance).sum();
    }

    /**
     * YAML ConfigurationSectionからルートチェストテンプレートをロードするためのファクトリメソッド
     * (lootchest.ymlのテンプレート部分を読み込む)
     */
    public static LootChestTemplate loadFromConfig(String name, ConfigurationSection section) {
        // 1. 基本設定のロード
        int minItems = section.getInt("min_items", 1);
        int maxItems = section.getInt("max_items", 3);

        List<LootEntry> entries = new ArrayList<>();

        // 2. ★修正点: entriesをリスト形式 (List<?>) でロード
        List<?> entriesList = section.getList("entries");

        if (entriesList != null) {
            for (Object obj : entriesList) {
                // リスト内の要素が Map (アイテム定義) であることを確認
                if (obj instanceof Map) {
                    // 以前のステップで型安全に修正された loadFromMap を使用
                    LootEntry entry = LootEntry.loadFromMap((Map<?, ?>) obj);

                    if (entry != null) {
                        entries.add(entry);
                    }
                } else {
                    // リスト要素の型が不正な場合の警告
                    System.err.println("[LootConfig Error] Template '" + name + "'のentriesリスト内の要素が不正な形式 (Mapではない) です。スキップしました。");
                }
            }
        }

        // 3. エラー/デバッグチェック: エントリーが空の場合の警告
        if (entries.isEmpty()) {
            // デバッグログで確認された Total Chance: 0.0000 の原因を特定しやすくする
            System.err.println("[LootConfig CRITICAL] Template '" + name + "' に有効なアイテムエントリーが一つもありません。チェストは空になります。");
        }

        // 最小アイテム数が最大アイテム数を超えないように調整（防御的な処理）
        if (minItems > maxItems) {
            System.err.println("[LootConfig Warning] min_items (" + minItems + ") が max_items (" + maxItems + ") を超えています。min_itemsをmax_itemsに合わせます。");
            minItems = maxItems;
        }


        return new LootChestTemplate(name, minItems, maxItems, entries);
    }

    // ゲッター
    public String getName() { return name; }
    public int getMinItems() { return minItems; }
    public int getMaxItems() { return maxItems; }
    public List<LootEntry> getEntries() { return entries; }
    public double getTotalChance() { return totalChance; }
}