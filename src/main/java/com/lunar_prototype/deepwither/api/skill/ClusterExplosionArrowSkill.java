package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cluster Explosion Arrow (クラスター爆発矢) スキル
 * 7発の爆発矢を拡散させながら連続で放つ。
 */
public class ClusterExplosionArrowSkill implements ISkillLogic {

    private final Random random = new Random();
    private static final Set<ClusterArrowProjectile> activeArrows = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 7発連射タスク (10発から削減)
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 7 || !caster.isValid()) {
                    this.cancel();
                    return;
                }

                Location eyeLoc = caster.getEyeLocation();
                Vector direction = eyeLoc.getDirection();

                // 拡散
                double spreadH = Math.toRadians(60.0);
                double spreadV = Math.toRadians(30.0);
                
                double rotX = (random.nextDouble() - 0.5) * spreadV;
                double rotY = (random.nextDouble() - 0.5) * spreadH;
                
                direction = rotateVector(direction, rotX, rotY);

                // 発射
                ClusterArrowProjectile arrow = new ClusterArrowProjectile(caster, eyeLoc, direction);
                activeArrows.add(arrow);
                arrow.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

                count++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    private class ClusterArrowProjectile extends SkillProjectile {
        public ClusterArrowProjectile(LivingEntity caster, Location eyeLoc, Vector direction) {
            super(caster, eyeLoc.clone().add(direction.clone().multiply(1.0)), direction);
            this.speed = 2.5;
            this.hitboxRadius = 1.0;
            this.maxTicks = 40;
        }

        @Override
        public void onTick() {
            org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0, 81, 255), 0.8f);
            currentLocation.getWorld().spawnParticle(org.bukkit.Particle.DUST, currentLocation, 5, 0.05, 0.05, 0.05, 0.02, dust);
            this.direction.add(new Vector(0, -0.05, 0));
        }

        @Override
        public void cancel() {
            super.cancel();
            activeArrows.remove(this);
        }

        @Override
        public void onHitEntity(LivingEntity target) { explode(); }
        @Override
        public void onHitBlock(org.bukkit.block.Block block) { explode(); }

        private void explode() {
            // キャスターからの距離に応じてダメージを減衰させる (20ブロックターゲット)
            double distance = caster.getLocation().distance(currentLocation);
            
            // 15ブロックまでは線形に減衰し、それ以降は最大倍率
            double distMultiplier = Math.min(0.6, (distance / 15.0) * 0.6);
            
            // 最低保証 0.15倍 (近距離は極めて弱く)
            double finalMultiplier = Math.max(0.15, distMultiplier);

            com.lunar_prototype.deepwither.api.skill.utils.ExplosionUtil.triggerExplosionArrowEffect(caster, currentLocation, finalMultiplier);

            // 至近距離での全弾ヒット抑制ロジック (半径4.0ブロック以内)
            activeArrows.removeIf(other -> {
                if (other != this && other.currentLocation.getWorld().equals(currentLocation.getWorld()) 
                        && other.currentLocation.distanceSquared(currentLocation) <= 16.0) {
                    other.currentLocation.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, other.currentLocation, 3, 0.1, 0.1, 0.1, 0.05);
                    other.superCancel();
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

    private Vector rotateVector(Vector v, double angleX, double angleY) {
        // 簡易的なベクトル回転
        double cosX = Math.cos(angleX);
        double sinX = Math.sin(angleX);
        double cosY = Math.cos(angleY);
        double sinY = Math.sin(angleY);

        // Y軸回転 (水平)
        double x = v.getX() * cosY - v.getZ() * sinY;
        double z = v.getX() * sinY + v.getZ() * cosY;
        
        // X軸回転 (垂直)
        double y = v.getY() * cosX - z * sinX;
        z = v.getY() * sinX + z * cosX;

        return new Vector(x, y, z);
    }
}
