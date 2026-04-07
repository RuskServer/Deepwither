package com.lunar_prototype.deepwither.modules.mine;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

public class MiningSkillService {

    private final Deepwither plugin;

    public MiningSkillService(Deepwither plugin) {
        this.plugin = plugin;
    }

    public MiningProfile resolveProfile(Player player) {
        int level = resolveLevel(player);
        return new MiningProfile(
                level,
                resolveBaseDamage(level),
                resolveCriticalChance(level),
                resolveCriticalBonus(level),
                resolveRareDropLuck(level),
                resolveGeologicalChance(level),
                resolveGeologicalRadius(level),
                resolveGeologicalLimit(level)
        );
    }

    public MiningStrike resolveStrike(MiningProfile profile) {
        boolean critical = plugin.getRandom().nextDouble() < profile.criticalChance();
        int damage = profile.baseDamage() + (critical ? profile.criticalBonus() : 0);
        return new MiningStrike(Math.max(1, damage), critical);
    }

    public double adjustDropChance(MiningProfile profile, double baseChance) {
        double chance = Math.max(0.0D, Math.min(1.0D, baseChance));
        if (chance >= 1.0D) {
            return 1.0D;
        }

        double luck = profile.rareDropLuck();
        double boosted = chance + ((1.0D - chance) * (1.0D - chance) * luck);
        return Math.max(chance, Math.min(1.0D, boosted));
    }

    public GeologicalBurst resolveGeologicalBurst(MiningProfile profile) {
        boolean triggered = plugin.getRandom().nextDouble() < profile.geologicalChance();
        return new GeologicalBurst(triggered, profile.geologicalRadius(), profile.geologicalLimit());
    }

    public Component buildStatusTooltip(MiningProfile profile) {
        Component tooltip = Component.text("採掘スキル", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("Lv." + profile.level(), NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("急所突き", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(": 発動率 " + formatPercent(profile.criticalChance())
                        + " / 耐久 " + profile.baseDamage() + "〜" + (profile.baseDamage() + profile.criticalBonus())
                        + " 減少", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("解体技術", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(": 低確率ドロップ補正 " + formatPercent(profile.rareDropLuck())
                        + " / レア枠ほど出やすい", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("地殻破壊", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(": 発動率 " + formatPercent(profile.geologicalChance())
                        + " / 半径 " + profile.geologicalRadius()
                        + " / 最大 " + profile.geologicalLimit() + " 個を連鎖破壊", NamedTextColor.GRAY));
        return tooltip;
    }

    private int resolveLevel(Player player) {
        ProfessionManager professionManager = plugin.getProfessionManager();
        if (professionManager == null || player == null) {
            return 1;
        }
        return professionManager.getLevel(professionManager.getData(player).getExp(ProfessionType.MINING));
    }

    private int resolveBaseDamage(int level) {
        if (level >= 90) {
            return 4;
        }
        if (level >= 60) {
            return 3;
        }
        if (level >= 30) {
            return 2;
        }
        return 1;
    }

    private double resolveCriticalChance(int level) {
        return Math.min(0.30D, 0.08D + (level * 0.0022D));
    }

    private int resolveCriticalBonus(int level) {
        return Math.min(2, 1 + (level / 60));
    }

    private double resolveRareDropLuck(int level) {
        return Math.min(0.30D, 0.10D + (level * 0.0020D));
    }

    private double resolveGeologicalChance(int level) {
        return Math.min(0.18D, 0.02D + (level * 0.0015D));
    }

    private int resolveGeologicalRadius(int level) {
        return Math.min(2, 1 + (level / 60));
    }

    private int resolveGeologicalLimit(int level) {
        return Math.min(6, 2 + (level / 25));
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value * 100.0D);
    }

    public record MiningProfile(int level, int baseDamage, double criticalChance, int criticalBonus,
                                double rareDropLuck, double geologicalChance, int geologicalRadius,
                                int geologicalLimit) {
    }

    public record MiningStrike(int damage, boolean critical) {
    }

    public record GeologicalBurst(boolean triggered, int radius, int maxBlocks) {
    }
}
