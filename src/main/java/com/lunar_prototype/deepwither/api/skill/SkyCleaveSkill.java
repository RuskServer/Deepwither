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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Random;

/**
 * 遠隔斬撃スキル: SkyCleave
 * 「THE 遠隔斬撃」を具現化した、高速・高威力の物理遠距離スキル。
 */
public class SkyCleaveSkill implements ISkillLogic {

    private final Random random = new Random();

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 放つ瞬間の音
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.2f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.5f);

        Vector direction = caster.getEyeLocation().getDirection();
        Location spawnLoc = caster.getEyeLocation().add(direction.clone().multiply(1.0));

        // 斬撃の基本角度（ロール角）をランダムに設定 (-PI/4 ~ PI/4)
        double rollAngle = (random.nextDouble() - 0.5) * Math.PI;

        new SkillProjectile(caster, spawnLoc, direction) {
            {
                this.speed = 1.8; // 高速
                this.hitboxRadius = 1.5;
                this.maxTicks = 40; // 射程距離用
            }

            @Override
            public void onTick() {
                drawCrescentEffect(currentLocation, direction, rollAngle, ticksLived);
                
                if (ticksLived % 2 == 0) {
                    currentLocation.getWorld().playSound(currentLocation, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.8f);
                }
            }

            @Override
            public void onHitEntity(LivingEntity target) {
                explode(currentLocation, caster);
                this.cancel();
            }

            @Override
            public void onHitBlock(Block block) {
                explode(currentLocation, caster);
                this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    /**
     * 三日月型の斬撃エフェクトを描画
     */
    private void drawCrescentEffect(Location loc, Vector dir, double roll, int ticks) {
        // 進行方向に対する垂直ベクトルを計算
        Vector up = new Vector(0, 1, 0);
        if (Math.abs(dir.getY()) > 0.9) up = new Vector(1, 0, 0);
        
        Vector right = dir.clone().crossProduct(up).normalize();
        Vector actualUp = right.clone().crossProduct(dir).normalize();

        // ロール角の適用
        Vector r = right.clone().multiply(Math.cos(roll)).add(actualUp.clone().multiply(Math.sin(roll)));
        Vector u = right.clone().multiply(-Math.sin(roll)).add(actualUp.clone().multiply(Math.cos(roll)));

        double radius = 4.0;
        // -90度から90度までの広大な円弧を描画
        for (double theta = -Math.PI / 2; theta <= Math.PI / 2; theta += 0.08) {
            double x = Math.cos(theta) * radius;
            double y = Math.sin(theta) * radius;

            // 斬撃の先端に向けて細く鋭くする
            Location pLoc = loc.clone().add(r.clone().multiply(y)).add(u.clone().multiply(x - radius * 0.5));

            // 空間を裂く演出
            pLoc.getWorld().spawnParticle(Particle.PORTAL, pLoc, 1, 0, 0, 0, 0);
            if (random.nextDouble() > 0.8) {
                pLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, pLoc, 1, 0, 0, 0, 0);
            }
        }
        
        // 中心部に鋭い芯を追加
        loc.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);
    }

    /**
     * 着弾時の大爆発
     */
    private void explode(Location loc, LivingEntity caster) {
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 100, 2.0, 2.0, 2.0, 0.2);
        loc.getWorld().spawnParticle(Particle.LAVA, loc, 20, 1.0, 1.0, 1.0, 0.5);
        
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f);
        loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.5f, 0.5f);
        loc.getWorld().playSound(loc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 1.5f);

        // 周囲への範囲ダメージ
        double damage = 20.0;
        Collection<Entity> targets = loc.getWorld().getNearbyEntities(loc, 7, 7, 7);
        for (Entity e : targets) {
            if (e instanceof LivingEntity target && !e.equals(caster)) {
                DamageContext ctx = new DamageContext(caster, target, DeepwitherDamageEvent.DamageType.PHYSICAL, damage);
                Deepwither.getInstance().getDamageProcessor().process(ctx);
                
                // 吹き飛ばし
                Vector knockback = target.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.8).setY(0.6);
                target.setVelocity(knockback);
            }
        }
    }
}
