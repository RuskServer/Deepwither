package com.lunar_prototype.deepwither;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LevelPlaceholderExpansion extends PlaceholderExpansion {

    private final LevelManager levelManager;
    private final ManaManager manaManager;

    public LevelPlaceholderExpansion(LevelManager levelManager,ManaManager manaManager) {
        this.levelManager = levelManager;
        this.manaManager = manaManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "deepwither"; // %deepwither_level% になる
    }

    @Override
    public @NotNull String getAuthor() {
        return "RuskLabo";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true; // Reloadしても有効
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        if (params.equalsIgnoreCase("level")) {
            PlayerLevelData data = levelManager.get(player);
            if (data != null) {
                return String.valueOf(data.getLevel());
            }
        }
        if (params.equalsIgnoreCase("mana")) {
            ManaData data = manaManager.get(player.getUniqueId());
            if (data != null) {
                return String.valueOf(data.getCurrentMana());
            }
        }
        if (params.equalsIgnoreCase("mana_max")) {
            ManaData data = manaManager.get(player.getUniqueId());
            if (data != null) {
                return String.valueOf(data.getMaxMana());
            }
        }
        return null;
    }
}