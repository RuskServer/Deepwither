package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Crimson Spear (深緋の槍)
 * 自身の現在HPの30%を血の代償として消費し、
 * 超高速の血の槍を発射して遠距離のターゲットに強力な魔法ダメージを与える。
 * 最大HPの20%未満の場合は発動を拒否する。
 */
public class CrimsonSpearSkill implements ISkillLogic {

    private static final double COST_PERCENT = 0.30;       // 現在HPの30%を消費
    private static final double MIN_HP_PERCENT = 0.20;     // 最大HPの20%未満なら発動不可
    private static final double PROJECTILE_SPEED = 3.5;    // 超高速 (ExplosionArrowの1.4倍)
    private static final double PROJECTILE_RANGE = 150.0;  // 最大射程距離

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        if (!(caster instanceof Player player)) return false;

        var statManager = Deepwither.getInstance().getStatManager();

        double currentHp = statManager.getActualCurrentHealth(player);
        double maxHp = statManager.getActualMaxHealth(player);
        double minHp = maxHp * MIN_HP_PERCENT;

        double bloodCost = currentHp * COST_PERCENT;

        // ===== 1. キャスター自傷 (血の代償) =====
        DamageContext selfCtx = new DamageContext(null, player,
                DeepwitherDamageEvent.DamageType.MAGIC, bloodCost);
        selfCtx.setTrueDamage(true);
        Deepwither.getInstance().getDamageProcessor().process(selfCtx);

        // ===== 2. 血の槍を発射 =====
        Location spawnLoc = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.5));
        Vector direction = player.getEyeLocation().getDirection();

        // 発射音
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.4f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_METAL_BREAK, 1.0f, 0.6f);

        // 発射演出
        playLaunchEffects(player, spawnLoc);

        new SkillProjectile(caster, spawnLoc, direction) {
            {
                this.speed = PROJECTILE_SPEED;
                this.hitboxRadius = 0.6;
                this.maxTicks = (int) (PROJECTILE_RANGE / PROJECTILE_SPEED);
            }

            @Override
            public void onTick() {
                // 槍に追従する血のパーティクル（深紅色 #8B0000）
                Particle.DustOptions bloodDust = new Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.2f);
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 12, 0.15, 0.15, 0.15, 0.08, bloodDust);

                // 軌跡パーティクル
                Particle.DustOptions trailDust = new Particle.DustOptions(Color.fromRGB(255, 20, 147), 0.8f);
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation.clone().add(
                        direction.clone().multiply(0.3).multiply(-1)), 5, 0.05, 0.05, 0.05, 0, trailDust);
            }

            @Override
            public void onHitEntity(LivingEntity target) {
                // 敵またはモブのみダメージ
                if (target.equals(caster)) {
                    return; // 発射者には当たらない
                }
                if (!(target instanceof Monster) && !(target instanceof Player)) {
                    return;
                }

                // ダメージ計算: HPコストの0.8倍をダメージとして与える
                double damage = bloodCost * 0.8 * (1.0 + level * 0.15);

                DamageContext ctx = new DamageContext(caster, target,
                        DeepwitherDamageEvent.DamageType.MAGIC, damage);
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                // 着弾演出
                playImpactEffects(target.getLocation());

                this.cancel();
            }

            @Override
            public void onHitBlock(Block block) {
                // 着弾演出
                playImpactEffects(block.getLocation().add(0.5, 0.5, 0.5));
                this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    private void playLaunchEffects(Player player, Location spawnLoc) {
        // 発射地点からの血の噴き出し
        Particle.DustOptions bloodDust = new Particle.DustOptions(Color.fromRGB(139, 0, 0), 2.0f);
        spawnLoc.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.2, 0),
                50, 0.3, 0.3, 0.3, 0.15, bloodDust);

        // ダメージインジケーター
        spawnLoc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0), 12);
    }

    private void playImpactEffects(Location impactLoc) {
        // 着弾地点での爆発的な血の演出
        Particle.DustOptions bloodDust = new Particle.DustOptions(Color.fromRGB(139, 0, 0), 2.5f);
        Particle.DustOptions darkBloodDust = new Particle.DustOptions(Color.fromRGB(100, 0, 0), 1.8f);

        impactLoc.getWorld().spawnParticle(Particle.DUST, impactLoc, 60, 0.4, 0.4, 0.4, 0.2, bloodDust);
        impactLoc.getWorld().spawnParticle(Particle.DUST, impactLoc, 40, 0.3, 0.3, 0.3, 0.1, darkBloodDust);

        // 爆発エフェクト
        impactLoc.getWorld().spawnParticle(Particle.WAX_OFF, impactLoc, 30, 0.5, 0.5, 0.5, 4.0);

        // 着弾音
        impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.6f);
        impactLoc.getWorld().playSound(impactLoc, Sound.BLOCK_METAL_HIT, 1.0f, 0.8f);
        impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.4f);
    }
}

