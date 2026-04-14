package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Graviton Accelerator Cannon (重力加速砲) スキル
 * 1秒のチャージ（視点固定・移動減速）の後、超高速の重力弾を放つ。
 */
public class GravitonAcceleratorCannonSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 初期視点を保存 (視点移動禁止用)
        final float initialYaw = caster.getLocation().getYaw();
        final float initialPitch = caster.getLocation().getPitch();

        // チャージ開始音
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.0f);

        new BukkitRunnable() {
            int ticks = 0;
            final int CHARGE_TICKS = 20; // 1秒

            @Override
            public void run() {
                if (!caster.isValid()) {
                    this.cancel();
                    return;
                }

                if (ticks >= CHARGE_TICKS) {
                    fire(caster, level);
                    this.cancel();
                    return;
                }

                // 視点固定 (強引にtpさせる)
                Location loc = caster.getLocation();
                loc.setYaw(initialYaw);
                loc.setPitch(initialPitch);
                caster.teleport(loc);

                // 移動速度低下
                caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5, 4, false, false, false));

                // チャージ演出
                Location eyeLoc = caster.getEyeLocation();
                Vector dir = eyeLoc.getDirection();
                Location chargeLoc = eyeLoc.clone().add(dir.multiply(1.5));
                
                // 収束パーティクル (黒と紫)
                caster.getWorld().spawnParticle(Particle.PORTAL, chargeLoc, 5, 0.3, 0.3, 0.3, 0.1);
                caster.getWorld().spawnParticle(Particle.DUST, chargeLoc, 3, 0.2, 0.2, 0.2, 0, 
                        new Particle.DustOptions(Color.fromRGB(153, 45, 255), 1.0f));

                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    private void fire(LivingEntity caster, int level) {
        Location spawnLoc = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(1.0));
        Vector direction = caster.getEyeLocation().getDirection();

        // 発射音
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.5f);

        new SkillProjectile(caster, spawnLoc, direction) {
            {
                this.speed = 4.0; // 極めて高速
                this.hitboxRadius = 1.0;
                this.maxTicks = 60; // 4.0 * 60 = 240ブロック (長射程)
            }

            @Override
            public void onTick() {
                World world = currentLocation.getWorld();
                // MMの設定: flash, Falling_Obsidian_Tear, reddust(#992dff)
                world.spawnParticle(Particle.FLASH, currentLocation, 2, 0.1, 0.1, 0.1, 0);
                world.spawnParticle(Particle.FALLING_OBSIDIAN_TEAR, currentLocation, 2, 0.1, 0.1, 0.1, 0);
                world.spawnParticle(Particle.DUST, currentLocation, 5, 0.1, 0.1, 0.1, 0, 
                        new Particle.DustOptions(Color.fromRGB(153, 45, 255), 1.2f));
            }

            @Override
            public void onHitEntity(LivingEntity target) {
                applyHitEffects(target);
                this.cancel();
            }

            @Override
            public void onHitBlock(Block block) {
                // 着弾地点での演出
                currentLocation.getWorld().spawnParticle(Particle.FLASH, currentLocation, 10, 0.5, 0.5, 0.5, 0.2);
                currentLocation.getWorld().playSound(currentLocation, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.0f, 0.2f);
                this.cancel();
            }

            private void applyHitEffects(LivingEntity target) {
                // ダメージ適用 (倍率 4.0, MAGIC)
                // 元のMM設定が a=150;m=15.0 だったので、m=4.0 に調整
                double damage = 10.0 * 4.0; // ベース10と仮定して4倍
                DamageContext ctx = new DamageContext(caster, target, DeepwitherDamageEvent.DamageType.MAGIC, damage);
                ctx.setWeaponStatType(com.lunar_prototype.deepwither.StatType.MAGIC_DAMAGE);
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                // ヒット時演出
                target.getWorld().spawnParticle(Particle.FLASH, target.getLocation(), 10, 0.5, 0.5, 0.5, 0.2);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.0f, 0.2f);

                // デバフ付与: SLOW 2 (40ticks), DARKNESS 2 (40ticks)
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 1));
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }
}
