package com.lunar_prototype.deepwither.dynamic_quest.enums;

import com.lunar_prototype.deepwither.dynamic_quest.obj.DynamicQuest;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public enum QuestType {
    FETCH("調達", Material.BUNDLE, QuestDifficulty.LOW),
    DELIVERY("配達", Material.CHEST_MINECART, QuestDifficulty.LOW),
    SCOUT("調査", Material.SPYGLASS, QuestDifficulty.MEDIUM),
    RAID("襲撃", Material.TNT, QuestDifficulty.HIGH),
    ELIMINATE("排除", Material.NETHERITE_SWORD, QuestDifficulty.HIGH);

    private final String display;
    private final Material icon;
    private final QuestDifficulty defaultDifficulty;

    QuestType(String display, Material icon, QuestDifficulty defaultDifficulty) {
        this.display = display;
        this.icon = icon;
        this.defaultDifficulty = defaultDifficulty;
    }

    public String getDisplay() {
        return display;
    }

    public Material getIcon() {
        return icon;
    }

    public QuestDifficulty getDefaultDifficulty() {
        return defaultDifficulty;
    }

    public ItemStack getIconItem() {
        return new ItemStack(icon);
    }
}
