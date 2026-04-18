package com.lunar_prototype.deepwither.core;

import com.lunar_prototype.deepwither.api.playerdata.IPlayerComponent;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーごとのデータを一括管理するキャッシュコンテナ。
 */
public class PlayerCache {
    private final UUID uuid;
    private final Map<Class<? extends IPlayerComponent>, IPlayerComponent> components = new ConcurrentHashMap<>();

    public PlayerCache(UUID uuid) {
        this.uuid = uuid;
    }

    /**
     * 指定されたクラスのコンポーネントを取得します。
     * 存在しない場合はnullを返します。
     */
    public <T extends IPlayerComponent> T get(Class<T> clazz) {
        IPlayerComponent component = components.get(clazz);
        return component != null ? clazz.cast(component) : null;
    }
    
    /**
     * 指定されたクラスのコンポーネントを取得します。
     * 存在しない場合は supplier を使って新しく生成し、格納してから返します。
     */
    public <T extends IPlayerComponent> T getOrPut(Class<T> clazz, java.util.function.Supplier<T> supplier) {
        return clazz.cast(components.computeIfAbsent(clazz, k -> supplier.get()));
    }

    /**
     * 指定されたクラスのコンポーネントをキャッシュにセットします。
     */
    public <T extends IPlayerComponent> void set(Class<T> clazz, T data) {
        if (data == null) {
            components.remove(clazz);
        } else {
            components.put(clazz, data);
        }
    }

    /**
     * 指定されたクラスのコンポーネントをキャッシュから削除します。
     */
    public void remove(Class<? extends IPlayerComponent> clazz) {
        components.remove(clazz);
    }

    /**
     * キャッシュに登録されている全コンポーネントのコレクションを返します（デバッグ用途等）。
     */
    public Collection<IPlayerComponent> getAllComponents() {
        return components.values();
    }

    public UUID getUuid() {
        return uuid;
    }
}
