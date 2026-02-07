package com.lunar_prototype.deepwither.api.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * データベースへのアクセスと操作を管理するインターフェース。
 */
public interface IDatabaseManager {

    /**
     * 接続プールから接続を取得します。
     */
    Connection getConnection() throws SQLException;

    // --- 高レベル API (抽象化) ---

    /**
     * 更新クエリ (INSERT, UPDATE, DELETE) を実行します。
     * @param sql SQL文字列
     * @param params パラメータ
     * @return 影響を受けた行数
     */
    int execute(String sql, Object... params);

    /**
     * 単一の行を取得するクエリを実行します。
     * @param sql SQL文字列
     * @param mapper マッピング関数
     * @param params パラメータ
     * @return 結果（存在しない場合は empty）
     */
    <T> Optional<T> querySingle(String sql, RowMapper<T> mapper, Object... params);

    /**
     * 複数の行を取得するクエリを実行します。
     * @param sql SQL文字列
     * @param mapper マッピング関数
     * @param params パラメータ
     * @return 結果のリスト
     */
    <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params);

     /** --- 非同期サポート ---
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
