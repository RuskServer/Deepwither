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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * チャージウォーリアスキル: 高速移動突進
 * MythicMobsの charge_warrior を独自スキルシステムで再現
 */
public class ChargeWarriorSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 1. 初期速度の付与 (velocity{m=ADD;x=0;y=0;z=5;r=true})
        // 視線方向に z=5 のパワーで加速
        Vector direction = caster.getEyeLocation().getDirection().normalize();
        Vector velocity = direction.clone().multiply(5.0);
        
        // y軸の過剰な上昇を防ぐため、少し補正するかそのままにするか
        // MMの z=5 は視線方向への強い推進力
        caster.setVelocity(velocity);

        // 発動音
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 1.5f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1.0f, 0.8f);

        // 2. 継続タスク (repeat=20;i=1)
        new BukkitRunnable() {
            int ticks = 0;
            final Set<UUID> hitEntities = new HashSet<>();
            
            @Override
            public void run() {
                if (ticks >= 20 || !caster.isValid()) {
                    this.cancel();
                    return;
                }

                // 壁衝突検知: 速度が著しく低下している場合 (突進開始直後は加速待ちのため ticks > 2)
                if (ticks > 2) {
                    double speed = caster.getVelocity().length();
                    if (speed < 0.2) {
                        this.cancel();
                        // 衝突フィードバック
                        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 0.8f);
                        return;
                    }
                }

                // パーティクル表示 (高速移動感の強化)
                // プレイヤーの足元
                caster.getWorld().spawnParticle(Particle.WAX_OFF, caster.getLocation().add(0, 0.2, 0), 10, 0.2, 0.1, 0.2, 0.05);
                // プレイヤーの背後 (風の跡)
                caster.getWorld().spawnParticle(Particle.CLOUD, caster.getLocation().subtract(direction.clone().multiply(0.5)), 3, 0.1, 0.1, 0.1, 0.02);

                // 周囲の敵へのダメージとノックバック (倍率 50% に調整)
                double baseAtk = 10.0 + (level * 2.0);
                double damage = baseAtk * 0.5; // ダメージ倍率 50%

                Collection<Entity> targets = caster.getNearbyEntities(2.5, 2.5, 2.5);
                
                for (Entity entity : targets) {
                    if (entity instanceof LivingEntity target && !entity.equals(caster)) {
                        
                        // 多段ヒット防止: すでにこの突進でダメージを与えた敵は無視
                        if (hitEntities.contains(target.getUniqueId())) continue;

                        // 無敵時間のチェック (ユーザー要望: 無敵時間貫通なし)
                        if (target.getNoDamageTicks() == 0) {
                            DamageContext ctx = new DamageContext(
                                    caster, 
                                    target, 
                                    DeepwitherDamageEvent.DamageType.PHYSICAL, 
                                    damage
                            );
                            Deepwither.getInstance().getDamageProcessor().process(ctx);

                            // ヒット済みに追加し、突進を即座に終了させる
                            hitEntities.add(target.getUniqueId());
                            
                            // ノックバック処理
                            Vector targetKnockback = direction.clone().multiply(0.8).add(new Vector(0, 0.3, 0));
                            target.setVelocity(targetKnockback);
                            
                            // ヒット音と演出
                            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.2f);
                            target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);

                            // 敵にヒットしたので突進をキャンセル
                            caster.setVelocity(new Vector(0, 0, 0)); // 慣性をリセット
                            this.cancel();
                            return; // ループを抜ける
                        }
                    }
                }

                // 突進中の速度維持
                if (ticks < 15) { 
                    // 進行方向へのベクトルを維持しつつ、重力を少し軽減
                    caster.setVelocity(direction.clone().multiply(1.5).add(new Vector(0, 0.05, 0)));
                }

                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }
}
