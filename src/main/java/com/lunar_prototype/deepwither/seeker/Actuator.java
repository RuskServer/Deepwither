package com.lunar_prototype.deepwither.seeker;

import com.lunar_prototype.deepwither.Deepwither;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.util.List;

public class Actuator {

    private double chaosX = 0.1, chaosY = 0.0, chaosZ = 0.0;
    private static final double SIGMA = 10.0;
    private static final double RHO = 28.0;
    private static final double BETA = 8.0 / 3.0;
    private static final double DT = 0.02;

    public void execute(ActiveMob activeMob, BanditDecision decision, Location coverLoc) {
        if (activeMob.getEntity() == null || !(activeMob.getEntity().getBukkitEntity() instanceof Mob)) return;
        Mob entity = (Mob) activeMob.getEntity().getBukkitEntity();

        if (decision.decision.new_stance != null) activeMob.setStance(decision.decision.new_stance);
        handleMovement(entity, decision.movement, coverLoc);

        if (entity.getTarget() != null) {
            double dist = entity.getLocation().distance(entity.getTarget().getLocation());
            double reach = 3.5;
            if (dist <= reach) {
                if (Deepwither.getInstance().getAiEngine().getBrain(activeMob.getUniqueId()).systemTemperature > 1.2f || Math.random() < 0.3) {
                    entity.attack(entity.getTarget());
                }
            }
        }
        handleActions(activeMob, decision);
    }

    private void handleMovement(Mob entity, BanditDecision.MovementPlan move, Location coverLoc) {
        if (entity.getVelocity().length() > 0.5 && entity.getVelocity().length() < 0.01) {
            entity.setVelocity(entity.getVelocity().setY(0.4));
        }
        if (move.strategy != null) {
            if (move.strategy.equals("BACKSTEP") || move.strategy.equals("SIDESTEP")) {
                performEvasiveStep(entity, move.strategy,Deepwither.getInstance().getAiEngine().getBrain(entity.getUniqueId()));
                return;
            }
            if (move.strategy.equals("CHARGE")) { performDirectCharge(entity); return; }
            if (move.strategy.equals("BURST_DASH")) { performBurstDash(entity,1.4, Deepwither.getInstance().getAiEngine().getBrain(entity.getUniqueId())); return; }
            if (move.strategy.equals("ORBITAL_SLIDE")) { performOrbitalSlide(entity); return; }
            if (move.strategy.equals("POST_ATTACK_EVADE")) { performPostAttackEvade(entity); return; }
            if (move.strategy.equals("SPRINT_ZIGZAG")) { performSprintZigzag(entity); return; }
            if (move.strategy.equals("ESCAPE_SQUEEZE")) { performEscapeSqueeze(entity,new SensorProvider().scanEnemies(entity,entity.getNearbyEntities(32, 32, 32))); return; }
        }
        if (move.strategy != null && move.strategy.equals("MAINTAIN_DISTANCE")) { maintainDistance(entity); return; }
        if (move.destination == null) return;
        switch (move.destination) {
            case "NEAREST_COVER" -> { if (coverLoc != null) entity.getPathfinder().moveTo(coverLoc, 1.2); }
            case "ENEMY" -> { if (entity.getTarget() != null) entity.getPathfinder().moveTo(entity.getTarget().getLocation(), 1.0); }
            case "NONE" -> entity.getPathfinder().stopPathfinding();
        }
    }

    private void performEscapeSqueeze(Mob entity, List<BanditContext.EnemyInfo> enemies) {
        Vector escapeVec = new Vector(0, 0, 0);
        for (BanditContext.EnemyInfo enemy : enemies) {
            Vector diff = entity.getLocation().toVector().subtract(enemy.playerInstance.getLocation().toVector());
            escapeVec.add(diff.normalize().multiply(1.0 / enemy.dist));
        }
        if (!isPathBlocked(entity, escapeVec.normalize())) {
            entity.setVelocity(escapeVec.normalize().multiply(1.2).setY(0.1));
        } else {
            entity.getPathfinder().moveTo(entity.getLocation().add(escapeVec.multiply(3)), 2.0);
        }
    }

