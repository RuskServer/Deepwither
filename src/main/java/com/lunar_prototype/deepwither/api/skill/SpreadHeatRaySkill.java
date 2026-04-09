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
import org.bukkit.util.Vector;

/**
 * 沈黙の監視者専用スキル: 拡散高速熱光線
 * 5方向拡散・ノックバック・魔法ダメージ (25ダメージ)
 */
public class SpreadHeatRaySkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.5f);
        
        Vector baseDir = caster.getEyeLocation().getDirection();
        
        // 5方向へ拡散
        for (int i = -2; i <= 2; i++) {
            double angle = Math.toRadians(i * 15); // 15度ずつ
            Vector dir = rotateY(baseDir.clone(), angle);
            
            new SkillProjectile(caster, caster.getEyeLocation(), dir) {
                {
                    this.speed = 1.8;
                    this.hitboxRadius = 0.8;
                    this.maxTicks = 15; // 近〜中距離用
                }

                @Override
                public void onTick() {
                    // オレンジの細い線
                    Particle.DustOptions line = new Particle.DustOptions(Color.fromRGB(255, 120, 0), 1.2f);
                    currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 2, 0.1, 0.1, 0.1, 0, line);
                }

                @Override
                public void onHitEntity(LivingEntity target) {
                    double damage = 25.0;
                    DamageContext ctx = new DamageContext(caster, target, DeepwitherDamageEvent.DamageType.MAGIC, damage);
                    Deepwither.getInstance().getDamageProcessor().process(ctx);
                    
                    // 強めのノックバック
                    Vector knockback = target.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize().multiply(1.2);
                    knockback.setY(0.3); // 少し浮かせる
                    target.setVelocity(target.getVelocity().add(knockback));
                    
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR, 1.0f, 2.0f);
                    this.cancel();
                }

                @Override
                public void onHitBlock(Block block) { this.cancel(); }
            }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
        }
        return true;
    }

    private Vector rotateY(Vector v, double angle) {
        double x = v.getX() * Math.cos(angle) - v.getZ() * Math.sin(angle);
        double z = v.getX() * Math.sin(angle) + v.getZ() * Math.cos(angle);
        return v.setX(x).setZ(z);
    }
}
