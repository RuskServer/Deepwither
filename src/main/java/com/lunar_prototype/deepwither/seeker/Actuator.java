package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

public class Actuator {

    public void execute(ActiveMob activeMob, BanditDecision decision, Location coverLoc) {
        Mob entity = (Mob) activeMob.getEntity();

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
        if (move.destination == null) return;

        switch (move.destination) {
            case "NEAREST_COVER":
                if (coverLoc != null) {
                    // PaperのPathfinderを使用して遮蔽物へ移動
                    entity.getPathfinder().moveTo(coverLoc, 1.2); // 少し急いで移動
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

    private void handleActions(ActiveMob activeMob, BanditDecision decision) {
        Entity entity = activeMob.getEntity().getBukkitEntity();
        // スキルの強制発動
        if (decision.decision.use_skill != null && !decision.decision.use_skill.equalsIgnoreCase("NONE")) {
            MythicBukkit.inst().getAPIHelper().castSkill(entity, decision.decision.use_skill);
        }

        // 音声（セリフ）の再生
        if (decision.communication.voice_line != null) {
            String message = "§7[" + activeMob.getType().getInternalName() + "] §f" + decision.communication.voice_line;

            // Location#getNearbyEntities を使用（より確実な方法）
            entity.getLocation().getNearbyEntities(10, 10, 10).forEach(e -> {
                if (e instanceof org.bukkit.entity.Player) {
                    e.sendMessage(message);
                }
            });
        }
    }
}