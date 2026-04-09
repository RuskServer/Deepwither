package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

/**
 * 沈黙の監視者専用スキル: 高速熱光線
 * 単発・超高速・高威力 (80ダメージ)
 */
public class HeatRaySkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 予備動作の開始音
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 2.0f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.4f, 2.0f);

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
                    // 本射撃
                    launchProjectile(caster);
                    this.cancel();
                    return;
                }

                Location eyeLoc = caster.getEyeLocation();
                Vector dir = eyeLoc.getDirection();
                Location spawnLoc = eyeLoc.clone().add(dir.clone().multiply(1.0));

                // 1. 予測線 (レーザーサイト)
                Particle.DustOptions laser = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 0.5f);
                for (double d = 0; d < 30; d += 1.5) {
                    Location p = spawnLoc.clone().add(dir.clone().multiply(d));
                    caster.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, laser);
                }

                // 2. 星型の予備動作パーティクル
                double size = (double) ticks / CHARGE_TICKS; // 収束・拡大演出用
                drawStar(spawnLoc, size);

                // チャージ音
                if (ticks % 5 == 0) {
                    caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.5f + (ticks * 0.05f));
                }

                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    private void launchProjectile(LivingEntity caster) {
        // 発射音: 高音の鋭い音
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 2.0f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 2.0f);

        Location spawnLoc = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(1.0));
        
        new SkillProjectile(caster, spawnLoc, caster.getEyeLocation().getDirection()) {
            {
                this.speed = 2.5; // 超高速
                this.hitboxRadius = 0.5;
                this.maxTicks = 60; // 長距離射程
            }

            @Override
            public void onTick() {
                // 赤い細い線
                Particle.DustOptions line = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.2f);
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 5, 0.02, 0.02, 0.02, 0, line);
            }

            @Override
            public void onHitEntity(LivingEntity target) {
                // 多段ヒット防止 (無敵時間のチェック)
                if (target.getNoDamageTicks() > 10) {
                    return;
                }

                double damage = 80.0;
                DamageContext ctx = new DamageContext(caster, target, DeepwitherDamageEvent.DamageType.MAGIC, damage);
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                // 無敵時間をセット
                target.setNoDamageTicks(20);

                // 着弾エフェクト
                target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0, 1, 0), 3);
                target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CONDUIT_ATTACK_TARGET, 1.0f, 2.0f);
                this.cancel();
            }

            @Override
            public void onHitBlock(Block block) {
                currentLocation.getWorld().playSound(currentLocation, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.5f);
                this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }

    private void drawStar(Location loc, double size) {
        // 簡単な十字・星型パーティクル
        Particle.DustOptions options = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.0f);
        for (double i = -size; i <= size; i += 0.2) {
            loc.add(i, 0, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, options);
            loc.subtract(i, 0, 0);

            loc.add(0, i, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, options);
            loc.subtract(0, i, 0);

            loc.add(0, 0, i);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, options);
            loc.subtract(0, 0, i);
        }
    }
}
