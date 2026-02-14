package com.lunar_prototype.deepwither;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class CustomPercentHeal implements ITargetedEntitySkill {

    protected final double healPercent;

    public CustomPercentHeal(MythicLineConfig config) {
        this.healPercent = config.getDouble(new String[] {"percent", "p"}, 5.0);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!(data.getCaster().getEntity().getBukkitEntity() instanceof Player player)) return SkillResult.ERROR;
        if (!(BukkitAdapter.adapt(target) instanceof LivingEntity bukkitTarget)) return SkillResult.INVALID_TARGET;
        if (bukkitTarget.isDead()) return SkillResult.INVALID_TARGET;

        double targetMaxHealth = bukkitTarget.getMaxHealth();
        double healAmount = targetMaxHealth * (this.healPercent / 100.0);

        if (healAmount <= 0) return SkillResult.SUCCESS;

        Deepwither.getInstance().getStatManager().heal(player,healAmount);

        player.sendMessage(Component.text("割合回復！ ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text(String.format("%.1f HPを回復しました。", healAmount), NamedTextColor.DARK_GREEN)));
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);

        return SkillResult.SUCCESS;
    }
}
