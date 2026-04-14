package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;

/**
 * Greater Heal (大治癒)
 * 最大体力の20%を回復し、自身についているデバフを全て除去する。
 */
public class GreaterHealSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 発動音・演出
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.0f);
        caster.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, caster.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

        // 1. HP回復 (最大体力の20%)
        if (caster instanceof org.bukkit.entity.Player player) {
            com.lunar_prototype.deepwither.api.stat.IStatManager statManager = Deepwither.getInstance().getStatManager();
            double maxHealth = statManager.getActualMaxHealth(player);
            double healAmount = maxHealth * 0.2;
            statManager.heal(player, healAmount);
        } else {
            double maxHealth = caster.getAttribute(Attribute.MAX_HEALTH).getValue();
            double healAmount = maxHealth * 0.2;
            double newHealth = Math.min(maxHealth, caster.getHealth() + healAmount);
            caster.setHealth(newHealth);
        }

        // 2. デバフ除去 (有害なポーション効果を削除)
        java.util.Set<PotionEffectType> harmfulEffects = new java.util.HashSet<>();
        harmfulEffects.add(PotionEffectType.BLINDNESS);
        harmfulEffects.add(PotionEffectType.HUNGER);
        harmfulEffects.add(PotionEffectType.POISON);
        harmfulEffects.add(PotionEffectType.WITHER);
        harmfulEffects.add(PotionEffectType.SLOWNESS);
        harmfulEffects.add(PotionEffectType.MINING_FATIGUE);
        harmfulEffects.add(PotionEffectType.WEAKNESS);
        harmfulEffects.add(PotionEffectType.LEVITATION);
        harmfulEffects.add(PotionEffectType.UNLUCK);
        harmfulEffects.add(PotionEffectType.BAD_OMEN);

        for (PotionEffectType type : harmfulEffects) {
            caster.removePotionEffect(type);
        }

        return true;
    }
}
