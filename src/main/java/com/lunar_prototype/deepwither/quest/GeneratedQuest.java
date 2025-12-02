package com.lunar_prototype.deepwither.quest;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LLMによってテキストが生成された後の、最終的なクエストインスタンス。
 */
@SerializableAs("GeneratedQuest")
public class GeneratedQuest implements ConfigurationSerializable {
    private final UUID questId;
    private final String title;               // LLMが生成するタイトル (オプション)
    private final String questText;           // LLMが生成した依頼文全文

    // ゲームプレイに必要なデータ
    private final String targetMobId;          // 討伐対象のMob ID (String)
    private final int requiredQuantity;        // 必要な討伐数
    private final LocationDetails locationDetails; // クエストの舞台となる場所の詳細
    private final RewardDetails rewardDetails;     // クエストの報酬の詳細 (NEW)

    public GeneratedQuest(String title, String questText, String targetMobId, int requiredQuantity, LocationDetails locationDetails, RewardDetails rewardDetails) {
        this.questId = UUID.randomUUID();
        this.title = title;
        this.questText = questText;
        this.targetMobId = targetMobId;
        this.requiredQuantity = requiredQuantity;
        this.locationDetails = locationDetails;
        this.rewardDetails = rewardDetails;
    }

    public String getQuestText() {
        return questText;
    }

    public UUID getQuestId() {
        return questId;
    }

    public int getRequiredQuantity() {
        return requiredQuantity;
    }

    public String getTitle() {
        return title;
    }

    public LocationDetails getLocationDetails() {
        return locationDetails;
    }

    public String getTargetMobId() {
        return targetMobId;
    }

    public RewardDetails getRewardDetails() {
        return rewardDetails;
    }

    static {
        ConfigurationSerialization.registerClass(GeneratedQuest.class);
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        // QuestIdはPlayerQuestDataでキーとして使用するため、ここでは不要
        map.put("title", this.title);
        map.put("questText", this.questText);
        map.put("targetMobId", this.targetMobId);
        map.put("requiredQuantity", this.requiredQuantity);

        // ネストされたConfigurationSerializableオブジェクトは、そのままセット可能
        map.put("locationDetails", this.locationDetails); // LocationDetailsもConfigurationSerializableである前提
        map.put("rewardDetails", this.rewardDetails);     // RewardDetailsもConfigurationSerializableである前提
        return map;
    }

    // YAMLからデータをロードするための静的ファクトリメソッド
    public static GeneratedQuest deserialize(Map<String, Object> map) {
        // Mapから値を取得し、必要に応じて型を変換
        String title = (String) map.get("title");
        String questText = (String) map.get("questText");
        String targetMobId = (String) map.get("targetMobId");
        int requiredQuantity = (int) map.get("requiredQuantity");

        // Bukkitは自動的にConfigurationSerializableをデシリアライズしてくれる
        LocationDetails locationDetails = (LocationDetails) map.get("locationDetails");
        RewardDetails rewardDetails = (RewardDetails) map.get("rewardDetails");

        // UUIDはPlayerQuestDataのキーで管理されているため、ここではnullを渡すか、適切なコンストラクタを使用
        return new GeneratedQuest(title, questText, targetMobId, requiredQuantity, locationDetails, rewardDetails);
    }

    /**
     * プレイヤーに表示するためのクエスト詳細を整形して返す
     */
    public String getFormattedQuestDetails() {
        String locationDisplay = String.format("%s (%s, X:%.1f Y:%.1f Z:%.1f)",
                this.locationDetails.getName(),
                this.locationDetails.getHierarchy(),
                this.locationDetails.getX(),
                this.locationDetails.getY(),
                this.locationDetails.getZ());

        String rewardDisplay = this.rewardDetails.getLlmRewardText();

        return "--- クエスト詳細 ---\n" +
                "タイトル: " + this.title + "\n" +
                "依頼文:\n" + this.questText + "\n" +
                "討伐目標ID: " + this.targetMobId + "\n" +
                "必要数: " + this.requiredQuantity + "\n" +
                "場所: " + locationDisplay + "\n" +
                "報酬: " + rewardDisplay + "\n" +
                "----------------------";
    }
}