package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Collection;

public class DarkStarSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 0.5f);
        
        // 4発同時に射出
        for (int i = 0; i < 4; i++) {
            // 水平方向に広がるように発射
            double angleOffset = (i - 1.5) * Math.toRadians(15.0); // 15度ずつ拡散
            Vector dir = caster.getEyeLocation().getDirection().clone();
            // Y軸周りに回転させて拡散
            double cos = Math.cos(angleOffset);
            double sin = Math.sin(angleOffset);
            double x = dir.getX() * cos - dir.getZ() * sin;
            double z = dir.getX() * sin + dir.getZ() * cos;
            Vector spreadDir = new Vector(x, dir.getY(), z);
            
            launchDarkStar(caster, caster.getEyeLocation().add(caster.getEyeLocation().getDirection()), spreadDir);
        }
        return true;
    }

    private void launchDarkStar(LivingEntity caster, Location spawnLoc, Vector direction) {
        LivingEntity target = getNearestTarget(caster);
        
        new SkillProjectile(caster, spawnLoc, direction) {
            private int accelerationTicks = 0;

            {
                this.speed = 0.5;
                this.hitboxRadius = 1.0;
                this.maxTicks = 100;
            }

            @Override
            public void onTick() {
                if (target != null && target.isValid()) {
                    Vector toTarget = target.getLocation().add(0, 0.5, 0).toVector().subtract(currentLocation.toVector()).normalize();
                    this.direction.add(toTarget.multiply(0.08)).normalize();
                }
                
                if (accelerationTicks < 20) {
                    this.speed += 0.05;
                } else {
                    this.speed = Math.min(this.speed + 0.1, 2.5);
                }
                
                // パーティクル
                currentLocation.getWorld().spawnParticle(Particle.FLASH, currentLocation, 1, 0.1, 0.1, 0.1, 0, Color.WHITE);
                currentLocation.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, currentLocation, 3, 0.1, 0.1, 0.1, 0.05);
                
                accelerationTicks++;
            }

            @Override
            public void onHitEntity(LivingEntity hit) { explode(currentLocation); }
            @Override
            public void onHitBlock(org.bukkit.block.Block block) { explode(currentLocation); }

            private void explode(Location loc) {
                loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3);
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.2f);
                
                loc.getWorld().getNearbyEntities(loc, 3.0, 3.0, 3.0).stream()
                    .filter(e -> e instanceof LivingEntity && !e.equals(caster))
                    .map(e -> (LivingEntity) e)
                    .forEach(vic -> {
                        DamageContext ctx = new DamageContext(caster, vic, DeepwitherDamageEvent.DamageType.MAGIC, 40.0);
                        Deepwither.getInstance().getDamageProcessor().process(ctx);
                        vic.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                    });
                this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }

    private LivingEntity getNearestTarget(LivingEntity caster) {
        return caster.getWorld().getNearbyEntities(caster.getLocation(), 20, 20, 20).stream()
                .filter(e -> e instanceof Monster && !e.equals(caster))
                .map(e -> (LivingEntity) e)
                .findFirst().orElse(null);
    }
}
