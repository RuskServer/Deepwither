package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Explosion Arrow (爆発矢) スキル
 * 直進する矢を放ち、着弾地点で広範囲の爆発とデバフを引き起こす。
 */
public class ExplosionArrowSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        Location spawnLoc = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(1.0));
        Vector direction = caster.getEyeLocation().getDirection();

        // 発射音
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);

        new SkillProjectile(caster, spawnLoc, direction) {
            {
                this.speed = 2.5; // MM v=50 相当
                this.hitboxRadius = 1.0;
                this.maxTicks = 40; // 約100ブロック
                // 重力は SkillProjectile のベース実装に依存するが、ここでは擬似的に重力を考慮した処理を onTick に書くことも可能
            }

            @Override
            public void onTick() {
                // 矢に追従するパーティクル（青色 #0051ff）
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 81, 255), 1.0f);
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 8, 0.1, 0.1, 0.1, 0.05, dust);
                
                // 矢の重力シミュレーション（簡易）
                this.direction.add(new Vector(0, -0.05, 0)); // gravity=1 相当の補正
            }

            @Override
            public void onHitEntity(LivingEntity target) {
                explode();
            }

            @Override
            public void onHitBlock(Block block) {
                explode();
            }

            private void explode() {
                com.lunar_prototype.deepwither.api.skill.utils.ExplosionUtil.triggerExplosionArrowEffect(caster, currentLocation);
                this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }
}
