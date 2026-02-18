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

    /**
     * Create a new ServiceContainer and register the container instance for dependency resolution.
     *
     * @param logger the Logger used to report warnings and informational messages
     */
    public ServiceContainer(Logger logger) {
        this.logger = logger;
        // 自分自身を登録
        registerInstance(ServiceContainer.class, this);
    }

    /**
     * Checks whether an instance for the given class is available in the container.
     *
     * @param clazz the service or implementation class to check for
     * @return `true` if an instantiated or explicitly registered instance exists for the class, `false` otherwise
     */
    public boolean has(Class<?> clazz) {
        return instances.containsKey(clazz) || registeredInstances.containsKey(clazz);
    }

    /**
     * Registers a pre-existing instance for the specified class in the container.
     *
     * After registration, subsequent requests for the class will return the provided instance.
     * If an existing registration or instance is present, a warning is logged and the registration is overwritten.
     *
     * @param clazz    the class key under which to register the instance
     * @param instance the instance to register and return for future lookups
     */
    public <T> void registerInstance(Class<T> clazz, T instance) {
        if (instances.containsKey(clazz) || registeredInstances.containsKey(clazz)) {
            logger.warning("Overwriting existing registration for: " + clazz.getName());
        }
        registeredInstances.put(clazz, instance);
        instances.put(clazz, instance);
    }

    /**
     * Retrieve or create the singleton instance for the specified class.
     *
     * @param clazz the class whose instance is requested
     * @return the singleton instance for the specified class
     * @throws RuntimeException if instantiation fails or a circular dependency is detected
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
     * Instantiate the given class and resolve its constructor dependencies.
     *
     * @param clazz the class to instantiate
     * @return the created instance of the requested class
     * @throws IllegalStateException   if a circular dependency involving the class is detected
     * @throws IllegalArgumentException if the class has no public constructor and cannot be instantiated
     * @throws RuntimeException        if instantiation or dependency resolution fails for any other reason
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
     * Clears the container's state, removing all registered and instantiated services and resetting creation tracking.
     *
     * After this call the container will have no registered instances, no stored created instances, and no classes marked as currently being created.
     */
    public void clear() {
        instances.clear();
        registeredInstances.clear();
        currentlyCreating.clear();
    }
}