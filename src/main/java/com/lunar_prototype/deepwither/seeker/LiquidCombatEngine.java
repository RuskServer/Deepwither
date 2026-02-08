package com.lunar_prototype.deepwither.seeker;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class LiquidCombatEngine {

    private static final String[] ACTIONS = {"ATTACK", "EVADE", "BAITING", "COUNTER", "OBSERVE", "RETREAT", "BURST_DASH", "ORBITAL_SLIDE"};

    /**
     * [Refactored] Singularity API Base Think
     * バージョンに関係なく、Dark-Singularity API (v3) を使用して行動を決定する。
     */
    public BanditDecision think(String version, BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        BanditDecision d = new BanditDecision();
        d.engine_version = "v3.5-Singularity-Native";
        d.decision = new BanditDecision.DecisionCore();
        d.movement = new BanditDecision.MovementPlan();
        d.communication = new BanditDecision.Communication();

        // 1. 環境認識とJava側での戦術評価
        List<BanditContext.EnemyInfo> enemies = context.environment.nearby_enemies;
        float enemyDist = 20.0f;
        Player targetPlayer = null;

        if (!enemies.isEmpty()) {
            // 最寄りの敵を探す
            BanditContext.EnemyInfo nearest = enemies.stream()
                    .min(Comparator.comparingDouble(e -> e.dist))
                    .orElse(enemies.get(0));
            enemyDist = (float) nearest.dist;
            if (nearest.playerInstance instanceof Player p) {
                targetPlayer = p;
                if (bukkitEntity.getTarget() != p) bukkitEntity.setTarget(p);
            }
        }
        
        // 戦術的優位性(Advantage)の更新
        updateTacticalAdvantage(bukkitEntity, brain, brain.tacticalMemory);
        float advantage = (float) brain.tacticalMemory.combatAdvantage;

        // 2. 報酬の計算 (前回の行動に対する評価)
        // これを cycle() の前に呼ぶことで、API に前回の行動の結果(報酬)を渡す
        applyMobilityRewards(bukkitEntity, brain, d, (double) enemyDist);

        // 3. 入力ベクトルの作成 (5次元)
        boolean isRecovering = (brain.selfPattern.averageInterval > 0 && (bukkitEntity.getTicksLived() - brain.selfPattern.lastAttackTick) < 15);
        float[] inputs = new float[5];
        inputs[0] = advantage;                                      // 戦況優位性
        inputs[1] = enemyDist;                                      // 敵との距離
        inputs[2] = (float) context.entity.hp_pct / 100.0f;         // 自身のHP
        inputs[3] = isRecovering ? 1.0f : 0.0f;                     // クールダウン中か
        inputs[4] = Math.min(enemies.size(), 5);                    // 敵の数

        // [TQH-Bootstrap] 現在の状態を量子化し、APIのハミルトニアン規則を有効化する
        int stateId = packState(advantage, enemyDist, inputs[2], isRecovering, enemies.size());
        brain.setCondition(stateId);

        // 4. Singularity API による思考サイクル (学習 + 推論)
        int actionIdx = brain.cycle(inputs);
        String actionName = ACTIONS[actionIdx];

        // 5. 決定の反映
        d.decision.action_type = actionName;
        
        // 行動ごとの移動戦略マッピング
        switch (actionName) {
            case "ATTACK" -> d.movement.destination = "ENEMY";
            case "EVADE" -> d.movement.strategy = "SIDESTEP";
            case "RETREAT" -> d.movement.destination = "NEAREST_COVER";
            case "BAITING" -> d.movement.strategy = "MAINTAIN_DISTANCE";
            case "COUNTER" -> d.movement.strategy = "BACKSTEP_COUNTER";
            case "BURST_DASH" -> d.movement.strategy = "BURST_DASH";
            case "ORBITAL_SLIDE" -> d.movement.strategy = "ORBITAL_SLIDE";
            case "OBSERVE" -> d.movement.strategy = "MAINTAIN_DISTANCE";
            default -> d.movement.strategy = "MAINTAIN_DISTANCE";
        }

        // 6. メタデータの付与 (デバッグ・演出用)
        d.reasoning = String.format("A:%s | S:%d | T:%.2f | F:%.2f | Adv:%.2f", 
                actionName, stateId, brain.systemTemperature, brain.frustration, advantage);
        
        // 脳の状態スナップショット保存
        brain.recordSnapshot(d.movement.strategy);

        return d;
    }
    
    /**
     * [BanditKnowledgeBase Consistent] 状態空間の量子化 (0-511)
     */
    private int packState(double advantage, double dist, float hp, boolean recovering, int enemyCount) {
        int bits = 0;
        bits |= (advantage > 0.6 ? 2 : (advantage > 0.4 ? 1 : 0)) << 7; // Advantage (2bit)
        bits |= (dist < 3.0 ? 0 : (dist < 7.0 ? 1 : 2)) << 5;           // Distance (2bit)
        bits |= (hp < 0.3 ? 0 : (hp < 0.7 ? 1 : 2)) << 3;               // HP (2bit)
        bits |= (recovering ? 1 : 0) << 2;                              // Recovering (1bit)
        bits |= (Math.min(enemyCount, 3));                              // Enemies (2bit)
        return bits & 0x1FF;
    }

    private void applyMobilityRewards(Mob bukkitEntity, LiquidBrain brain, BanditDecision d, double currentDist) {
        if (brain.lastActionIdx < 0) return;

        Player target = (Player) bukkitEntity.getTarget();
        if (target == null) return;

        float totalProcessReward = 0.0f;
        
        // 相関スケーラー: 冷静なときほど報酬を正しく評価しやすい
        float correlationFactor = brain.composure;

        // 1. 背後・側面奪取 (Flanking)
        Vector toSelf = bukkitEntity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        Vector targetFacing = target.getLocation().getDirection();
        float dot = (float) toSelf.dot(targetFacing);

        if (dot < -0.3f) {
            float rwd = 0.1f + (0.3f * correlationFactor);
            totalProcessReward += rwd;
        } else if (dot < 0.2f) {
            float rwd = 0.05f + (0.1f * correlationFactor);
            totalProcessReward += rwd;
        }

        // 2. リーチ・スペーシング (Spacing)
        String weakness = CollectiveKnowledge.getGlobalWeakness(target.getUniqueId());
        if (weakness.equals("CLOSE_QUARTERS")) {
            if (currentDist < 2.5) totalProcessReward += 0.2f * brain.composure;
        } else if (currentDist > 3.0 && currentDist < 5.0) {
            totalProcessReward += 0.05f;
        }
        
        // API に蓄積 (cycle() で回収される)
        if (totalProcessReward > 0) {
            brain.accumulatedReward += totalProcessReward;
        }
    }

    /**
     * [v3.3] メタ認知型・戦術優位性評価
     */
    public void updateTacticalAdvantage(Mob self, LiquidBrain brain, LiquidBrain.TacticalMemory tacticalMemory) {
        if (self.getTarget() == null) {
            tacticalMemory.combatAdvantage *= 0.9;
            return;
        }

        Player target = (Player) self.getTarget();

        // 1. 【認知重み】 相手との「視線の交差」
        Vector toSelf = self.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        brain.attentionLevel = (float) target.getLocation().getDirection().dot(toSelf);

        // 2. 【グリア・空間評価】 APIからのグリア活性度 (抑制レベル)
        float gliaStress = brain.getGliaActivity(); 
        double spatialFreedom = 1.0 - gliaStress;

        // 3. 【生命維持バイアス】 HP
        double myHpPct = self.getHealth() / self.getMaxHealth();
        
        // 4. 【ハメ判定】
        boolean beingComboed = (System.currentTimeMillis() - brain.tacticalMemory.lastHitTime < 500);

        // --- ダイナミック・ウェイト ---
        double wHp = (myHpPct < 0.4) ? 0.5 : 0.2;
        double wSpatial = 0.3;
        double wCombo = beingComboed ? -0.4 : 0.0;

        double currentSnapshot = (myHpPct * wHp) +
                (spatialFreedom * wSpatial) +
                wCombo;

        // 学習率
        float learningRate = (gliaStress > 0.5f) ? 0.5f : 0.2f;
        tacticalMemory.combatAdvantage = (tacticalMemory.combatAdvantage * (1.0 - learningRate)) + (currentSnapshot * learningRate);
    }
}