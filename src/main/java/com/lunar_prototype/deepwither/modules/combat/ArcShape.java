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

    @Override
    public void spawnSlashEffect(Location origin, Vector direction, double reach, HitDetectionManager.VisualType style) {
        if (style == HitDetectionManager.VisualType.HEAVY) {
            spawnHeavyEffect(origin, direction, reach);
            return;
        }

        double halfAngle = angleDegrees / 2.0;
        Vector dir = direction.clone().normalize();
        
        // 回転軸の計算（プレイヤーの視線に対する「上」方向を軸にする）
        Vector right = new Vector(0, 1, 0).crossProduct(dir);
        if (right.lengthSquared() < 0.001) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = dir.clone().crossProduct(right).normalize();

        // 密度を高めて、より「なぎ払い」らしく見せる
        for (double a = -halfAngle; a <= halfAngle; a += 10.0) {
            double rad = Math.toRadians(a);
            
            // 視線方向を「上」軸で回転させることで、水平ななぎ払いを作る（ピッチに合わせて傾く）
            Vector v = dir.clone();
            v.rotateAroundAxis(up, rad);

            Location p = origin.clone().add(v.multiply(reach * 0.8));
            origin.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, p, 1, 0.05, 0.05, 0.05, 0);
            
            // 軌跡を強調するためのサブパーティクル
            if (style == HitDetectionManager.VisualType.SCYTHE) {
                origin.getWorld().spawnParticle(org.bukkit.Particle.SQUID_INK, p, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }
    }

    private void spawnHeavyEffect(Location origin, Vector direction, double reach) {
        Vector dir = direction.clone().normalize();
        
        // 1. 縦の振り下ろし軌跡 (CRIT粒子)
        for (double i = 0.0; i <= reach * 0.5; i += 0.2) {
            Location point = origin.clone()
                    .add(dir.clone().multiply(reach * 0.5)) // 前方
                    .subtract(0, i, 0);                    // 上から下へ

            origin.getWorld().spawnParticle(org.bukkit.Particle.CRIT, point, 3, 0.01, 0.01, 0.01, 0.1);
        }

        // 2. 着弾地点の判定 (足元の前方)
        Location impactLoc = origin.clone().add(dir.clone().multiply(reach * 0.5));
        impactLoc.setY(origin.getY() - 1.5); // 足元付近へ調整

        // 3. 地面への衝撃波
        origin.getWorld().spawnParticle(org.bukkit.Particle.FLASH, impactLoc.clone().add(0, 0.1, 0), 1, 0, 0, 0, 0, org.bukkit.Color.WHITE);
        origin.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, impactLoc, 15, 0.2, 0.1, 0.2, 0.1, org.bukkit.Material.STONE.createBlockData());
    }
}
