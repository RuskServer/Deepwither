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

        // 1. 演出
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.3f);
        world.spawnParticle(Particle.EXPLOSION, location, 1, 0.1, 0.2, 0.1, 0);
        world.spawnParticle(Particle.FIREWORK, location, 100, 2.0, 2.0, 2.0, 0.1); 
        world.spawnParticle(Particle.GLOW, location, 80, 1.2, 1.2, 1.2, 0.05);

        // 2. ダメージとデバフ (半径1〜5mのリング状)
        double baseDamage = 10.0;
        double multiplier = 1.2;
        double finalDamage = baseDamage * multiplier;

        Collection<Entity> targets = world.getNearbyEntities(location, 5.0, 5.0, 5.0);
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living) || entity.equals(caster)) continue;
            
            double dist = entity.getLocation().distance(location);
            if (dist >= 1.0 && dist <= 5.0) {
                // ダメージ適用 (防御無視 p=true)
                DamageContext ctx = new DamageContext(caster, living, DeepwitherDamageEvent.DamageType.PHYSICAL, finalDamage);
                ctx.setTrueDamage(true);
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                // 鈍足付与 (SLOW 2, 100 ticks)
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            }
        }
    }
}
