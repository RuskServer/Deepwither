package com.lunar_prototype.deepwither.quest;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 進行中の単一クエストの進捗状況を保持するクラス。
 * GeneratedQuestの目標を参照し、現在の達成数を記録します。
 */
public class QuestProgress {
    private final GeneratedQuest questDetails; // クエストの目標、報酬などの不変情報
    private final AtomicInteger currentCount;   // 現在の達成数

    public QuestProgress(GeneratedQuest questDetails) {
        this.questDetails = questDetails;
        this.currentCount = new AtomicInteger(0);
    }

    /**
     * 進捗を1増やします。
     */
    public int incrementProgress() {
        return currentCount.incrementAndGet();
    }

    /**
     * 現在の進捗数を設定します。
     */
    public void setProgress(int count) {
        currentCount.set(count);
    }

    /**
     * クエストが完了したかどうかをチェックします。
     */
    public boolean isComplete() {
        return currentCount.get() >= questDetails.getRequiredQuantity();
    }

    // ゲッター

    public GeneratedQuest getQuestDetails() {
        return questDetails;
    }

    public int getCurrentCount() {
        return currentCount.get();
    }
}