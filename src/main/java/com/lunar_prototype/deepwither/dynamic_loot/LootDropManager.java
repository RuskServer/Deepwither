package com.lunar_prototype.deepwither.dynamic_loot;

import com.lunar_prototype.deepwither.ItemFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LootDropManager {
    private final ItemFactory factory;

    // レアリティ定義（ItemFactoryのMAX_MODIFIERS_BY_RARITYのキーと一致させる）
    private final String[] RARITIES = {"&f&lコモン", "&a&lアンコモン", "&b&lレア", "&d&lエピック", "&6&lレジェンダリー"};

    public LootDropManager(ItemFactory factory) {
        this.factory = factory;
    }

    public String rollDrop(int lootLevel) {
        double factor = (double) lootLevel / 3500.0; // 0.0 ~ 1.0

        // ルートレベルに基づく各レアリティの重み設定（例）
        // レベルが高いほど、上位レアリティの基礎値(Base)にfactorが加算されるイメージ
        Map<String, Double> weights = new HashMap<>();
        weights.put("&6&lレジェンダリー", Math.pow(factor, 4) * 10); // LL3500で重み10
        weights.put("&d&lエピック", Math.pow(factor, 2) * 30);       // LL3500で重み30
        weights.put("&b&lレア", factor * 50 + 10);
        weights.put("&a&lアンコモン", 100.0);
        weights.put("&f&lコモン", 200.0 / (factor + 0.1)); // レベルが高いほどコモンは減る

        // 重み付きランダム抽選
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double r = Math.random() * totalWeight;
        double count = 0;

        for (String rarity : RARITIES) {
            count += weights.getOrDefault(rarity, 0.0);
            if (r <= count) {
                List<String> pool = factory.getItemsByRarity(rarity);
                if (pool.isEmpty()) continue; // そのレアリティにアイテムがなければ次へ
                return pool.get((int) (Math.random() * pool.size()));
            }
        }
        return null;
    }
}
