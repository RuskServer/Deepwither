package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Lightning Burst (ライトニング・バースト) スキル
 * 魔力を一気に溜め、高速の雷弾を15連射する。
 */
public class LightningBurstSkill implements ISkillLogic {

    private final Random random = new Random();

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        World world = caster.getWorld();
        Location eyeLoc = caster.getEyeLocation();

        // 1. チャージ演出 (溜め)
        world.playSound(caster.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.2f, 0.5f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, eyeLoc.add(eyeLoc.getDirection().multiply(0.5)), 20, 0.3, 0.3, 0.3, 0.1);

        // 2. バースト発射 (15連射, 間隔を詰めて高速化)
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 15 || !caster.isValid()) {
                    this.cancel();
                    return;
                }

                fireLightningBolt(caster);
                count++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 5L, 1L); // 5Tick溜めた後、1Tick毎に発射

        return true;
    }

    private void fireLightningBolt(LivingEntity caster) {
        Location spawnLoc = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(1.0));
        Vector direction = caster.getEyeLocation().getDirection();

        // わずかな拡散を加えて「バーって発射する」感を出す
        direction.add(new Vector(
            (random.nextDouble() - 0.5) * 0.05,
            (random.nextDouble() - 0.5) * 0.05,
            (random.nextDouble() - 0.5) * 0.05
        )).normalize();

        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 2.0f);

        new SkillProjectile(caster, spawnLoc, direction) {
            {
                this.speed = 1.6; // 弾速を少し落として視認性向上 (2.0 -> 1.6)
                this.hitboxRadius = 0.8;
                this.maxTicks = 30; // 最大射程 約48ブロック
            }

            @Override
            public void onTick() {
                // 飛翔中の雷パーティクル (黄色系)
                Particle.DustOptions yellowDust = new Particle.DustOptions(Color.fromRGB(221, 255, 26), 1.0f);
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 3, 0.05, 0.05, 0.05, 0.02, yellowDust);
                currentLocation.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, currentLocation, 1, 0, 0, 0, 0.01);
            }

            @Override
            public void onHitEntity(LivingEntity target) {
                hit(target);
            }

            @Override
            public void onHitBlock(Block block) {
                hit(null);
            }

            private void hit(LivingEntity victim) {
                World world = currentLocation.getWorld();
                
                // 1. 演出
                world.playSound(currentLocation, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 2.0f);
                world.playSound(currentLocation, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);
                
                Particle.DustOptions yellowDust = new Particle.DustOptions(Color.fromRGB(221, 255, 26), 2.0f);
                world.spawnParticle(Particle.DUST, currentLocation, 12, 0.2, 0.2, 0.2, 0.1, yellowDust);
                world.spawnParticle(Particle.ELECTRIC_SPARK, currentLocation, 10, 0.3, 0.3, 0.3, 0.5);

                // 2. ダメージ
                if (victim != null && !victim.equals(caster)) {
                    double baseDamage = 5.0;
                    double multiplier = 0.8;
                    double finalDamage = baseDamage * multiplier;

                    DamageContext ctx = new DamageContext(caster, victim, DeepwitherDamageEvent.DamageType.MAGIC, finalDamage);
                    ctx.addTag("BURST");
                    Deepwither.getInstance().getDamageProcessor().process(ctx);
                }
                
                this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }
}
