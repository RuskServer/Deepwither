package com.lunar_prototype.deepwither.core.engine;

/**
 * ライフサイクルイベントを持つコンポーネントのためのインターフェース。
 */
public interface Lifecycle {

    /**
     * 有効化時に呼び出されます。
     */
    void onEnable();

    /**
     * 無効化時に呼び出されます。
     */
    void onDisable();
}
