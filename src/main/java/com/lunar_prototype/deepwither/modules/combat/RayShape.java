package com.lunar_prototype.deepwither.modules.combat;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

public class RayShape implements HitShape {
    private final double radius;

    public RayShape(double radius) {
        this.radius = radius;
    }

    @Override
    public boolean isHit(Location origin, Vector direction, Entity target, double reach) {
        Vector a = origin.toVector();
        Vector d = direction.clone().normalize();
        Vector p = target.getLocation().add(0, target.getHeight() / 2, 0).toVector();

        // 点Pから直線(a + td)への最短距離を求める
        Vector ap = p.subtract(a);
        double t = ap.dot(d);

        if (t < 0 || t > reach) return false;

        Vector closestPoint = a.add(d.multiply(t));
        double distSquared = closestPoint.distanceSquared(target.getLocation().add(0, target.getHeight() / 2, 0).toVector());

        // エンティティの当たり判定（幅）も考慮
        double targetRadius = target.getWidth() / 2 + radius;
        return distSquared <= targetRadius * targetRadius;
    }

    @Override
    public double getMaxReach(double baseReach) {
        return baseReach;
    }

    @Override
    public void drawDebug(Location origin, Vector direction, double reach) {
        Vector d = direction.clone().normalize();
        for (double i = 0.5; i <= reach; i += 0.3) {
            Location p = origin.clone().add(d.clone().multiply(i));
            // 半径に合わせて少し散らす
            origin.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, p, 1, radius, radius, radius, 0);
        }
    }

    @Override
    public void spawnSlashEffect(Location origin, Vector direction, double reach) {
        Vector d = direction.clone().normalize();
        // 密度を高めて鋭い突きを表現
        for (double i = 0.5; i <= reach; i += 0.2) {
            Location p = origin.clone().add(d.clone().multiply(i));
            origin.getWorld().spawnParticle(org.bukkit.Particle.CRIT, p, 1, 0.01, 0.01, 0.01, 0);
            
            if (i > reach * 0.8) { // 先端に少し輝き
                origin.getWorld().spawnParticle(Particle.FIREWORK, p, 1, 0, 0, 0, 0);
            }
        }
    }
}
