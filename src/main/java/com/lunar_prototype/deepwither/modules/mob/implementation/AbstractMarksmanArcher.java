package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMob;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * 遠距離射撃を行うカスタムモブの基底クラス。
 */
public abstract class AbstractMarksmanArcher extends CustomMob {

    protected final Random random = new Random();
    protected int multiShotCooldown = 0;

    @Override
    public void onAttack(LivingEntity victim, DeepwitherDamageEvent event) {
        // 遠距離攻撃のダメージ強化 (サブクラスで個別に調整可能)
        if (event.getType() == DeepwitherDamageEvent.DamageType.PROJECTILE) {
            double baseDamage = getCustomArrowDamage();
            event.setDamage(baseDamage);
            
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 5, 0.1, 0.1, 0.1, 0.2);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.2f);
        }
    }

    /**
     * 基本となる矢のダメージを返します。
     */
    protected abstract double getCustomArrowDamage();

    /**
     * 扇状に矢を放つマルチショットを実行します。
     */
    protected void performMultiShot(LivingEntity target, int arrowCount, double spreadAngle, double velocity) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1.0f, 0.8f);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.5f);
        
        Vector direction = target.getLocation().toVector().subtract(entity.getEyeLocation().toVector()).normalize();
        
        double startAngle = -spreadAngle / 2.0;
        double angleStep = spreadAngle / (arrowCount - 1);

        for (int i = 0; i < arrowCount; i++) {
            double currentAngle = Math.toRadians(startAngle + (i * angleStep));
            Vector shotDir = rotateVector(direction.clone(), currentAngle);
            
            Arrow arrow = entity.launchProjectile(Arrow.class, shotDir.multiply(velocity));
            arrow.setShooter(entity);
            arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
            
            entity.getWorld().spawnParticle(Particle.FIREWORK, entity.getEyeLocation().add(shotDir.multiply(1.0)), 1, 0, 0, 0, 0.05);
        }
    }

    protected Vector rotateVector(Vector v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }

    protected LivingEntity getNearestTarget(double range) {
        return entity.getWorld().getNearbyEntities(entity.getLocation(), range, range, range).stream()
                .filter(e -> e instanceof Player p && p.getGameMode() != org.bukkit.GameMode.SPECTATOR)
                .map(e -> (LivingEntity) e)
                .min((e1, e2) -> Double.compare(entity.getLocation().distanceSquared(e1.getLocation()), 
                                             entity.getLocation().distanceSquared(e2.getLocation())))
                .orElse(null);
    }
}