    private boolean isPathBlocked(Mob entity, Vector direction) {
        Location eyeLoc = entity.getEyeLocation();
        Location targetCheck = eyeLoc.clone().add(direction.clone().multiply(1.5));
        if (!targetCheck.getBlock().getType().isAir()) return true;
        Location floorCheck = targetCheck.clone().subtract(0, 2, 0);
        return floorCheck.getBlock().getType().isAir();
    }

    private void performBurstDash(Mob entity, double power, LiquidBrain brain) {
        if (entity.getTarget() == null) return;
        Location myLoc = entity.getLocation();
        Entity target = entity.getTarget();
        double dist = myLoc.distance(target.getLocation());
        Vector targetDir;
        if (dist < 15.0 && brain.lastPredictedLocation != null && brain.velocityTrust > 0.3) {
            Vector predictedPos = brain.lastPredictedLocation;
            targetDir = predictedPos.clone().subtract(myLoc.toVector()).normalize();
            targetDir.multiply(1.2).add(target.getLocation().toVector().subtract(myLoc.toVector()).normalize()).normalize();
        } else {
            targetDir = target.getLocation().toVector().subtract(myLoc.toVector()).normalize();
        }
        if (entity.getLocation().subtract(0, 0.1, 0).getBlock().getType().isSolid()) {
            Vector boost = targetDir.multiply(power).setY(0.42);
            if (isPathBlocked(entity, targetDir)) { boost.setY(0.55); boost.multiply(0.85); }
            entity.setVelocity(boost);
            int[] rgb = brain.getTQHFlashColor();
            entity.getWorld().spawnParticle(org.bukkit.Particle.DUST, entity.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(rgb[0], rgb[1], rgb[2]), 1.5f));
        } else {
            entity.setVelocity(entity.getVelocity().add(targetDir.multiply(0.08)));
        }
    }

    private void performDirectCharge(Mob entity) {
        if (entity.getTarget() == null) return;
        Location targetLoc = entity.getTarget().getLocation();
        Vector direction = targetLoc.toVector().subtract(entity.getLocation().toVector()).normalize();
        entity.getPathfinder().moveTo(targetLoc.clone().add(direction.multiply(1.5)), 2.5);
    }

    private void performOrbitalSlide(Mob entity) {
        if (entity.getTarget() == null) return;
        double dx = SIGMA * (chaosY - chaosX) * DT;
        double dy = (chaosX * (RHO - chaosZ) - chaosY) * DT;
        double dz = (chaosX * chaosY - BETA * chaosZ) * DT;
        chaosX += dx; chaosY += dy; chaosZ += dz;
        Location self = entity.getLocation();
        Vector toTarget = entity.getTarget().getLocation().toVector().subtract(self.toVector()).normalize();
        Vector side = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();
        double lateralMod = chaosX * 0.15;
        double longitudinalMod = chaosY * 0.1;
        Vector chaoticVel = toTarget.multiply(0.4 + longitudinalMod).add(side.multiply(lateralMod));
        if (isPathBlocked(entity, chaoticVel.clone().normalize())) {
            chaosX *= -1;
            chaoticVel = toTarget.multiply(0.4).add(side.multiply(chaosX * 0.15));
        }
        entity.setVelocity(chaoticVel.setY(0.1));
    }

    private void performPostAttackEvade(Mob entity) {
        if (entity.getTarget() == null) return;
        Location selfLoc = entity.getLocation();
        Location targetLoc = entity.getTarget().getLocation();
        Vector awayVec = selfLoc.toVector().subtract(targetLoc.toVector()).normalize();
        Vector sideVec = new Vector(-awayVec.getZ(), 0, awayVec.getX()).normalize();
        if (entity.getUniqueId().getMostSignificantBits() % 2 == 0) sideVec.multiply(-1);
        entity.getPathfinder().moveTo(selfLoc.clone().add(awayVec.multiply(3.0)).add(sideVec.multiply(2.0)), 1.8);
    }

    private void performEvasiveStep(Mob entity, String strategy, LiquidBrain brain) {
        if (entity.getTarget() == null) return;
        Location selfLoc = entity.getLocation();
        Entity target = entity.getTarget();
        Vector awayDir = selfLoc.toVector().subtract(target.getLocation().toVector()).setY(0).normalize();
        Location bestDest = null;
        if (strategy.equals("BACKSTEP")) bestDest = findSafeDestination(selfLoc, awayDir, 3.5);
        if (bestDest == null || strategy.equals("SIDESTEP")) {
            Vector leftDir = new Vector(-awayDir.getZ(), 0, awayDir.getX());
            Vector rightDir = leftDir.clone().multiply(-1);
            Location leftDest = findSafeDestination(selfLoc, leftDir, 4.0);
            Location rightDest = findSafeDestination(selfLoc, rightDir, 4.0);
            bestDest = (leftDest != null) ? leftDest : rightDest;
        }
        if (bestDest != null) {
            if (strategy.equals("BACKSTEP") && selfLoc.distance(target.getLocation()) < 4.0) executeCounterStrike(entity, target, brain);
            entity.getPathfinder().stopPathfinding();
            if (brain.systemTemperature > 1.0f) {
                Vector jumpDir = bestDest.toVector().subtract(selfLoc.toVector()).normalize().multiply(0.6);
                entity.setVelocity(entity.getVelocity().add(jumpDir.setY(0.25)));
            }
            entity.getPathfinder().moveTo(bestDest, 1.8);
            entity.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, selfLoc, 3, 0.2, 0.1, 0.2, 0.02);
        }
    }

    private void executeCounterStrike(Mob entity, Entity target, LiquidBrain brain) {
        entity.attack(target);
        entity.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, entity.getEyeLocation().add(entity.getLocation().getDirection().multiply(1.2)), 1);
        if (brain.systemTemperature > 1.2f) {
            Vector push = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(0.5).setY(0.2);
            target.setVelocity(target.getVelocity().add(push));
        }
    }

    private Location findSafeDestination(Location start, Vector dir, double dist) {
        Location dest = start.clone().add(dir.multiply(dist));
        var ray = start.getWorld().rayTraceBlocks(start.clone().add(0, 1, 0), dir, dist);
        if (ray != null && ray.getHitBlock() != null) return null;
        if (!dest.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
            if (!dest.clone().subtract(0, 2, 0).getBlock().getType().isSolid()) return null;
        }
        return dest.clone().add(0, 1, 0);
    }

    private void handleActions(ActiveMob activeMob, BanditDecision decision) {
        Entity entity = activeMob.getEntity().getBukkitEntity();
        if (decision.decision.use_skill != null && !decision.decision.use_skill.equalsIgnoreCase("NONE")) {
            MythicBukkit.inst().getAPIHelper().castSkill(entity, decision.decision.use_skill);
        }
        if (decision.communication.voice_line != null) {
            Component message = Component.text("[" + activeMob.getType().getInternalName() + "] ", NamedTextColor.GRAY)
                    .append(Component.text(decision.communication.voice_line, NamedTextColor.WHITE));
            entity.getNearbyEntities(10, 10, 10).forEach(e -> { if (e instanceof org.bukkit.entity.Player) e.sendMessage(message); });
        }
    }

    private void maintainDistance(Mob entity) {
        if (entity.getTarget() == null) return;
        Location targetLoc = entity.getTarget().getLocation();
        Location selfLoc = entity.getLocation();
        double dist = selfLoc.distance(targetLoc);
        Vector direction = (dist < 6.0) ? selfLoc.toVector().subtract(targetLoc.toVector()).normalize() : targetLoc.toVector().subtract(selfLoc.toVector()).normalize();
        Vector sideStep = new Vector(-direction.getZ(), 0, direction.getX()).multiply(Math.sin(entity.getTicksLived() * 0.1) * 2.0);
        entity.getPathfinder().moveTo(selfLoc.clone().add(direction.multiply(3.0)).add(sideStep), 1.2);
    }

    private void performSprintZigzag(Mob entity) {
        if (entity.getTarget() == null) return;
        Location selfLoc = entity.getLocation();
        Vector toTarget = entity.getTarget().getLocation().toVector().subtract(selfLoc.toVector()).normalize();
        Vector sideVec = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();
        double wave = Math.sin(entity.getTicksLived() * 0.4) * 2.5;
        entity.getPathfinder().moveTo(selfLoc.clone().add(toTarget.multiply(4.0).add(sideVec.multiply(wave))), 2.2);
    }
}
