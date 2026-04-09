package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

/**
 * 沈黙の監視者専用スキル: 高速熱光線
 * 単発・超高速・高威力 (80ダメージ)
 */
public class HeatRaySkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 発射音: 高音の鋭い音
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 2.0f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.6f, 2.0f);

        Location spawnLoc = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(1.0));
        
        new SkillProjectile(caster, spawnLoc, caster.getEyeLocation().getDirection()) {
            {
                this.speed = 2.5; // 超高速
                this.hitboxRadius = 0.5;
                this.maxTicks = 60; // 長距離射程
            }

            @Override
            public void onTick() {
                // 赤い細い線
                Particle.DustOptions line = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 0.8f);
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 3, 0.05, 0.05, 0.05, 0, line);
                
                // 飛翔中の微かな高音
                if (ticksLived % 3 == 0) {
                    currentLocation.getWorld().playSound(currentLocation, Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.3f, 2.0f);
                }
            }

            @Override
            public void onHitEntity(LivingEntity target) {
                double damage = 80.0;
                DamageContext ctx = new DamageContext(caster, target, DeepwitherDamageEvent.DamageType.MAGIC, damage);
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                // 着弾エフェクト
                target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0, 1, 0), 1);
                target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CONDUIT_ATTACK_TARGET, 1.0f, 2.0f);
                this.cancel();
            }

            @Override
            public void onHitBlock(Block block) {
                currentLocation.getWorld().playSound(currentLocation, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.5f);
                this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }
}
