package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.skill.HeatRaySkill;
import com.lunar_prototype.deepwither.api.skill.SpreadHeatRaySkill;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMob;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * カスタムモブ: 沈黙の監視者 (Silent Watcher)
 * ハスクベース。320HP。距離を保ちつつ高威力熱光線で攻撃する。
 */
public class SilentWatcher extends CustomMob {

    private int attackCooldown = 0;

    @Override
    public void onSpawn() {
        setMaxHealth(320.0);
        entity.setCustomName("§7§lSilent Watcher");
        entity.setCustomNameVisible(true);

        // 近接攻撃力を最小化
        if (entity.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.0);
        }
        
        // 移動速度を少し速めに
        if (entity.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
        }

        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
    }

    @Override
    public void onTick() {
        if (!(entity instanceof Mob)) return;
        Mob mob = (Mob) entity;
        LivingEntity target = mob.getTarget();

        // 浮遊感のあるパーティクル
        if (getTicksLived() % 10 == 0) {
            entity.getWorld().spawnParticle(Particle.DUST, entity.getLocation().add(0, 1.5, 0), 5, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.GRAY, 1.0f));
        }

        if (target != null) {
            double distance = entity.getLocation().distance(target.getLocation());
            if (distance < 10.0) {
                // 近すぎる場合は後退 (速度を緩和 0.4 -> 0.1)
                Vector away = entity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.1);
                entity.setVelocity(entity.getVelocity().add(away));
                
                // 接近されたら拡散熱光線で迎撃 (1.5秒おき程度)
                if (attackCooldown <= 0 && distance < 6.0) {
                    castSkill(new SpreadHeatRaySkill(), 1);
                    attackCooldown = 30;
                }
            } else if (distance > 15.0) {
                // 遠すぎる場合は近づく (デフォルトAIに任せる)
            } else {
                // 適正距離なら立ち止まって射撃
                entity.setVelocity(entity.getVelocity().multiply(0.5));
            }

            // メイン攻撃: 高速熱光線 (10秒おき)
            if (attackCooldown <= 0 && distance < 30.0) {
                castSkill(new HeatRaySkill(), 1);
                attackCooldown = 200;
                
                // 射撃時に少し反動
                Vector recoil = entity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.2);
                entity.setVelocity(entity.getVelocity().add(recoil));
            }
        }

        if (attackCooldown > 0) attackCooldown--;
    }

    @Override
    public void onDamaged(LivingEntity attacker, DeepwitherDamageEvent event) {
        // ダメージを受けた時に高音のノイズ
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 2.0f);
    }

    @Override
    public void onDeath() {
        entity.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, entity.getLocation().add(0, 1, 0), 1);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 2.0f);
    }
}
