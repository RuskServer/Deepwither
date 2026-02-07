package com.lunar_prototype.deepwither.api.database;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ResultSetの1行をオブジェクトにマッピングするための関数型インターフェース。
 * @param <T> マッピング後の型
 */
@FunctionalInterface
public interface RowMapper<T> {
    /**
     * 現在のResultSetの行をオブジェクトに変換します。
     * @param rs ResultSet
     * @return 変換後のオブジェクト
     * @throws SQLException SQLエラー時
     */
    T map(ResultSet rs) throws SQLException;
}
