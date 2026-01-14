package com.lunar_prototype.deepwither.aethelgard;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーが現在進行中のクエストデータと進捗状況を保持するクラス。
 * 永続化の単位となります。
 */
public class PlayerQuestData {

    private final UUID playerId;
    // 進行中のクエストID（UUID）とその進捗データ
    private Map<UUID, QuestProgress> activeQuests;

    public PlayerQuestData(UUID playerId) {
        this.playerId = playerId;
        this.activeQuests = new ConcurrentHashMap<>();
    }

    /**
     * 新しいクエストを追加します。
     * @param questId クエストのUUID
     * @param generatedQuest クエスト内容（GeneratedQuestは不変データと仮定）
     */
    public void addQuest(UUID questId, GeneratedQuest generatedQuest) {
        if (!activeQuests.containsKey(questId)) {
            // 新しい進捗オブジェクトを作成し、登録
            QuestProgress progress = new QuestProgress(generatedQuest);
            activeQuests.put(questId, progress);
        }
    }

    /**
     * 特定のクエストの進捗を取得します。
     * @param questId クエストのUUID
     * @return QuestProgress または null
     */
    public QuestProgress getProgress(UUID questId) {
        return activeQuests.get(questId);
    }

    /**
     * 全ての進行中のクエストを取得します。
     * 修正3: ゲッター自体でnullチェックを行い、呼び出し側でのNPEを完全に防ぐ
     */
    public Map<UUID, QuestProgress> getActiveQuests() {
        if (activeQuests == null) {
            activeQuests = new ConcurrentHashMap<>();
        }
        return activeQuests;
    }

    /**
     * クエストを完了リストから削除します。
     * @param questId 完了またはキャンセルされたクエストのUUID
     */
    public void removeQuest(UUID questId) {
        activeQuests.remove(questId);
    }

    // 永続化のためのゲッター、セッター（実際の実装ではJSON/YAMLライブラリが自動で行うことが多い）

    public UUID getPlayerId() {
        return playerId;
    }
}