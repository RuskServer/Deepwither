package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * 沈黙の監視者専用スキル: 高速熱光線 (チャージ単発版)
 * 1秒のチャージ(後半のみ予測線)、判定半径0.3、一瞬の閃光ビーム
 */
public class HeatRaySkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 予備動作の開始音 (重低音で溜めを強調)
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            final int CHARGE_TICKS = 20; // 1秒間の予備動作

            @Override
            public void run() {
                if (!caster.isValid()) {
                    this.cancel();
                    return;
                }

                if (ticks >= CHARGE_TICKS) {
                    // 本射撃 (一瞬で終わる単発ビーム)
                    fireHeatRay(caster);
                    this.cancel();
                    return;
                }

                Location eyeLoc = caster.getEyeLocation();
                Vector dir = eyeLoc.getDirection();
                Location spawnLoc = eyeLoc.clone().add(dir.clone().multiply(1.0));

                // 1. 弾道予測線 (発射直前の後半 0.5秒のみ、かつ疎な点線)
                // これにより「照射」ではなく「狙いを定めている」演出にする
                if (ticks >= 10) {
                    Particle.DustOptions laser = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 0.5f);
                    for (double d = 2.0; d < 50; d += 4.0) { // 4ブロックおきに表示して点線化
                        Location p = spawnLoc.clone().add(dir.clone().multiply(d));
                        caster.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, laser);
                    }
                }

                // 2. 収束エフェクト (パワーを溜める演出)
                double progress = (double) ticks / CHARGE_TICKS;
                drawChargeEffect(spawnLoc, progress);

                // チャージ音 (ピッチを上げていく)
                if (ticks % 4 == 0) {
                    caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 0.5f + (ticks * 0.08f));
                }

                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    private void fireHeatRay(LivingEntity caster) {
        World world = caster.getWorld();
        Location eyeLoc = caster.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        
        double maxRange = 60.0;

        // レイキャスト判定 (単発1ヒット, 半径 0.3)
        RayTraceResult result = world.rayTrace(
                eyeLoc, 
                direction, 
                maxRange, 
                FluidCollisionMode.NEVER, 
                true, 
                0.3, 
                entity -> !entity.equals(caster) && entity instanceof LivingEntity
        );

        double dist = maxRange;
        Location hitLoc = eyeLoc.clone().add(direction.clone().multiply(maxRange));

        if (result != null) {
            if (result.getHitBlock() != null) {
                hitLoc = result.getHitPosition().toLocation(world);
                dist = eyeLoc.distance(hitLoc);
                world.playSound(hitLoc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.5f);
            }
            
            if (result.getHitEntity() instanceof LivingEntity target) {
                hitLoc = result.getHitPosition().toLocation(world);
                dist = eyeLoc.distance(hitLoc);
                
                // ダメージ適用 (40.0) - 威力調整: 80.0 -> 40.0
                double damage = 40.0;
                DamageContext ctx = new DamageContext(caster, target, DeepwitherDamageEvent.DamageType.MAGIC, damage);
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                target.getWorld().spawnParticle(Particle.FLASH, hitLoc, 5, 0, 0, 0, 0, Color.WHITE);
                target.getWorld().playSound(hitLoc, Sound.BLOCK_CONDUIT_ATTACK_TARGET, 1.0f, 2.0f);
            }
        }

        // 発射音 (鋭い爆発音)
        world.playSound(eyeLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 2.0f);
        world.playSound(eyeLoc, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 2.0f);

        // ビーム演出 (一瞬の閃光を描画)
        // 密度を高めて一撃の「筋」を見せるが、ティックを跨がないため瞬時に消える
        Particle.DustOptions beamOptions = new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.2f);
        for (double d = 0; d < dist; d += 0.8) {
            Location p = eyeLoc.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.DUST, p, 2, 0.01, 0.01, 0.01, 0, beamOptions);
            if (d % 5 == 0) {
                world.spawnParticle(Particle.FLASH, p, 1, 0, 0, 0, 0, Color.WHITE);
            }
        }
    }

    private void drawChargeEffect(Location loc, double progress) {
        // 次第に赤く染まりながら収束する球状パーティクル
        int red = (int) (150 + (progress * 105));
        int gb = (int) (255 * (1.0 - progress));
        Particle.DustOptions options = new Particle.DustOptions(Color.fromRGB(red, gb, gb), 1.0f);
        
        double radius = (1.2 - progress) * 1.5; 
        if (radius < 0.1) radius = 0.1;

        for (double i = 0; i < Math.PI * 2; i += Math.PI / 3) {
            double x = Math.cos(i) * radius;
            double y = Math.sin(i) * radius;
            
            // 各軸で回転させる
            spawnChargeParticle(loc, x, y, 0, options);
            spawnChargeParticle(loc, 0, x, y, options);
            spawnChargeParticle(loc, x, 0, y, options);
        }
    }

    private void spawnChargeParticle(Location base, double x, double y, double z, Particle.DustOptions opt) {
        base.add(x, y, z);
        base.getWorld().spawnParticle(Particle.DUST, base, 1, 0, 0, 0, 0, opt);
        base.subtract(x, y, z);
    }
}
