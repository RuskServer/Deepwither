package com.lunar_prototype.deepwither.loot;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.Random;

/**
 * ルートチェストから出現する可能性のある単一のアイテムと確率の定義。
 */
public class LootEntry {
    private final String itemId;
    private final boolean isCustom;
    private final double chance; // 抽選される確率 (0.0 - 1.0)
    private final int minStack;
    private final int maxStack;

    public LootEntry(String itemId, boolean isCustom, double chance, int minStack, int maxStack) {
        this.itemId = itemId;
        this.isCustom = isCustom;
        this.chance = chance;
        this.minStack = minStack;
        this.maxStack = maxStack;
    }

    // YAML ConfigurationSectionからロードするためのファクトリメソッド
    /**
     * Map形式（YAMLのリスト要素）からLootEntryをロードするためのファクトリメソッド
     */
    public static LootEntry loadFromMap(Map<?, ?> map) {

        // 1. id (必須)
        String id = (String) map.get("id");
        // IDがない場合は致命的なエラーとして null を返すか、例外をスローすべき
        if (id == null) {
            System.err.println("[LootConfig Error] Loot entry 'id' is missing.");
            return null;
        }

        // 2. isCustom (デフォルト: false)
        // Mapから取得し、Boolean型であることをチェックしてからキャストする
        boolean isCustom = false;
        Object customObj = map.get("is_custom");
        if (customObj instanceof Boolean) {
            isCustom = (Boolean) customObj;
        }

        // 3. chance (デフォルト: 0.0)
        double chance = 0.0;
        Object chanceObj = map.get("chance");
        // Number型（Double, Integerなど）として取得し、Doubleに変換
        if (chanceObj instanceof Number) {
            chance = ((Number) chanceObj).doubleValue();
        }

        // 4. minStack (デフォルト: 1)
        int minStack = 1;
        Object minStackObj = map.get("min_stack");
        if (minStackObj instanceof Integer) {
            minStack = (Integer) minStackObj;
        }

        // 5. maxStack (デフォルト: 1)
        int maxStack = 1;
        Object maxStackObj = map.get("max_stack");
        if (maxStackObj instanceof Integer) {
            maxStack = (Integer) maxStackObj;
        }

        // 最小スタック数が最大スタック数を超えないように調整
        if (minStack > maxStack) {
            System.err.println("[LootConfig Warning] min_stack (" + minStack + ") was greater than max_stack (" + maxStack + ") for ID: " + id + ". Setting minStack = maxStack.");
            minStack = maxStack;
        }

        return new LootEntry(id, isCustom, chance, minStack, maxStack);
    }

    // ゲッター
    public String getItemId() { return itemId; }
    public boolean isCustom() { return isCustom; }
    public double getChance() { return chance; }
    public int getMinStack() { return minStack; }
    public int getMaxStack() { return maxStack; }

    /**
     * このエントリに基づき、スタックサイズを決定して ItemStack を生成します。
     * @param random Randomインスタンス
     * @return 生成された ItemStack または null (チャンスが0の場合)
     */
    public ItemStack createItem(Random random) {
        if (minStack <= 0) return null; // 何も出ないエントリーの場合

        // スタック数をランダムに決定
        int stackSize = minStack + (maxStack > minStack ? random.nextInt(maxStack - minStack + 1) : 0);

        if (isCustom) {
            // カスタムアイテムを生成（ItemFactoryが実装されている前提）
            return Deepwither.getInstance().getItemFactory().getCustomCountItemStack(itemId, stackSize);
        } else {
            // バニラアイテムを生成
            Material material = Material.matchMaterial(itemId.toUpperCase());
            if (material != null) {
                return new ItemStack(material, stackSize);
            } else {
                return null; // 不正なバニラID
            }
        }
    }
}