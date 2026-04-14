package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Scorching Slash (灼熱の斬撃) リワーク版
 * 自己中心型のAoE物理ダメージと炎上・スロウを付与する。
 */
public class ScorchingSlashSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 発動音・演出
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
        caster.getWorld().spawnParticle(Particle.FLAME, caster.getLocation().add(0, 1, 0), 50, 2.0, 1.0, 2.0, 0.1);

        // 即時ダメージ (物理: 8 * 0.5 = 4.0)
        double immediateDamage = 8.0 * 0.5;
        Collection<Entity> targets = caster.getWorld().getNearbyEntities(caster.getLocation(), 5.0, 5.0, 5.0);
        
        for (Entity e : targets) {
            if (e instanceof LivingEntity target && !e.equals(caster)) {
                // 1. 即時ダメージ
                DamageContext ctx = new DamageContext(caster, target, DeepwitherDamageEvent.DamageType.PHYSICAL, immediateDamage);
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                // 2. スロウ (3秒 = 60 tick)
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0)); // Level 1
                
                // 3. 着火 (5秒 = 100 tick)
                target.setFireTicks(100);
                
                target.getWorld().spawnParticle(Particle.WAX_OFF, target.getLocation().add(0, 1.5, 0), 10, 0.3, 0.5, 0.3, 0.1);
            }
        }

        return true;
    }
}
