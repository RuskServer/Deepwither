package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.skill.utils.SkillParticleUtil;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;

public class MeteorSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        World world = caster.getWorld();
        Location eyeLoc = caster.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        
        // 最大24ブロック先までターゲット探索
        RayTraceResult result = world.rayTraceBlocks(eyeLoc, direction, 24.0, FluidCollisionMode.NEVER, true);
        Location targetLoc;

        if (result != null && result.getHitBlock() != null) {
            targetLoc = result.getHitBlock().getLocation().add(0, 1.0, 0); // ブロックの1マスの上が中心
        } else {
            // ブロックに当たらなかった場合は、24ブロック先のX,Zの地表をターゲットにする
            Location endLoc = eyeLoc.clone().add(direction.multiply(24.0));
            int y = world.getHighestBlockYAt(endLoc.getBlockX(), endLoc.getBlockZ());
            targetLoc = new Location(world, endLoc.getX(), y + 1.0, endLoc.getZ());
        }

        // --- フェーズ1: 予測円描画とチャージ (約0.5秒 = 10Tick) ---
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 10;
            final double radius = 5.0; // AOE半径
            final Color[] warningColors = {
                Color.fromRGB(255, 30, 0),
                Color.fromRGB(255, 120, 0),
                Color.fromRGB(255, 200, 0),
                Color.fromRGB(255, 120, 0),
            };

            @Override
            public void run() {
                if (ticks == 0) {
                    world.playSound(targetLoc, Sound.BLOCK_BEACON_AMBIENT, 2.0f, 1.5f);
                    world.playSound(targetLoc, Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 0.8f);
                }

                // グラデーションリング（64点で滑らか）
                SkillParticleUtil.drawGradientRing(targetLoc, radius, 64, warningColors, 1.2f, 0.05);
                // 内側の山山回転リングで内側に深みを出す
                Particle.DustOptions innerDust = new Particle.DustOptions(Color.fromRGB(255, 80, 0), 0.8f);
                SkillParticleUtil.drawCircleFlatRotating(targetLoc, radius * 0.6, 32, innerDust, 0.05, ticks);

                // 真ん中にも薄く散布
                world.spawnParticle(Particle.LAVA, targetLoc, 2, radius/2, 0.1, radius/2, 0);

                ticks++;
                if (ticks >= maxTicks) {
                    this.cancel();
                    dropMeteor(caster, targetLoc, radius, level);
                }
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    private void dropMeteor(LivingEntity caster, Location targetLoc, double damageRadius, int level) {
        World world = targetLoc.getWorld();
        Location spawnLoc = targetLoc.clone().add(0, 15.0, 0); // 15ブロック上から
        Vector dropDir = new Vector(0, -1, 0); // 真下

        world.playSound(spawnLoc, Sound.ENTITY_GHAST_SHOOT, 2.0f, 0.5f);

        new SkillProjectile(caster, spawnLoc, dropDir) {
            {
                this.speed = 2.0; // かなり速く。約7.5Tickで着弾
                this.hitboxRadius = 1.5;
                this.maxTicks = 60;
            }

            @Override
            public void onTick() {
                // 隕石のパーティクル演出
                Particle.DustOptions magmaColor = new Particle.DustOptions(Color.fromRGB(200, 30, 0), 3.0f);
                
                world.spawnParticle(Particle.DUST, currentLocation, 20, 1.0, 1.0, 1.0, 0, magmaColor);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, currentLocation, 10, 0.5, 0.5, 0.5, 0.05);
                world.spawnParticle(Particle.FLAME, currentLocation, 15, 0.8, 0.8, 0.8, 0.1);
                
                // ブロックパーティクルで黒曜石っぽさを出す
                world.spawnParticle(Particle.BLOCK, currentLocation, 10, 0.5, 0.5, 0.5, Bukkit.createBlockData(Material.OBSIDIAN));

                world.playSound(currentLocation, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.6f);
            }

            @Override
            public void onHitEntity(LivingEntity target) {
                triggerExplosion();
            }

            @Override
            public void onHitBlock(Block block) {
                // targetLoc に近づいたか、ブロックに衝突した場合も爆発
                triggerExplosion();
            }

            private void triggerExplosion() {
                // 大爆発の演出
                world.spawnParticle(Particle.EXPLOSION_EMITTER, currentLocation, 5);
                world.spawnParticle(Particle.LAVA, currentLocation, 50, 3.0, 2.0, 3.0, 0.2);
                
                world.playSound(currentLocation, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
                world.playSound(currentLocation, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0f, 0.5f);

                double baseDamage = 60.0;
                double multiplier = 6.0;
                double finalSkillDamage = baseDamage * multiplier;

                // 範囲内のLivingEntityにダメージとデバフを適用
                Collection<Entity> entities = world.getNearbyEntities(currentLocation, damageRadius, damageRadius, damageRadius);
                for (Entity entity : entities) {
                    if (entity instanceof LivingEntity && !entity.getUniqueId().equals(caster.getUniqueId())) {
                        LivingEntity livingTarget = (LivingEntity) entity;

                        // CustomDamage{a=60;m=6.0;t=MAGIC}
                        DamageContext ctx = new DamageContext(caster, livingTarget, DeepwitherDamageEvent.DamageType.MAGIC, finalSkillDamage);
                        Deepwither.getInstance().getDamageProcessor().process(ctx);

                        // ignite{ticks=120}
                        livingTarget.setFireTicks(120);

                        // potion{type=SLOW;duration=100;lvl=2}
                        livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                    }
                }
                this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }
}
