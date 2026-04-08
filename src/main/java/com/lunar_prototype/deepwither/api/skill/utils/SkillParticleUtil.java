package com.lunar_prototype.deepwither.api.skill.utils;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * スキルのエフェクト描画に使う共通ユーティリティ
 * 円、螺旋、リング、指向性パーティクルなどの複雑な形状を簡単に描画できます。
 */
public class SkillParticleUtil {

    /**
     * 地面に平行な円（魔法陣のリング）を描画します。
     *
     * @param center  中心座標
     * @param radius  半径（ブロック単位）
     * @param points  円を構成する点の数（多いほど滑らか）
     * @param dust    描画するパーティクルのDustOptions
     * @param yOffset 中心から垂直方向へのオフセット（地面スレスレにしたい場合は0.05など）
     */
    public static void drawCircleFlat(Location center, double radius, int points, Particle.DustOptions dust, double yOffset) {
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = center.clone().add(x, yOffset, z);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
        }
    }

    /**
     * 地面に平行でアニメーション（回転）する円を描画します。
     *
     * @param center       中心座標
     * @param radius       半径
     * @param points       点の数
     * @param dust         パーティクル
     * @param yOffset      Y方向オフセット
     * @param rotationTick 回転タイマー（現在のtick数）。毎tickでわずかに角度をずらします
     */
    public static void drawCircleFlatRotating(Location center, double radius, int points, Particle.DustOptions dust, double yOffset, int rotationTick) {
        World world = center.getWorld();
        double rotOffset = rotationTick * 0.05; // 回転速度の調整
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i + rotOffset;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = center.clone().add(x, yOffset, z);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
        }
    }

    /**
     * 二重の輪（内側 + 外側）で描画する高精細な魔法陣リングです。
     *
     * @param center      中心
     * @param innerRadius 内側の半径
     * @param outerRadius 外側の半径
     * @param points      各リングの点数
     * @param innerDust   内側のパーティクル色
     * @param outerDust   外側のパーティクル色
     */
    public static void drawDoubleRing(Location center, double innerRadius, double outerRadius, int points, Particle.DustOptions innerDust, Particle.DustOptions outerDust) {
        drawCircleFlat(center, innerRadius, points, innerDust, 0.05);
        drawCircleFlat(center, outerRadius, points, outerDust, 0.05);
    }

    /**
     * 複数のカラーが混在する多重リングを描画します。
     * MeteorSkillの警告マーカーなどに使用します。
     *
     * @param center   中心
     * @param radius   メインの半径
     * @param points   点数（推奨: 64以上で滑らかに見える）
     * @param colors   リングに使用する色の配列（順番に1点ごとに割り当てられます）
     * @param size     パーティクルサイズ
     * @param yOffset  Y方向オフセット
     */
    public static void drawGradientRing(Location center, double radius, int points, Color[] colors, float size, double yOffset) {
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = center.clone().add(x, yOffset, z);
            Color col = colors[i % colors.length];
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, new Particle.DustOptions(col, size));
        }
    }

    /**
     * 指定された位置から中心へ向かって飛ぶパーティクルを生成します（吸引演出）。
     *
     * @param center    中心（吸引先）
     * @param from      発生位置
     * @param particle  使用するパーティクル
     * @param speed     Velocityの強さ
     */
    public static void drawSuckParticle(Location center, Location from, Particle particle, double speed) {
        Vector dir = center.toVector().subtract(from.toVector()).normalize().multiply(speed);
        from.getWorld().spawnParticle(particle, from, 0, dir.getX(), dir.getY(), dir.getZ(), 1);
    }

    /**
     * 半径と高さを指定して円柱状にパーティクルを散布します。
     *
     * @param center 中心
     * @param radius 半径
     * @param height 高さ
     * @param amount 生成するパーティクル数
     * @param dust   パーティクル
     */
    public static void drawCylinderScatter(Location center, double radius, double height, int amount, Particle.DustOptions dust) {
        World world = center.getWorld();
        for (int i = 0; i < amount; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = Math.random() * radius;
            double y = Math.random() * height;
            double x = Math.cos(angle) * r;
            double z = Math.sin(angle) * r;
            world.spawnParticle(Particle.DUST, center.clone().add(x, y, z), 1, 0, 0, 0, 0, dust);
        }
    }
}
