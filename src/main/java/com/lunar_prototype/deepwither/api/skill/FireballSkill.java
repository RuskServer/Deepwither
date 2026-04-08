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
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FireballSkill implements ISkillLogic {

    @Override
    public boolean cast(Player player, SkillDefinition def, int level) {
        // 発射音
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.2f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);

        // 発射位置 (EyeLocation から少し前方)
        Location spawnLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.2));
        
        new SkillProjectile(player, spawnLoc, player.getLocation().getDirection()) {
            {
                // MMの fireball projectileに相当する設定
                this.speed = 1.0; // v=8 (0.4 blocks/tick)、だがMMの弾速は独自計算が入る。とりあえず1.0にする
                this.hitboxRadius = 1.0;
                this.maxTicks = 100;
            }

            @Override
            public void onTick() {
                // パーティクル: レッドダストを用いた螺旋の構築など
                Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(255, 82, 52), 1.5f);
                Particle.DustOptions outer = new Particle.DustOptions(Color.fromRGB(145, 43, 25), 2.0f);
                
                // 弾の本体 (Core)
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 5, 0.1, 0.1, 0.1, 0, core);
                // 弾の周り (Outer)
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 10, 0.3, 0.3, 0.3, 0, outer);
                
                // 飛翔音を少し鳴らす (Tickごとはうるさいので少し絞るか音圧を下げる)
                if (ticksLived % 2 == 0) {
                    currentLocation.getWorld().playSound(currentLocation, Sound.BLOCK_FIRE_AMBIENT, 0.3f, 1.5f);
                }
            }

            @Override
            public void onHitEntity(LivingEntity target) {
                // customdamage{a=15;m=1.5;t=MAGIC} 相当の設定
                double baseDamage = 15.0; // 本来は level 等から計算すべき値
                double multiplier = 1.5;
                double finalSkillDamage = baseDamage * multiplier;

                // ダメージ適用
                DamageContext ctx = new DamageContext(caster, target, DeepwitherDamageEvent.DamageType.MAGIC, finalSkillDamage);
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                // potion{type=SLOW;duration=100;lvl=2}
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1)); // lvl 2 = amplifier 1

                // Hitエフェクト (origin指定)
                Particle.DustOptions hitEffect = new Particle.DustOptions(Color.fromRGB(255, 82, 52), 3.0f);
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0, hitEffect);
                
                // サウンド強化
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BLAZE_HURT, 1.0f, 0.6f);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 2.0f);
                
                this.cancel(); // 弾を消滅
            }

            @Override
            public void onHitBlock(Block block) {
                // ブロック着弾時にもエフェクト
                Particle.DustOptions hitEffect = new Particle.DustOptions(Color.fromRGB(255, 82, 52), 2.0f);
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 20, 0.4, 0.4, 0.4, 0, hitEffect);
                
                // サウンド強化
                currentLocation.getWorld().playSound(currentLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
                currentLocation.getWorld().playSound(currentLocation, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f);
                
                this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L); // 1tickごとにTick

        return true;
    }
}
