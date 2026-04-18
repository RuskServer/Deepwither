package com.lunar_prototype.deepwither.api.playerdata;

import com.lunar_prototype.deepwither.core.PlayerCache;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 各マネージャーがプレイヤーデータのロード・セーブを担当するためのインターフェース。
 * PlayerDataManager に登録することで、ログイン時・ログアウト時に一括して呼び出されます。
 */
public interface IPlayerDataHandler {

    /**
     * データベース等からデータを読み込み、PlayerCacheに格納します。
     * @param uuid 対象プレイヤーのUUID
     * @param cache 対象プレイヤーのキャッシュコンテナ
     * @return 処理の完了を表すFuture
     */
    CompletableFuture<Void> loadData(UUID uuid, PlayerCache cache);

    /**
     * PlayerCacheのデータをデータベース等に保存します。
     * @param uuid 対象プレイヤーのUUID
     * @param cache 対象プレイヤーのキャッシュコンテナ
     * @return 処理の完了を表すFuture
     */
    CompletableFuture<Void> saveData(UUID uuid, PlayerCache cache);
    
    /**
     * データロード時・セーブ時に一意な識別子として使用されるマネージャー名（デバッグ用）
     * @return マネージャーの識別名
     */
    default String getHandlerName() {
        return this.getClass().getSimpleName();
    }
}
