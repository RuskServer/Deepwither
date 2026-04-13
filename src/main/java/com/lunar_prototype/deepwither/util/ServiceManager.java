package com.lunar_prototype.deepwither.util;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;

import java.util.*;

/**
 * @deprecated ライフサイクル管理は ServiceContainer に統合されました。
 * このクラスは互換性のための薄いラッパーです。
 */
@Deprecated
public class ServiceManager {

    private final Deepwither plugin;
    private final ServiceContainer container;

    public ServiceManager(Deepwither plugin, ServiceContainer container) {
        this.plugin = plugin;
        this.container = container;
    }

    /**
     * 新しいマネージャーを登録します。
     */
    public <T extends IManager> void register(T service) {
        container.registerInstance((Class) service.getClass(), service);
    }

    /**
     * インスタンスを直接登録します。
     */
    public <T> void registerInstance(Class<T> clazz, T instance) {
        container.registerInstance(clazz, instance);
    }

    /**
     * 登録済みのサービスを取得します。
     */
    public <T> T get(Class<T> clazz) {
        return container.get(clazz);
    }

    /**
     * 全てのサービスを初期化します。ServiceContainer に処理を委譲します。
     */
    public void startAll() throws Exception {
        container.initializeAll();
    }

    /**
     * 全てのサービスを停止します。ServiceContainer に処理を委譲します。
     */
    public void stopAll() {
        container.shutdownAll();
    }
}
