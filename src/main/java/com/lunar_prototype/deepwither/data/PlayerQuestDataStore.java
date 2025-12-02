package com.lunar_prototype.deepwither.data;

import com.lunar_prototype.deepwither.quest.PlayerQuestData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * プレイヤー個人のクエスト進行状況を永続化するための抽象インターフェース。
 */
public interface PlayerQuestDataStore {

    /**
     * 特定のプレイヤーのクエストデータを非同期でロードします。
     * データが存在しない場合は、初期化された新しいPlayerQuestDataを返します。
     * @param playerId プレイヤーのUUID
     * @return ロードされたPlayerQuestDataを含むCompletableFuture
     */
    CompletableFuture<PlayerQuestData> loadQuestData(UUID playerId);

    /**
     * 特定のプレイヤーのクエストデータを非同期で保存します。
     * @param data 保存するPlayerQuestData
     * @return 処理結果を示すCompletableFuture
     */
    CompletableFuture<Void> saveQuestData(PlayerQuestData data);
}