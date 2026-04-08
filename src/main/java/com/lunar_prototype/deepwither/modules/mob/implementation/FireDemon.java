package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.skill.FireballSkill;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMob;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;

/**
 * プロトタイプモブ: ファイア・デーモン
 * 5秒おきにファイアボールを放ち、攻撃した相手を発火させる。
 */
public class FireDemon extends CustomMob {

    @Override
    public void onSpawn() {
        setMaxHealth(200.0);
        entity.setCustomName("§6§lFire Demon");
        entity.setCustomNameVisible(true);
        
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
        entity.getWorld().spawnParticle(Particle.FLAME, entity.getLocation(), 50, 0.5, 1.0, 0.5, 0.1);
    }

    @Override
    public void onTick() {
        // 100ticks (5秒) おきにスキル発動
        if (getTicksLived() % 100 == 0) {
            castSkill(new FireballSkill(), 3);
        }

        // 常に体に炎のパーティクルを纏う
        if (getTicksLived() % 5 == 0) {
            entity.getWorld().spawnParticle(Particle.LAVA, entity.getLocation().add(0, 1, 0), 1, 0.3, 0.5, 0.3, 0);
        }
    }

    @Override
    public void onAttack(LivingEntity victim, DeepwitherDamageEvent event) {
        // 攻撃した相手を5秒間着火
        victim.setFireTicks(100);
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
    }

    @Override
    public void onDamaged(LivingEntity attacker, DeepwitherDamageEvent event) {
        // ダメージを受けた時に咆哮
        if (event.getDamage() > 10) {
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_BLAZE_HURT, 1.0f, 0.5f);
        }
    }

    @Override
    public void onDeath() {
        entity.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, entity.getLocation(), 1);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
    }
}
