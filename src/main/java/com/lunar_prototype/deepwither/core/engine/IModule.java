package com.lunar_prototype.deepwither.core.engine;

/**
 * Deepwitherの機能モジュールが実装すべきインターフェース。
 * 
 * <p>
 * すべての機能は原則としてモジュール単位で管理され、
 * このインターフェースを通じて構成(Configure)とライフサイクル(Start/Stop)が制御されます。
 * </p>
 */
public interface IModule {

    /**
     * モジュールの構成を行います。
     * このフェーズでは、自身のクラスや依存するコンポーネントを
     * {@link ServiceContainer} に登録します。
     * 
     * @param container DIコンテナ
     */
    void configure(ServiceContainer container);

    /**
     * モジュールの開始処理を行います。
     * コンテナによって依存関係が解決された後に呼び出されます。
     * リスナーの登録や初期化処理はここで行います。
     */
    void start();

    /**
     * モジュールの停止処理を行います。
     * プラグインの無効化時に呼び出されます。
     */
    void stop();
}
