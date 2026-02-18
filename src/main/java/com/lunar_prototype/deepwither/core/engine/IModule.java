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
 * Configure the module by registering its services and required components into the provided service container.
 *
 * @param container the dependency-injection container used to register the module's own classes and any dependencies
 */
    void configure(ServiceContainer container);

    /**
 * Start the module and perform startup initialization.
 *
 * Called after the ServiceContainer has resolved the module's dependencies.
 * Register event listeners and perform any initialization required for the module here.
 */
    void start();

    /**
 * Performs module shutdown and cleanup.
 *
 * Called when the plugin is disabled; implementations should unregister listeners, stop background tasks, and release resources acquired during startup.
 */
    void stop();
}