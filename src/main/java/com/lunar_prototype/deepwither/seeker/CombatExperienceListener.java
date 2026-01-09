package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.util.Vector;

import java.util.Optional;

public class CombatExperienceListener implements Listener {
    private final SeekerAIEngine aiEngine;

    public CombatExperienceListener(SeekerAIEngine aiEngine) {
        this.aiEngine = aiEngine;
    }

    @EventHandler
    public void onCombat(EntityDamageByEntityEvent event) {
        double damage = event.getFinalDamage();

        // --- 1. Mobがダメージを与えた場合 (成功体験) ---
        if (event.getDamager() instanceof Mob) {
            Mob attacker = (Mob) event.getDamager();
            // 既存のReward処理
            handleReward(attacker, 0.3);

            // Q-Learning: ダメージを与えたのでプラスの報酬
            applyLearning(attacker, damage, 0.0,false);

            // 戦術メモリーの更新
            getBrain(attacker).ifPresent(brain -> brain.tacticalMemory.myHits++);
        }

        // --- 2. Mobがダメージを受けた場合 (失敗体験 & 攻撃パターンの学習) ---
        if (event.getEntity() instanceof Mob) {
            Mob victim = (Mob) event.getEntity();

            // 既存のPenalty処理
            handlePenaltyAndPattern(victim, event.getDamager(), 0.5);

            // Q-Learning: ダメージを受けたのでマイナスの報酬
            applyLearning(victim, 0.0, damage,false);

            // 被弾による戦術メモリーの更新
            getBrain(victim).ifPresent(brain -> brain.tacticalMemory.takenHits++);

            // 相手がプレイヤーなら攻撃パターンを詳細に記録
            if (event.getDamager() instanceof Player) {
                Player p = (Player) event.getDamager();
                getBrain(victim).ifPresent(brain -> {
                    double dist = p.getLocation().distance(victim.getLocation());
                    // isMiss = false (命中弾) として記録
                    brain.recordAttack(p.getUniqueId(), victim.getTicksLived(), dist, false, null, null);
                });
            }
        }
    }

    private void handleReward(Entity entity, double amount) {
        getBrain(entity).ifPresent(brain -> {
            brain.accumulatedReward += amount;
            brain.recordSelfAttack(entity.getTicksLived());
        });
    }

    /**
     * 失敗体験の蓄積に加え、V2用の攻撃リズム・距離学習を実行する
     */
    private void handlePenaltyAndPattern(Entity victim, Entity damager, double amount) {
        if (!(victim instanceof Mob)) return;
        Mob mob = (Mob) victim;

        getBrain(mob).ifPresent(brain -> {
            // 失敗体験（ペナルティ）の蓄積
            brain.accumulatedPenalty += amount;

            // --- V2機能: 攻撃パターンの記録 ---
            // 攻撃者がプレイヤーの場合のみ、その距離と時間を脳に記録する
            if (damager instanceof Player) {
                Player player = (Player) damager;
                double distance = player.getLocation().distance(mob.getLocation());
                long currentTick = mob.getTicksLived(); // 個体の生存Tickを時間軸として使用
                Vector toMob = mob.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();

                brain.recordAttack(player.getUniqueId(), currentTick, distance,false,player.getLocation().getDirection(),toMob);
            }
        });
    }

    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        Player player = event.getPlayer();

        player.getNearbyEntities(8, 8, 8).stream()
                .filter(e -> e instanceof Mob)
                .filter(e -> MythicBukkit.inst().getMobManager().isActiveMob(e.getUniqueId()))
                .forEach(e -> {
                    Mob mob = (Mob) e;
                    Optional.ofNullable(aiEngine.getBrain(mob.getUniqueId())).ifPresent(brain -> {
                        Vector playerLoc = player.getLocation().toVector();
                        Vector mobLoc = mob.getLocation().toVector();
                        Vector toMob = mobLoc.clone().subtract(playerLoc).normalize();
                        Vector lookDir = player.getLocation().getDirection();
                        double dist = player.getLocation().distance(mob.getLocation());

                        // --- [追加] 回避成功の学習 ---
                        // プレイヤーがMobをしっかり狙っている（dot > 0.9）のに当たっていない場合
                        if (lookDir.dot(toMob) > 0.9) {
                            // 回避成功報酬（0.8程度）を与える
                            // これにより「EVADE」や「BURST_DASH」のQ値が跳ね上がる
                            applyLearning(mob, 0.0, 0.0, true);
                            brain.tacticalMemory.avoidedHits++; // 回避カウンター増
                        }

                        // 既存の空振りリズム学習
                        brain.recordAttack(player.getUniqueId(), mob.getTicksLived(), dist, true, lookDir, toMob);
                    });
                });
    }

    private Optional<LiquidBrain> getBrain(Entity entity) {
        return MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId())
                .map(am -> aiEngine.getBrain(am.getUniqueId()));
    }

    public void applyLearning(Mob mob, double damageDealt, double damageTaken, boolean evaded) {
        // OptionalをifPresentで展開して処理する
        getBrain(mob).ifPresent(brain -> {
            double reward = 0.0;

            // 1. ダメージベースの報酬
            // 与えたダメージは正義、受けたダメージは反省
            reward += (damageDealt * 2.0);
            reward -= (damageTaken * 1.5);

            if (evaded) {
                reward += 1.5; // 被弾を抑えたことへの大きな報酬
            }

            // 2. 状況ベースの報酬（ハメ対策）
            if (mob.getTarget() instanceof Player) {
                double dist = mob.getLocation().distance(mob.getTarget().getLocation());

                // 槍の間合い（4~6m程度）を潰して接近できたらボーナス報酬
                // damageTakenが少ない状態で接近できた＝上手く潜り込めたと判定
                if (dist < 3.0 && damageTaken < 2.0) {
                    reward += 1.0;
                }
            }

            // 3. Q-Tableの更新
            // 次の状態（Next State）を推測してQ値を更新する
            String nextState = brain.qTable.getStateKey(
                    brain.tacticalMemory.combatAdvantage,
                    mob.getTarget() != null ? mob.getLocation().distance(mob.getTarget().getLocation()) : 20.0,
                    false // 次の状態の回復フラグは簡略化
            );

            // 前回の行動がこの結果を招いたとして学習
            brain.qTable.update(
                    brain.lastStateKey,
                    brain.lastActionType,
                    reward,
                    nextState
            );
        });
    }
}