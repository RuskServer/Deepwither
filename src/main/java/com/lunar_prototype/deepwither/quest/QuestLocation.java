package com.lunar_prototype.deepwither.quest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;

/**
 * 特定の都市ギルド（受付NPC）に割り当てられたクエストリストを保持するクラス。
 * クエストエリアの情報は個々のGeneratedQuestではなく、locationNameに基づきQuestComponentPoolから取得されます。
 */
public class QuestLocation {
    private final String locationId; // 都市ID (例: "CITY_A_GUILD")
    private final String locationName; // 表示名 (例: "エターナル・シティー")

    private final List<GeneratedQuest> currentQuests; // 現在割り当てられているクエストリスト

    public static final int MAX_QUESTS = 5; // 各ギルドが持つクエストの最大数

    public QuestLocation(String locationId, String locationName, List<GeneratedQuest> initialQuests) {
        this.locationId = locationId;
        this.locationName = locationName;
        // 複数スレッドからの読み書き（クエスト更新とプレイヤーの取得）を考慮し、CopyOnWriteArrayListを使用
        this.currentQuests = new CopyOnWriteArrayList<>(initialQuests);
    }

    /**
     * 新しいクエストをリストに追加します。
     * @param quest 追加するGeneratedQuest
     */
    public void addQuest(GeneratedQuest quest) {
        if (currentQuests.size() < MAX_QUESTS) {
            currentQuests.add(quest);
        }
    }

    /**
     * 指定されたIDのクエストをリストから削除します。（プレイヤーが受けた場合などに使用）
     * @param questId 削除するクエストのUUID
     * @return 削除に成功した場合、削除されたGeneratedQuestオブジェクト。失敗した場合はnull。
     */
    public GeneratedQuest removeQuest(UUID questId) {
        GeneratedQuest removedQuest = null;
        // イテレータを使って安全に削除
        for (GeneratedQuest quest : currentQuests) {
            if (quest.getQuestId().equals(questId)) {
                currentQuests.remove(quest);
                removedQuest = quest;
                break;
            }
        }
        return removedQuest;
    }

    // --- ゲッターメソッド ---

    public String getLocationId() {
        return locationId;
    }

    public String getLocationName() {
        return locationName;
    }

    public List<GeneratedQuest> getCurrentQuests() {
        return currentQuests;
    }

    /**
     * 現在のクエスト数を取得します。
     */
    public int getQuestCount() {
        return currentQuests.size();
    }
}