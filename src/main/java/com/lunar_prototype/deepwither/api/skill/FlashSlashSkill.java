package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Random;

/**
 * フラッシュスラッシュ スキル
 * 半径3ブロックにすさまじい数の斬撃をばらまく、序盤の盛り上げ用必殺技
 */
public class FlashSlashSkill implements ISkillLogic {

    private final Random random = new Random();

    @Override
    public boolean cast(Player player, SkillDefinition def, int level) {
        
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    return;
                }

                // 1. 溜めフェーズ (0.1秒 = 2ticks)
                if (ticks < 2) {
                    if (ticks == 0) {
                        // 予兆音: 金属がこすれるような高い音
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 2.0f);
                    }
                    // 予兆パーティクル: プレイヤーを中心に収束する光
                    Location pLoc = player.getEyeLocation().subtract(0, 0.3, 0);
                    player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, pLoc, 10, 0.5, 0.5, 0.5, 0.1);
                }
                
                // 2. 解放フェーズ (Flash Slash)
                else if (ticks == 2) {
                    executeSlash(player, level);
                    this.cancel();
                }

                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    private void executeSlash(Player player, int level) {
        Location center = player.getLocation().add(0, 1.0, 0);
        
        // --- サウンド演出 ---
        // 重厚なベース音 (ユーザー指定)
        player.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.5f);
        // 金属的な斬撃音
        player.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
        player.getWorld().playSound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 1.5f);

        // --- 斬撃エフェクト (すさまじい数の斬撃) ---
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2 / 24) * i + (random.nextDouble() * 0.5);
            double x = Math.cos(angle) * 2.5;
            double z = Math.sin(angle) * 2.5;
            
            // 斬撃の高さにバリエーションを持たせる
            double yOffset = (random.nextDouble() - 0.5) * 1.5;
            Location slashLoc = center.clone().add(x, yOffset, z);
            
            // SWEEP_ATTACK をばらまく
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, slashLoc, 1, 0.2, 0.1, 0.2, 0);
            
            // 跡に残る光 (END_ROD)
            if (i % 2 == 0) {
                player.getWorld().spawnParticle(Particle.END_ROD, slashLoc, 2, 0.1, 0.1, 0.1, 0.05);
            }
        }

        // --- 円状の閃光演出 ---
        for (int j = 0; j < 60; j++) {
            double angle = (Math.PI * 2 / 60) * j;
            double x = Math.cos(angle) * 3.2; // 半径3ブロック強
            double z = Math.sin(angle) * 3.2;
            player.getWorld().spawnParticle(Particle.FIREWORK, center.clone().add(x, 0, z), 1, 0, 0, 0, 0);
        }

        // --- ダメージ判定 ---
        double baseDamage = 15.0 + (level * 2.0);
        double multiplier = 1.2; // 補助スキル程度に少し抑えめ
        double finalDamage = baseDamage * multiplier;

        Collection<Entity> targets = player.getNearbyEntities(3.0, 3.0, 3.0);
        for (Entity entity : targets) {
            if (entity instanceof LivingEntity target && !entity.equals(player)) {
                
                DamageContext ctx = new DamageContext(
                        player, 
                        target, 
                        DeepwitherDamageEvent.DamageType.PHYSICAL, 
                        finalDamage
                );
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                // ヒットエフェクト
                target.getWorld().spawnParticle(Particle.ENCHANTED_HIT, target.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.2);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.6f, 1.5f);
                
                // わずかにノックバックさせて「斬られた」感を出す
                Vector kb = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.3);
                target.setVelocity(target.getVelocity().add(kb));
            }
        }
    }
}
