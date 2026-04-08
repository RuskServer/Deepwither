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

/**
 * チャージウォーリアスキル: 高速移動突進
 * MythicMobsの charge_warrior を独自スキルシステムで再現
 */
public class ChargeWarriorSkill implements ISkillLogic {

    @Override
    public boolean cast(Player player, SkillDefinition def, int level) {
        // 1. 初期速度の付与 (velocity{m=ADD;x=0;y=0;z=5;r=true})
        // 視線方向に z=5 のパワーで加速
        Vector direction = player.getLocation().getDirection().normalize();
        Vector velocity = direction.clone().multiply(5.0);
        
        // y軸の過剰な上昇を防ぐため、少し補正するかそのままにするか
        // MMの z=5 は視線方向への強い推進力
        player.setVelocity(velocity);

        // 発動音
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1.0f, 0.8f);

        // 2. 継続タスク (repeat=20;i=1)
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20 || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                // パーティクル表示 (高速移動感の強化)
                // プレイヤーの足元
                player.getWorld().spawnParticle(Particle.WAX_OFF, player.getLocation().add(0, 0.2, 0), 10, 0.2, 0.1, 0.2, 0.05);
                // プレイヤーの背後 (風の跡)
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().subtract(direction.clone().multiply(0.5)), 3, 0.1, 0.1, 0.1, 0.02);

                // 周囲の敵へのダメージとノックバック (CustomDamage{a=10;m=0.5} @EIR{r=2})
                // a=10, m=0.5 は基礎威力5。レベルに応じて強化可能
                double baseAtk = 10.0 + (level * 2.0); 
                double damage = baseAtk * 0.5;
                
                Collection<Entity> targets = player.getNearbyEntities(2.5, 2.5, 2.5); // 少し判定を広めに
                
                for (Entity entity : targets) {
                    if (entity instanceof LivingEntity target && !entity.equals(player)) {
                        
                        // 無敵時間のチェック (ユーザー要望: 無敵時間貫通なし)
                        if (target.getNoDamageTicks() == 0) {
                            DamageContext ctx = new DamageContext(
                                    player, 
                                    target, 
                                    DeepwitherDamageEvent.DamageType.PHYSICAL, 
                                    damage
                            );
                            Deepwither.getInstance().getDamageProcessor().process(ctx);

                            // ノックバック処理: 突進の進行方向に弾き飛ばす (THE 高速移動感)
                            Vector targetKnockback = direction.clone().multiply(0.8).add(new Vector(0, 0.3, 0));
                            target.setVelocity(targetKnockback);
                            
                            // ヒット音
                            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.2f);
                            target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
                        }
                    }
                }

                // 突進中の速度維持
                if (ticks < 15) { 
                    // 進行方向へのベクトルを維持しつつ、重力を少し軽減
                    player.setVelocity(direction.clone().multiply(1.5).add(new Vector(0, 0.05, 0)));
                }

                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }
}
