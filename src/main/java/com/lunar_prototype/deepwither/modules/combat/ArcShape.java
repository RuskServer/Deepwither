package com.lunar_prototype.deepwither.modules.combat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

public class ArcShape implements HitShape {
    private final double angleDegrees;
    private final double thickness;

    public ArcShape(double angleDegrees, double thickness) {
        this.angleDegrees = angleDegrees;
        this.thickness = thickness;
    }

    @Override
    public boolean isHit(Location origin, Vector direction, Entity target, double reach) {
        Location targetLoc = target.getLocation().add(0, target.getHeight() / 2, 0);
        Vector toTarget = targetLoc.toVector().subtract(origin.toVector());
        double distance = toTarget.length();

        if (distance > reach) return false;
        if (Math.abs(toTarget.getY()) > thickness / 2 + target.getHeight() / 2) return false;

        // 水平方向の角度チェック
        Vector dirH = direction.clone().setY(0).normalize();
        Vector toTargetH = toTarget.clone().setY(0).normalize();
        
        if (dirH.lengthSquared() == 0 || toTargetH.lengthSquared() == 0) {
            // 真上・真下を向いている場合は、水平方向の角度チェックをスキップするか、特殊処理が必要
            // 簡易的に、非常に近い場合はヒットとする
            return distance < 1.0;
        }

        double dot = dirH.dot(toTargetH);
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));

        return angle <= angleDegrees / 2;
    }

    @Override
    public double getMaxReach(double baseReach) {
        return baseReach;
    }

    @Override
    public void drawDebug(Location origin, Vector direction, double reach) {
        double halfAngle = angleDegrees / 2.0;
        Vector dirH = direction.clone().setY(0).normalize();
        
        // 扇形の外縁と骨組みを描画
        for (double d = 0.5; d <= reach; d += 0.5) {
            for (double a = -halfAngle; a <= halfAngle; a += 10.0) {
                double rad = Math.toRadians(a);
                double x = dirH.getX() * Math.cos(rad) - dirH.getZ() * Math.sin(rad);
                double z = dirH.getX() * Math.sin(rad) + dirH.getZ() * Math.cos(rad);
                
                Location p = origin.clone().add(new Vector(x, 0, z).multiply(d));
                origin.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, p, 1, 0, 0, 0, 0);
            }
        }
    }
}
