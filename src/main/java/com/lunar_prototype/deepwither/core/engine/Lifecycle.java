package com.lunar_prototype.deepwither.core.engine;

/**
 * ライフサイクルイベントを持つコンポーネントのためのインターフェース。
 */
public interface Lifecycle {

    /**
 * Invoked when the component is enabled.
 *
 * Implementations should perform any initialization or activation needed when the component becomes enabled.
 */
    void onEnable();

    /**
 * Invoked when the component is disabled.
 *
 * Implementations should stop ongoing work and release or reset resources associated with the component.
 */
    void onDisable();
}