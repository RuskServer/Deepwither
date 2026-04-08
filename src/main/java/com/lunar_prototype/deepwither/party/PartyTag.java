package com.lunar_prototype.deepwither.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum PartyTag {
    BEGINNER("初心者歓迎", Material.APPLE, NamedTextColor.GREEN),
    FARM("ファーム", Material.WHEAT, NamedTextColor.YELLOW),
    PVP("PvP", Material.DIAMOND_SWORD, NamedTextColor.RED),
    PVE("PvE", Material.IRON_CHESTPLATE, NamedTextColor.AQUA),
    LEVEL_RANGE("レベル帯指定", Material.EXPERIENCE_BOTTLE, NamedTextColor.LIGHT_PURPLE),
    EFFICIENCY("効率ガチ勢", Material.GOLDEN_PICKAXE, NamedTextColor.GOLD),
    JP_ONLY("JP Only", Material.NAME_TAG, NamedTextColor.WHITE),
    DISCORD("VC/Discord連携", Material.JUKEBOX, NamedTextColor.DARK_AQUA);

    private final String displayName;
    private final Material icon;
    private final NamedTextColor color;

    PartyTag(String displayName, Material icon, NamedTextColor color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public Component getComponent() {
        return Component.text("[" + displayName + "]", color);
    }
}
