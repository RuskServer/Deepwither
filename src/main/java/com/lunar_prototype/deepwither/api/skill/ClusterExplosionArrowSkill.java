package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Cluster Explosion Arrow (クラスター爆発矢) スキル
 * 10発の爆発矢を拡散させながら連続で放つ。
 */
public class ClusterExplosionArrowSkill implements ISkillLogic {

    private final Random random = new Random();

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // ベースとなる矢のスキルインスタンス
        ExplosionArrowSkill baseSkill = new ExplosionArrowSkill();

        // 10連射タスク (repeat=10, i=1)
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 10 || !caster.isValid()) {
                    this.cancel();
                    return;
                }

                Location eyeLoc = caster.getEyeLocation();
                Vector direction = eyeLoc.getDirection();

                // 拡散（hn=10, vn=2 相当から強化）
                // 角度をラジアンに変換して回転を加える
                double spreadH = Math.toRadians(45.0); // 水平45度まで拡大
                double spreadV = Math.toRadians(25.0); // 垂直25度まで拡大
                
                double rotX = (random.nextDouble() - 0.5) * spreadV;
                double rotY = (random.nextDouble() - 0.5) * spreadH;
                
                direction = rotateVector(direction, rotX, rotY);

                // 発射 (ExplosionArrowSkillの内部ロジックを流用したいが、
                // 今回はシンプルに独立したProjectileとして発射する)
                fireSingleArrow(caster, eyeLoc, direction);

                count++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    private void fireSingleArrow(LivingEntity caster, Location eyeLoc, Vector direction) {
        // ExplosionArrowSkillの実装を流用（コピー）して単発の爆発矢を発射
        // ※ 本来は ExplosionArrowSkill 内に public な fire メソッドを作るのが綺麗だが、
        // 現時点では副作用を避けるためここに直接記述する。
        
        Location spawnLoc = eyeLoc.clone().add(direction.clone().multiply(1.0));
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.2f);

        new SkillProjectile(caster, spawnLoc, direction) {
            {
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
            public void onHitEntity(LivingEntity target) { explode(); }
            @Override
            public void onHitBlock(org.bukkit.block.Block block) { explode(); }

            private void explode() {
                // キャスターからの距離に応じてダメージを減衰させる (20ブロックで1.0, それ以下は減衰)
                double distance = caster.getLocation().distance(currentLocation);
                
                // 15ブロックまでは線形に減衰し、それ以降は1.0 (20ブロックターゲットだが15ブロックで最大に到達するように調整)
                double distMultiplier = Math.min(1.0, distance / 15.0);
                
                // 最低保証 0.3倍 (「バチクソ」ほどではないが、近距離は明確に弱く)
                double finalMultiplier = Math.max(0.3, distMultiplier);

                com.lunar_prototype.deepwither.api.skill.utils.ExplosionUtil.triggerExplosionArrowEffect(caster, currentLocation, finalMultiplier);
                this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
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
