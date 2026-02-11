package com.lunar_prototype.deepwither.api;

import com.lunar_prototype.deepwither.api.database.IDatabaseManager;
import com.lunar_prototype.deepwither.api.stat.IStatManager;

/**
 * Deepwitherの提供する全機能にアクセスするためのメインAPIエントリポイント。
 */
public interface DeepwitherAPI {

    /**
     * クラスまたはインターフェースを指定してマネージャーを取得します。
     * @param clazz マネージャーのクラス
     * @return マネージャーのインスタンス
     */
    <T> T get(Class<T> clazz);

    /**
     * ステータス管理マネージャーを取得します。
     * @return IStatManager
     */
    IStatManager getStatManager();

    /**
     * データベース管理マネージャーを取得します。
     * @return IDatabaseManager
     */
    IDatabaseManager getDatabaseManager();

    /**
     * アイテム管理マネージャーを取得します。
     * @return IItemFactory
     */
    IItemFactory getItemFactory();

    // 他のマネージャーのインターフェースも今後ここに追加
}
