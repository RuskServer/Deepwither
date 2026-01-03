package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

public class Actuator {

    public void execute(ActiveMob activeMob, BanditDecision decision, Location coverLoc) {
        if (activeMob.getEntity() == null || !(activeMob.getEntity().getBukkitEntity() instanceof Mob)) {
            return;
        }
        Mob entity = (Mob) activeMob.getEntity().getBukkitEntity();

        // 1. Stance（状態）の反映
        if (decision.decision.new_stance != null) {
            activeMob.setStance(decision.decision.new_stance);
        }

        // 2. 移動戦略の実行
        handleMovement(entity, decision.movement, coverLoc);

        // 3. スキル・コミュニケーションの実行
        handleActions(activeMob, decision);
    }

    private void handleMovement(Mob entity, BanditDecision.MovementPlan move, Location coverLoc) {
        if (move.strategy != null) {
            // --- 追加: 物理的なステップ（回避）処理 ---
            if (move.strategy.equals("BACKSTEP") || move.strategy.equals("SIDESTEP")) {
                performEvasiveStep(entity, move.strategy);
                return; // 物理移動をした場合はPathfinderを上書きするためリターン
            }
        }

        if (move.destination == null) return;

        switch (move.destination) {
            case "NEAREST_COVER":
                if (coverLoc != null) {
                    entity.getPathfinder().moveTo(coverLoc, 1.2);
                }
                break;
            case "ENEMY":
                if (entity.getTarget() != null) {
                    entity.getPathfinder().moveTo(entity.getTarget().getLocation(), 1.0);
                }
                break;
            case "NONE":
                entity.getPathfinder().stopPathfinding();
                break;
        }
    }

    /**
     * 速度ベクトル(Velocity)を直接操作して、瞬間的な回避運動を行う
     */
    private void performEvasiveStep(Mob entity, String strategy) {
        if (entity.getTarget() == null) return;

        Location selfLoc = entity.getLocation();
        Location targetLoc = entity.getTarget().getLocation();

        // ターゲットから自分へのベクトル（逃げる方向）
        Vector awayVec = selfLoc.toVector().subtract(targetLoc.toVector()).normalize();
        Vector finalVel;

        if (strategy.equals("BACKSTEP")) {
            // 真後ろに大きく跳ねる
            finalVel = awayVec.multiply(0.8).setY(0.3);
        } else {
            // サイドステップ（ターゲットに対して垂直な方向）
            // ベクトルの外積を利用して横方向を算出
            Vector sideVec = new Vector(-awayVec.getZ(), 0, awayVec.getX());

            // 左右どちらに避けるかはランダム、または周囲の状況で決める
            if (Math.random() > 0.5) sideVec.multiply(-1);

            finalVel = sideVec.multiply(0.7).setY(0.25);
        }

        // 瞬間的な速度変化を適用
        entity.setVelocity(finalVel);
    }

    private void handleActions(ActiveMob activeMob, BanditDecision decision) {
        Entity entity = activeMob.getEntity().getBukkitEntity();
        // スキルの強制発動
        if (decision.decision.use_skill != null && !decision.decision.use_skill.equalsIgnoreCase("NONE")) {
            MythicBukkit.inst().getAPIHelper().castSkill(entity, decision.decision.use_skill);
        }

        // 音声（セリフ）の再生
        if (decision.communication.voice_line != null) {
            String message = "§7[" + activeMob.getType().getInternalName() + "] §f" + decision.communication.voice_line;
            entity.getNearbyEntities(10, 10, 10).forEach(e -> {
                if (e instanceof org.bukkit.entity.Player) {
                    e.sendMessage(message);
                }
            });
        }
    }
}