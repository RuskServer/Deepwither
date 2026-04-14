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

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DarkStarSkill implements ISkillLogic {

    private static final Set<DarkStarProjectile> activeProjectiles = Collections.newSetFromMap(new ConcurrentHashMap<>());

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
            
            DarkStarProjectile projectile = new DarkStarProjectile(caster, caster.getEyeLocation().add(caster.getEyeLocation().getDirection()), spreadDir);
            activeProjectiles.add(projectile);
            projectile.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
        }
        return true;
    }

    private class DarkStarProjectile extends SkillProjectile {
        private int accelerationTicks = 0;
        private LivingEntity target = null;

        public DarkStarProjectile(LivingEntity caster, Location spawnLoc, Vector direction) {
            super(caster, spawnLoc, direction);
            this.speed = 0.5;
            this.hitboxRadius = 1.2;
            this.maxTicks = 120;
        }

        @Override
        public void onTick() {
            // 撃墜判定 (Shoot-down detection)
            
            // 1. バニラのプロジェクトタイル (矢など) を検知
            for (Entity e : currentLocation.getWorld().getNearbyEntities(currentLocation, 1.2, 1.2, 1.2)) {
                if (e instanceof org.bukkit.entity.Projectile proj) {
                    if (proj.getShooter() != null && !proj.getShooter().equals(caster)) {
                        explode(currentLocation);
                        proj.remove();
                        return;
                    }
                }
            }

            // 2. 他のスキルのカスタムプロジェクトタイル (アイスショットなど) を検知
            for (SkillProjectile other : SkillProjectile.getActiveProjectiles()) {
                if (other == this) continue;
                if (!other.getCurrentLocation().getWorld().equals(currentLocation.getWorld())) continue;
                
                // キャスターが自分以外かつ、距離が近い場合
                if (!other.getCaster().equals(caster) && other.getCurrentLocation().distanceSquared(currentLocation) <= 2.25) { // 半径1.5
                    explode(currentLocation);
                    other.cancel(); // 相手の弾も消す
                    return;
                }
            }

            // ターゲット検索
            if (target == null || !target.isValid() || target.isDead()) {
                target = getNearestTarget(currentLocation, 25.0, caster);
            }

            if (target != null) {
                Vector toTarget = target.getEyeLocation().toVector().subtract(currentLocation.toVector()).normalize();
                // 旋回性能を大幅に抑制し、慣性（横滑り）を表現 (0.25 -> 0.1)
                this.direction.add(toTarget.multiply(0.1)).normalize();
            }
            
            if (accelerationTicks < 30) {
                this.speed = Math.min(this.speed + 0.04, 1.8);
            } else {
                this.speed = Math.min(this.speed + 0.02, 2.2);
            }
            
            currentLocation.getWorld().spawnParticle(Particle.FLASH, currentLocation, 1, 0.1, 0.1, 0.1, 0, Color.WHITE);
            currentLocation.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, currentLocation, 3, 0.1, 0.1, 0.1, 0.05);
            
            accelerationTicks++;
        }

        @Override
        public void cancel() {
            super.cancel();
            activeProjectiles.remove(this);
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
                    DamageContext ctx = new DamageContext(caster, vic, DeepwitherDamageEvent.DamageType.MAGIC, 25.0);
                    Deepwither.getInstance().getDamageProcessor().process(ctx);
                    vic.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                });

            // 近距離での「ショットガン」ダメージ抑制: 着弾地点付近の他の弾を消滅させる
            activeProjectiles.removeIf(p -> {
                if (p != this && p.currentLocation.getWorld().equals(loc.getWorld()) && p.currentLocation.distanceSquared(loc) <= 25.0) { // 半径5ブロック
                    p.currentLocation.getWorld().spawnParticle(Particle.SMOKE, p.currentLocation, 5, 0.1, 0.1, 0.1, 0.05);
                    p.superCancel(); // 爆発を伴わずキャンセル
                    return true;
                }
                return false;
            });

            this.cancel();
        }

        private void superCancel() {
            super.cancel();
        }
    }

    private LivingEntity getNearestTarget(Location loc, double range, LivingEntity caster) {
        return loc.getWorld().getNearbyEntities(loc, range, range, range).stream()
                .filter(e -> e instanceof LivingEntity && !e.isDead() && !e.equals(caster))
                .filter(e -> e instanceof Monster || e instanceof org.bukkit.entity.Player)
                .map(e -> (LivingEntity) e)
                .min(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(loc)))
                .orElse(null);
    }
}
