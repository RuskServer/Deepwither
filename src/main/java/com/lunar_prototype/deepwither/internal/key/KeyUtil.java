package com.lunar_prototype.deepwither.internal.key;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class KeyUtil {
    /**
     * フォールバック用: 使われないと嬉しい
     */
    private static final NamespacedKey UNKNOWN = new NamespacedKey(JavaPlugin.getPlugin(Deepwither.class), "unknown");

    @NotNull
    public static NamespacedKey of(@NotNull String key) {
        return Optional.ofNullable(NamespacedKey.fromString(key.toLowerCase(), JavaPlugin.getPlugin(Deepwither.class))).orElse(UNKNOWN);
    }

    private KeyUtil() {}
}
