package com.lunar_prototype.deepwither.constants;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

public final class Keys {
    public static final NamespacedKey PVP_BACK_WORLD = key("pvp_back_world");
    public static final NamespacedKey PVP_BACK_LOCATION = key("pvp_back_location");

    private static NamespacedKey key(@NotNull String key) {
        return NamespacedKey.fromString(key.toLowerCase(), Deepwither.getInstance());
    }
}
