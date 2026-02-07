package com.lunar_prototype.deepwither.api;

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

    // 他のマネージャーのインターフェースも今後ここに追加
}
