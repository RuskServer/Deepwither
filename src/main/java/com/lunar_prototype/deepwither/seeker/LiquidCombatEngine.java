package com.lunar_prototype.deepwither.seeker;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;

public class LiquidCombatEngine {

    private static final String[] ACTIONS = {"ATTACK", "EVADE", "BAITING", "COUNTER", "OBSERVE", "RETREAT", "BURST_DASH", "ORBITAL_SLIDE"};

    /**
     * [v5-Native] Native Rebirth Think
     * Java ネイティブの Q-Learning エンジンを使用して行動を決定する。
     */
    public BanditDecision think(String version, BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        BanditDecision d = new BanditDecision();
        d.engine_version = "v5.0-Native-Rebirth";
        d.decision = new BanditDecision.DecisionCore();
        d.movement = new BanditDecision.MovementPlan();
        d.communication = new BanditDecision.Communication();

        // 1. 環境認識とJava側での戦術評価
        List<BanditContext.EnemyInfo> enemies = context.environment.nearby_enemies;
        float enemyDist = 20.0f;
        Player targetPlayer = null;

        if (!enemies.isEmpty()) {
            BanditContext.EnemyInfo nearest = enemies.stream()
                    .min(Comparator.comparingDouble(e -> e.dist))
                    .orElse(enemies.get(0));
            enemyDist = (float) nearest.dist;
            if (nearest.playerInstance instanceof Player p) {
                targetPlayer = p;
                if (bukkitEntity.getTarget() != p) bukkitEntity.setTarget(p);
            }
        }

        updateTacticalAdvantage(bukkitEntity, brain, brain.tacticalMemory);
        float advantage = (float) brain.tacticalMemory.combatAdvantage;

        // 2. 報酬の計算
        applyMobilityRewards(bukkitEntity, brain, d, (double) enemyDist);

        // 3. 入力ベクトルの作成 (5次元)
        boolean isRecovering = (brain.selfPattern.averageInterval > 0 && (bukkitEntity.getTicksLived() - brain.selfPattern.lastAttackTick) < 15);
        float[] inputs = new float[5];
        inputs[0] = advantage;
        inputs[1] = enemyDist;
        inputs[2] = (float) context.entity.hp_pct / 100.0f;
        inputs[3] = isRecovering ? 1.0f : 0.0f;
        inputs[4] = Math.min(enemies.size(), 5);

        // 敵の攻撃予測を更新
        if (targetPlayer != null) {
            brain.updateEnemyPrediction(targetPlayer.getUniqueId(), (double) enemyDist);
        }

        // [TQH-Bootstrap] 現在の状態を量子化
        int stateId = packState(advantage, enemyDist, inputs[2], isRecovering, enemies.size());
        brain.setCondition(stateId);

        // 4. Native Q-Engine による思考サイクル
        int actionIdx = brain.cycle(inputs);
        String actionName = ACTIONS[actionIdx];

        // 5. 決定の反映
        d.decision.action_type = actionName;

        switch (actionName) {
            case "ATTACK" -> d.movement.destination = "ENEMY";
            case "EVADE" -> d.movement.strategy = "SIDESTEP";
            case "RETREAT" -> d.movement.destination = "NEAREST_COVER";
            case "BAITING" -> d.movement.strategy = "MAINTAIN_DISTANCE";
            case "COUNTER" -> d.movement.strategy = "BACKSTEP"; // Actuator の strategy 名に合わせる
            case "BURST_DASH" -> d.movement.strategy = "BURST_DASH";
            case "ORBITAL_SLIDE" -> d.movement.strategy = "ORBITAL_SLIDE";
            case "OBSERVE" -> d.movement.strategy = "MAINTAIN_DISTANCE";
            default -> d.movement.strategy = "MAINTAIN_DISTANCE";
        }

        // 6. メタデータの付与
        d.reasoning = String.format("A:%s | S:%d | T:%.2f | F:%.2f | Adv:%.2f", 
                actionName, stateId, brain.systemTemperature, brain.frustration, advantage);

        brain.recordSnapshot(d.movement.strategy);

        return d;
    }

    private int packState(double advantage, double dist, float hp, boolean recovering, int enemyCount) {
        int bits = 0;
        bits |= (advantage > 0.6 ? 2 : (advantage > 0.4 ? 1 : 0)) << 7;
        bits |= (dist < 3.0 ? 0 : (dist < 7.0 ? 1 : 2)) << 5;
        bits |= (hp < 0.3 ? 0 : (hp < 0.7 ? 1 : 2)) << 3;
        bits |= (recovering ? 1 : 0) << 2;
        bits |= (Math.min(enemyCount, 3));
        return bits & 0x1FF;
    }

    private void applyMobilityRewards(Mob bukkitEntity, LiquidBrain brain, BanditDecision d, double currentDist) {
        if (brain.lastActionIdx < 0) return;

        Player target = (Player) bukkitEntity.getTarget();
        if (target == null) return;

        float totalProcessReward = 0.0f;
        float correlationFactor = brain.composure;

        Vector toSelf = bukkitEntity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        Vector targetFacing = target.getLocation().getDirection();
        float dot = (float) toSelf.dot(targetFacing);

        if (dot < -0.3f) {
            totalProcessReward += 0.1f + (0.3f * correlationFactor);
        } else if (dot < 0.2f) {
            totalProcessReward += 0.05f + (0.1f * correlationFactor);
        }

        if (currentDist > 3.0 && currentDist < 5.0) {
            totalProcessReward += 0.05f;
        }

        if (totalProcessReward > 0) {
            brain.accumulatedReward += totalProcessReward;
        }
    }

    public void updateTacticalAdvantage(Mob self, LiquidBrain brain, LiquidBrain.TacticalMemory tacticalMemory) {
        if (self.getTarget() == null) {
            tacticalMemory.combatAdvantage *= 0.9;
            return;
        }

        Player target = (Player) self.getTarget();

        Vector toSelf = self.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        brain.attentionLevel = (float) target.getLocation().getDirection().dot(toSelf);

        float gliaStress = brain.getGliaActivity(); 
        double spatialFreedom = 1.0 - gliaStress;

        double myHpPct = self.getHealth() / self.getMaxHealth();
        boolean beingComboed = (System.currentTimeMillis() - brain.tacticalMemory.lastHitTime < 500);

        double wHp = (myHpPct < 0.4) ? 0.5 : 0.2;
        double wSpatial = 0.3;
        double wCombo = beingComboed ? -0.4 : 0.0;

        double currentSnapshot = (myHpPct * wHp) + (spatialFreedom * wSpatial) + wCombo;

        float learningRate = (gliaStress > 0.5f) ? 0.5f : 0.2f;
        tacticalMemory.combatAdvantage = (tacticalMemory.combatAdvantage * (1.0 - learningRate)) + (currentSnapshot * learningRate);
    }
}