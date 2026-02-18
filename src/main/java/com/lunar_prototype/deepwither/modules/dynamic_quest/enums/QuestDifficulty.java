package com.lunar_prototype.deepwither.modules.dynamic_quest.enums;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum QuestDifficulty {
    LOW("低", NamedTextColor.GREEN, 1.0),
    MEDIUM("中", NamedTextColor.YELLOW, 1.5),
    HIGH("高", NamedTextColor.RED, 2.5),
    EXTREME("最高", NamedTextColor.DARK_RED, 5.0);

    private final String display;
    private final NamedTextColor color;
    private final double rewardMultiplier;

    QuestDifficulty(String display, NamedTextColor color, double rewardMultiplier) {
        this.display = display;
        this.color = color;
        this.rewardMultiplier = rewardMultiplier;
    }

    public String getDisplay() {
        return display;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public double getRewardMultiplier() {
        return rewardMultiplier;
    }

    public Component getDisplayName() {
        return Component.text(display, color);
    }
}
