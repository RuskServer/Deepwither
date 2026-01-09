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
        // --- 1. Mobがダメージを与えた場合 (成功体験) ---
        handleReward(event.getDamager(), 0.3);

        // --- 2. Mobがダメージを受けた場合 (失敗体験 & 攻撃パターンの学習) ---
        handlePenaltyAndPattern(event.getEntity(), event.getDamager(), 0.5);
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

        // 8m以内のエンティティを走査
        player.getNearbyEntities(8, 8, 8).stream()
                .filter(e -> e instanceof Mob) // 【修正】まずMobであることを確認
                .filter(e -> MythicBukkit.inst().getMobManager().isActiveMob(e.getUniqueId())) // MythicMobか確認
                .forEach(e -> {
                    Mob mob = (Mob) e; // ここで安全にキャスト可能

                    // 脳を取得して記録
                    Optional.ofNullable(aiEngine.getBrain(mob.getUniqueId())).ifPresent(brain -> {
                        Vector playerLoc = player.getLocation().toVector();
                        Vector mobLoc = mob.getLocation().toVector();

                        // プレイヤーからMobへの方向ベクトル
                        Vector toMob = mobLoc.clone().subtract(playerLoc).normalize();
                        // プレイヤーの視線方向
                        Vector lookDir = player.getLocation().getDirection();

                        double dist = player.getLocation().distance(mob.getLocation());

                        // 空振り学習を実行 (recordAttack)
                        // isMiss = true として記録
                        brain.recordAttack(
                                player.getUniqueId(),
                                mob.getTicksLived(),
                                dist,
                                true,
                                lookDir,
                                toMob
                        );
                    });
                });
    }

    private Optional<LiquidBrain> getBrain(Entity entity) {
        return MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId())
                .map(am -> aiEngine.getBrain(am.getUniqueId()));
    }
}