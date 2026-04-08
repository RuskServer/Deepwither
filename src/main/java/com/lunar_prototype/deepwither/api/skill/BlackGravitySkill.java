package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.skill.utils.SkillParticleUtil;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;

public class BlackGravitySkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 発動音
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 0.0f);

        Location spawnLoc = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(1.0));
        
        // MMの projectile{v=50} は超高速弾（ほぼ即着弾）
        new SkillProjectile(caster, spawnLoc, caster.getEyeLocation().getDirection()) {
            {
                this.speed = 2.5; // v=50 に近い速度
                this.hitboxRadius = 1.0;
                this.maxTicks = 10; // 最大射程を約25ブロックに制限
            }

            @Override
            public void onTick() {
                // 飛翔中のエフェクト（黒い軌跡）
                currentLocation.getWorld().spawnParticle(Particle.PORTAL, currentLocation, 5, 0.2, 0.2, 0.2, 0.1);
            }

            @Override
            public void onHitEntity(LivingEntity target) {
                triggerBlackHole();
            }

            @Override
            public void onHitBlock(Block block) {
                triggerBlackHole();
            }
            
            @Override
            public void run() {
                super.run();
                if (this.isCancelled() && ticksLived >= maxTicks) {
                    // 何も当たらずに射程限界に達した場合、そこでの空中起動
                    triggerBlackHole();
                }
            }

            private void triggerBlackHole() {
                Location center = currentLocation.clone();
                startBlackHole(caster, center, level);
                this.cancel();
            }

        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    private void startBlackHole(LivingEntity caster, Location center, int level) {
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 80; // repeat=80

            @Override
            public void run() {
                if (ticks >= maxTicks || !caster.isValid()) {
                    this.cancel();
                    // 終了時に少し爆発系パーティクル
                    center.getWorld().spawnParticle(Particle.SONIC_BOOM, center, 1, 0, 0, 0, 0, Color.WHITE);
                    return;
                }

                // --- 1. パーティクル演出 ---
                // 中心点のフラッシュ（白黒の瞬き）
                center.getWorld().spawnParticle(Particle.FLASH, center, 2, 0.2, 0.2, 0.2, 0, Color.WHITE);

                // 周囲から中心へ吸い込まれる安定した演出 (drawSuckParticle)
                for (int i = 0; i < 3; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double r = 4.0;
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    Location edge = center.clone().add(x, (Math.random() - 0.5) * 2, z);
                    SkillParticleUtil.drawSuckParticle(center, edge, Particle.PORTAL, 0.3);
                    center.getWorld().spawnParticle(Particle.LARGE_SMOKE, edge, 1, 0.05, 0.05, 0.05, 0.02);
                }

                // 回転リング（64点外側 + 32点内側の二重リング）
                Particle.DustOptions outerDust = new Particle.DustOptions(Color.fromRGB(40, 10, 80), 2.0f);
                Particle.DustOptions innerDust = new Particle.DustOptions(Color.fromRGB(80, 20, 130), 1.2f);
                SkillParticleUtil.drawCircleFlatRotating(center, 5.0, 64, outerDust, 0.0, ticks);
                SkillParticleUtil.drawCircleFlatRotating(center, 3.0, 32, innerDust, 0.0, -ticks); // 逆方向に回転

                // --- 2. ダメージ判定 (tick 0 と tick 40) ---
                if (ticks == 0 || ticks == 40) {
                    center.getWorld().playSound(center, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.5f, 0.5f);
                    
                    double baseDamage = 15.0;
                    double multiplier = 1.5;
                    double finalDamage = baseDamage * multiplier;

                    Collection<Entity> dmgTargets = center.getWorld().getNearbyEntities(center, 3.0, 3.0, 3.0);
                    for (Entity entity : dmgTargets) {
                        if (entity instanceof LivingEntity && !entity.getUniqueId().equals(caster.getUniqueId())) {
                            LivingEntity living = (LivingEntity) entity;
                            DamageContext ctx = new DamageContext(caster, living, DeepwitherDamageEvent.DamageType.MAGIC, finalDamage);
                            Deepwither.getInstance().getDamageProcessor().process(ctx);
                        }
                    }
                }

                // --- 3. 脱出可能なSoft-Pull (吸引処理) ---
                // 半径8以内の対象を吸引
                Collection<Entity> pullTargets = center.getWorld().getNearbyEntities(center, 8.0, 8.0, 8.0);
                
                // 吸引力の決定 (MMの delay と repeat に基づいた段階的な処理)
                double pullPower = 0.0;
                if (ticks < 10) {
                    pullPower = 0.3; // v=6相当
                } else if (ticks < 30) {
                    pullPower = 0.15; // v=3相当
                } else {
                    pullPower = 0.05; // v=1相当
                }

                for (Entity entity : pullTargets) {
                    if (entity instanceof LivingEntity && !entity.getUniqueId().equals(caster.getUniqueId())) {
                        Vector currentVel = entity.getVelocity();

                        // [脱出許可アルゴリズム]
                        // 長さの二乗が 0.3 以上ある場合、ダッシュやジャンプ等による強い力が働いていると見なす
                        if (currentVel.lengthSquared() > 0.3) {
                            continue; // 強いVelocityがある場合は引っ張らない（抜け出せる）
                        }

                        // 対象から中心へのベクトル
                        Vector toCenter = center.toVector().subtract(entity.getLocation().toVector());
                        
                        // 距離が近すぎる場合は跳ねてしまうので引っ張らない
                        if (toCenter.lengthSquared() < 0.2) continue;

                        toCenter.normalize().multiply(pullPower);
                        
                        // 下への力は少し弱めて、地面にめり込むのを防ぐ
                        toCenter.setY(toCenter.getY() * 0.5);

                        // Velocityの加算（上書きしないことで、他の微細な移動やジャンプが阻害されにくい）
                        entity.setVelocity(currentVel.add(toCenter));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }
}
