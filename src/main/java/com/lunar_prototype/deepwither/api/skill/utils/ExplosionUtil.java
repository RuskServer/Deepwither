package com.lunar_prototype.deepwither.api.skill.utils;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;

public class ExplosionUtil {

    /**
     * Explosion Arrowの爆発効果（演出 + ダメージ + デバフ）を一括処理します。
     */
    public static void triggerExplosionArrowEffect(LivingEntity caster, Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // 1. 演出 (強化版)
        // 音の重層化
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
        world.playSound(location, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0f, 0.6f);
        world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 0.5f);

        // 中心部の巨大爆発
        world.spawnParticle(Particle.EXPLOSION, location, 3, 0.5, 0.5, 0.5, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, location, 2, 0.2, 0.2, 0.2, 0);

        // 飛散する火花と破片
        world.spawnParticle(Particle.LAVA, location, 40, 1.5, 1.5, 1.5, 0.2);
        world.spawnParticle(Particle.FLAME, location, 60, 2.5, 2.5, 2.5, 0.1);

        // 立ち昇る濃煙
        world.spawnParticle(Particle.LARGE_SMOKE, location, 30, 1.0, 2.0, 1.0, 0.05);

        // 魔法的な輝き
        world.spawnParticle(Particle.FIREWORK, location, 150, 3.0, 3.0, 3.0, 0.1);
        world.spawnParticle(Particle.GLOW, location, 100, 2.0, 2.0, 2.0, 0.05);

        // 2. ダメージとデバフ (半径1〜5mのリング状)
        double baseDamage = 5.0; // 遠距離ダメージ属性が乗るようになったため、基礎ダメージを半減 (10.0 -> 5.0)
        double multiplier = 1.2;
        double finalDamage = baseDamage * multiplier;

        Collection<Entity> targets = world.getNearbyEntities(location, 5.0, 5.0, 5.0);
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living) || entity.equals(caster)) continue;
            
            double dist = entity.getLocation().distance(location);
            if (dist >= 1.0 && dist <= 5.0) {
                // 無敵時間チェック (多段ヒット防止)
                if (living.getNoDamageTicks() > 10) continue;

                // ダメージ適用 (防御無視 p=true, 遠距離判定)
                DamageContext ctx = new DamageContext(caster, living, DeepwitherDamageEvent.DamageType.PHYSICAL, finalDamage);
                ctx.setTrueDamage(true);
                ctx.setProjectile(true);
                ctx.setWeaponStatType(com.lunar_prototype.deepwither.StatType.PROJECTILE_DAMAGE);
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                // 鈍足付与 (SLOW 2, 100 ticks)
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));

                // 無敵時間を付与
                living.setNoDamageTicks(10);
            }
        }
    }
}
