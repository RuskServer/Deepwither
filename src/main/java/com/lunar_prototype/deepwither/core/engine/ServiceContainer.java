package com.lunar_prototype.deepwither.core.engine;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.logging.Logger;

/**
 * 軽量なDependency Injection (DI) コンテナ。
 * 
 * <p>
 * 特徴:
 * </p>
 * <ul>
 * <li>コンストラクタインジェクションのみをサポート (Field Injection禁止)</li>
 * <li>シングルトンインスタンス管理</li>
 * <li>循環依存検出</li>
 * <li>明示的な登録と自動解決のハイブリッド</li>
 * </ul>
 */
public class ServiceContainer {

    private final Map<Class<?>, Object> instances = new HashMap<>();
    private final Map<Class<?>, Object> registeredInstances = new HashMap<>();
    private final Set<Class<?>> currentlyCreating = new HashSet<>();
    private final Logger logger;

    public ServiceContainer(Logger logger) {
        this.logger = logger;
        // 自分自身を登録
        registerInstance(ServiceContainer.class, this);
    }

    /**
     * 既存のインスタンスをコンテナに登録します。
     * 
     * @param clazz    登録するクラスの型
     * @param instance 登録するインスタンス
     */
    public <T> void registerInstance(Class<T> clazz, T instance) {
        if (instances.containsKey(clazz) || registeredInstances.containsKey(clazz)) {
            logger.warning("Overwriting existing registration for: " + clazz.getName());
        }
        registeredInstances.put(clazz, instance);
        instances.put(clazz, instance);
    }

    /**
     * 指定されたクラスのインスタンスを取得します。
     * まだ生成されていない場合は、依存関係を解決して生成します。
     * 
     * @param clazz 取得したいクラス
     * @return インスタンス
     * @throws RuntimeException インスタンス化に失敗した場合、または循環依存が検出された場合
     */
    public <T> T get(Class<T> clazz) {
        if (instances.containsKey(clazz)) {
            return clazz.cast(instances.get(clazz));
        }

        // 登録済みインスタンスがあればそれを返す (registerInstanceで登録されたもの)
        if (registeredInstances.containsKey(clazz)) {
            return clazz.cast(registeredInstances.get(clazz));
        }

        return createInstance(clazz);
    }

    /**
     * インスタンスを生成し、依存関係を解決します。
     */
    private <T> T createInstance(Class<T> clazz) {
        if (currentlyCreating.contains(clazz)) {
            throw new IllegalStateException("Circular dependency detected: " + clazz.getName());
        }

        currentlyCreating.add(clazz);

        try {
            // コンストラクタの選択
            // 引数が最も多いコンストラクタ、あるいは @Inject (今回はアノテーションレスの方針だが) があるものを優先などの戦略があるが、
            // ここではシンプルに「publicコンストラクタは1つだけ」または「引数なし」を前提とするか、
            // "最も引数が多いもの" を選択する戦略をとる。
            Constructor<?>[] constructors = clazz.getConstructors();
            if (constructors.length == 0) {
                // publicコンストラクタがない場合、インスタンス化できない(あってもprivateな場合など)
                // ただし、static factoryメソッドなどは今回は考慮しない
                throw new IllegalArgumentException("No public constructor found for: " + clazz.getName());
            }

            // シンプルな戦略: 最初のコンストラクタを使う。
            // 改善案: 最も引数の多いコンストラクタを探す
            Constructor<?> targetConstructor = constructors[0];
            for (Constructor<?> c : constructors) {
                if (c.getParameterCount() > targetConstructor.getParameterCount()) {
                    targetConstructor = c;
                }
            }

            Class<?>[] parameterTypes = targetConstructor.getParameterTypes();
            Object[] parameters = new Object[parameterTypes.length];

            for (int i = 0; i < parameterTypes.length; i++) {
                // 再帰的に依存解決
                parameters[i] = get(parameterTypes[i]);
            }

            T instance = clazz.cast(targetConstructor.newInstance(parameters));
            instances.put(clazz, instance);
            return instance;

        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate: " + clazz.getName(), e);
        } finally {
            currentlyCreating.remove(clazz);
        }
    }

    /**
     * コンテナ内の全インスタンスをクリアします。
     */
    public void clear() {
        instances.clear();
        registeredInstances.clear();
        currentlyCreating.clear();
    }
}
