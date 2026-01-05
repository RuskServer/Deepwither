package com.lunar_prototype.deepwither.aethelgard;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.HashMap;
import java.util.Map;

/**
 * クエストの報酬詳細（通貨、経験値、アイテムのID、表示名、個数）を保持するクラス。
 * 報酬を実際に渡すための情報（IDと個数）と、表示するための情報（表示名）を両方保持する。
 */
@SerializableAs("RewardDetails")
public class RewardDetails implements ConfigurationSerializable {
    private final int guildCoin;
    private final int experiencePoints;

    // ゲームロジック用
    private final String itemRewardId;       // カスタムアイテムID (例: "SMALL_HEALTH_POTION")
    private final int itemQuantity;          // アイテムの個数

    // LLMプロンプト/表示用
    private final String itemRewardDisplayName; // 解決済みのアイテム表示名 (例: "小さな回復薬")

    /**
     * @param guildCoin ギルドコイン
     * @param experiencePoints 経験値
     * @param itemRewardId カスタムアイテムID
     * @param itemRewardDisplayName 解決済みのアイテム表示名
     * @param itemQuantity アイテムの個数
     */
    public RewardDetails(int guildCoin, int experiencePoints, String itemRewardId, String itemRewardDisplayName, int itemQuantity) {
        this.guildCoin = guildCoin;
        this.experiencePoints = experiencePoints;
        this.itemRewardId = itemRewardId;
        this.itemRewardDisplayName = itemRewardDisplayName;
        this.itemQuantity = itemQuantity;
    }

    public int getGuildCoin() {
        return guildCoin;
    }

    public int getExperiencePoints() {
        return experiencePoints;
    }

    /**
     * 報酬を実際にプレイヤーに渡す際に使用するカスタムアイテムIDを取得します。
     */
    public String getItemRewardId() {
        return itemRewardId;
    }

    public String getItemRewardDisplayName() {
        return itemRewardDisplayName;
    }

    /**
     * 報酬を実際にプレイヤーに渡す際に使用するアイテムの個数を取得します。
     */
    public int getItemQuantity() {
        return itemQuantity;
    }

    static {
        ConfigurationSerialization.registerClass(RewardDetails.class);
    }

    /**
     * YAMLに保存するためのデータをMapとして提供します。
     */
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("exp", this.experiencePoints);
        map.put("coin", this.guildCoin);
        map.put("item_id", this.itemRewardId);
        map.put("itemrewarddisplayname", this.itemRewardDisplayName);
        map.put("itemquantity", this.itemQuantity);
        return map;
    }

    /**
     * YAMLからデータをロードするための静的ファクトリメソッドです。
     */
    public static RewardDetails deserialize(Map<String, Object> map) {
        // nullチェックや型チェックを適切に追加してください
        int exp = (int) map.get("exp");
        int coin = (int) map.get("coin");
        String itemId = (String) map.get("item_id");
        String itemRewardDisplayName = (String) map.get("itemrewarddisplayname");
        int itemQuantity = (int) map.get("itemquantity");

        return new RewardDetails(exp, coin, itemId, itemRewardDisplayName, itemQuantity);
    }

    /**
     * LLMプロンプトやUI表示に含めるための報酬テキストを生成します。
     * 表示名と個数をここで結合することで、QuestGenerator側の結合処理を不要にします。
     */
    public String getLlmRewardText() {
        // 例: "小さな回復薬 x3"
        String itemText = String.format("%s x%d", this.itemRewardDisplayName, this.itemQuantity);

        // 例: "ギルドコイン 250枚、経験値 700、アイテム: 小さな回復薬 x3"
        return String.format(
                "%d ゴールド、経験値 %d、アイテム: %s",
                this.guildCoin,
                this.experiencePoints,
                itemText
        );
    }
}