package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;

/**
 * Holy Initiation (聖なる導き)
 * 周囲を神聖な光で照らし、範囲内の敵に移動速度低下を与える。
 */
public class HolyInitiationSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 発動音と初期演出
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        caster.getWorld().spawnParticle(Particle.FLASH, caster.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0, Color.WHITE);

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 60; // 3秒間維持

            @Override
            public void run() {
                if (ticks >= maxTicks || !caster.isValid()) {
                    this.cancel();
                    return;
                }

                // 範囲8ブロック以内の敵を対象
                Collection<Entity> nearby = caster.getWorld().getNearbyEntities(caster.getLocation(), 8.0, 8.0, 8.0);
                for (Entity entity : nearby) {
                    if (entity instanceof LivingEntity target && !entity.equals(caster)) {
                        // 敵対判定（LivingEntityであれば敵とみなす、簡易実装）
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1)); // Slow II
                        
                        // エフェクト
                        target.getWorld().spawnParticle(Particle.GLOW, target.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
                    }
                }

                // 神聖な光のリング演出
                double radius = 8.0;
                for (int i = 0; i < 20; i++) {
                    double angle = (Math.PI * 2 / 20) * i;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    caster.getWorld().spawnParticle(Particle.DUST, caster.getLocation().clone().add(x, 0.1, z), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.YELLOW, 1.5f));
                }

                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }
}
