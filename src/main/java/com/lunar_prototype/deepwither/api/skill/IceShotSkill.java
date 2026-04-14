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

/**
 * Ice Shot (氷結射撃)
 * 4つの氷の弾丸を射出し、命中時に魔法ダメージとシールドブレイクを与える。
 */
public class IceShotSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.5f);

        // 5tick遅延してから発射
        new BukkitRunnable() {
            @Override
            public void run() {
                // 左下, 左上, 右下, 右上
                fireProjectile(caster, -1.0, -0.5);
                fireProjectile(caster, -1.0, 0.5);
                fireProjectile(caster, 1.0, -0.5);
                fireProjectile(caster, 1.0, 0.5);
            }
        }.runTaskLater(Deepwither.getInstance(), 5L);

        return true;
    }

    private void fireProjectile(LivingEntity caster, double sso, double syo) {
        Vector direction = caster.getEyeLocation().getDirection();
        
        // 横（右）方向のベクトルを計算
        Vector right = direction.clone().setY(0).normalize().rotateAroundY(-Math.PI / 2.0);
        if (direction.getY() > 0.9 || direction.getY() < -0.9) {
            // 真上または真下を向いている場合の特殊処理
            right = new Vector(1, 0, 0);
        }
        
        // 上方向のベクトルを計算
        Vector up = right.clone().crossProduct(direction).normalize().multiply(-1);

        // オフセットを適用した出現位置
        Location spawnLoc = caster.getEyeLocation().clone()
                .add(direction.clone().multiply(0.5)) // 少し前方に
                .add(right.clone().multiply(sso * 0.8)) // 横オフセット
                .add(up.clone().multiply(syo * 0.8));   // 縦オフセット
        
        new SkillProjectile(caster, spawnLoc, direction) {
            {
                this.speed = 15.0 / 20.0; // 速度調整
                this.hitboxRadius = 1.0;
                this.maxTicks = 100;
            }

            @Override
            public void onTick() {
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 10, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(0, 195, 255), 2.0f));
                currentLocation.getWorld().spawnParticle(Particle.BLOCK, currentLocation, 5, 0.1, 0.1, 0.1, 0, Material.ICE.createBlockData());
                currentLocation.getWorld().playSound(currentLocation, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.5f);
            }

            @Override
            public void onHitEntity(LivingEntity target) { explode(target); }
            @Override
            public void onHitBlock(Block block) { explode(null); }

            private void explode(LivingEntity target) {
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 10, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(0, 195, 255), 2.0f));
                currentLocation.getWorld().playSound(currentLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.2f);
                
                if (target != null) {
                    // ダメージ: a=5, m=0.6 -> 3.0
                    DamageContext ctx = new DamageContext(caster, target, DeepwitherDamageEvent.DamageType.MAGIC, 3.0);
                    Deepwither.getInstance().getDamageProcessor().process(ctx);
                    
                    // シールドブレイク (200tick = 10秒)
                    // Deepwither.getInstance().getShieldManager().breakShield(target, 200); 
                    // ※ ShieldManagerの実装に合わせて適宜調整してください
                }
                this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }
}
