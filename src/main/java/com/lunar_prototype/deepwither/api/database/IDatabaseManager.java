package com.lunar_prototype.deepwither.api.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * データベースへのアクセスと操作を管理するインターフェース。
 */
public interface IDatabaseManager {

    /**
     * 接続プールから接続を取得します。
     * 使用後は必ず close() (プールへ返却) してください。
     * @return データベース接続
     * @throws SQLException 接続失敗時
     */
    Connection getConnection() throws SQLException;

    /**
     * 非同期でクエリを実行します。
     * @param task 実行したいデータベース処理
     * @return 完了通知
     */
    CompletableFuture<Void> runAsync(Consumer<Connection> task);

    /**
     * 非同期でクエリを実行し、結果を返します。
     * @param task 実行したいデータベース処理と戻り値の定義
     * @param <T> 戻り値の型
     * @return クエリ結果の Future
     */
    <T> CompletableFuture<T> supplyAsync(Function<Connection, T> task);
}
