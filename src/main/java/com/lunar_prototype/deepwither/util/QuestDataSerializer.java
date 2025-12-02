package com.lunar_prototype.deepwither.util;

import com.lunar_prototype.deepwither.quest.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.UUID;

/**
 * PlayerQuestDataとその内部オブジェクト（QuestProgress, GeneratedQuest）を
 * YamlConfigurationとの間で変換（シリアライズ/デシリアライズ）する静的ヘルパークラス。
 */
public class QuestDataSerializer {

    public static void serializePlayerQuestData(PlayerQuestData data, YamlConfiguration config) {
        ConfigurationSection activeQuestsSection = config.createSection("ActiveQuests");

        for (var entry : data.getActiveQuests().entrySet()) {
            UUID questId = entry.getKey();
            QuestProgress progress = entry.getValue();

            ConfigurationSection questSection = activeQuestsSection.createSection(questId.toString());

            // 1. 進捗の保存
            questSection.set("progress", progress.getCurrentCount());

            // 2. クエスト詳細（GeneratedQuest）の保存
            // GeneratedQuestがConfigurationSerializableの場合、自動でMapに変換される
            questSection.set("details", progress.getQuestDetails()); // ★ ConfigurationSerializableとして直接セット
        }
    }

    // --- デシリアライズ (YAML -> Java) ---

    public static PlayerQuestData deserializePlayerQuestData(UUID playerId, YamlConfiguration config) {
        PlayerQuestData data = new PlayerQuestData(playerId);
        ConfigurationSection activeQuestsSection = config.getConfigurationSection("ActiveQuests");

        if (activeQuestsSection != null) {
            for (String questIdStr : activeQuestsSection.getKeys(false)) {
                try {
                    UUID questId = UUID.fromString(questIdStr);
                    ConfigurationSection questSection = activeQuestsSection.getConfigurationSection(questIdStr);

                    int currentCount = questSection.getInt("progress", 0);

                    // 1. クエスト詳細（GeneratedQuest）をYAMLから復元
                    // ConfigurationSerializableとして登録されているため、get()で直接オブジェクトを取得できる
                    GeneratedQuest details = (GeneratedQuest) questSection.get("details"); // ★ 直接オブジェクトを取得

                    if (details == null) {
                        throw new IllegalStateException("Quest details could not be deserialized.");
                    }

                    // 2. QuestProgressオブジェクトを組み立て
                    QuestProgress progress = new QuestProgress(details);
                    progress.setProgress(currentCount);

                    // 3. PlayerQuestDataに追加
                    data.getActiveQuests().put(questId, progress);

                } catch (Exception e) {
                    System.err.println("Failed to deserialize quest " + questIdStr + " for player " + playerId + ": " + e.getMessage());
                }
            }
        }
        return data;
    }
}