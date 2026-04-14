package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Four Consecutive Attacks (4連撃) スキル
 * 発動後、次に攻撃がヒットした際に4連続の追撃ダメージを与えるオーラを付与する。
 */
public class FourConsecutiveAttacksSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 10秒間(200tick)持続するオーラを付与
        Deepwither.getInstance().getAuraManager().addAura(caster, "four_consecutive_attacks", 200, null);
        return true;
    }

    /**
     * DamageManager等から呼び出されるヒット時の処理。
     */
    public static void triggerHit(LivingEntity caster, LivingEntity target) {
        Deepwither.getInstance().getAuraManager().removeAura(caster, "four_consecutive_attacks");

        new BukkitRunnable() {
            int hits = 0;
            @Override
            public void run() {
                if (hits >= 4 || !caster.isValid() || !target.isValid()) {
                    this.cancel();
                    return;
                }

                // 音とパーティクル
                caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_LANTERN_BREAK, 1.0f, 1.0f);
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.5, 0), 24, 0.5, 0.5, 0.5, 0.2);
                target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1.5, 0), 1);

                // ダメージ (a=5, m=1.0)
                DamageContext ctx = new DamageContext(caster, target, DeepwitherDamageEvent.DamageType.PHYSICAL, 5.0);
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                hits++;
                if (hits >= 4) this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 10L); // 10 tick間隔
    }
}
