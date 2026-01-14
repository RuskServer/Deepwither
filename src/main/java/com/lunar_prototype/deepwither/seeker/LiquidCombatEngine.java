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

    private Vector lastPlayerVelocity = new Vector(0, 0, 0);

    private static final String[] ACTIONS = {"ATTACK", "EVADE", "BAITING", "COUNTER", "OBSERVE", "RETREAT", "BURST_DASH", "ORBITAL_SLIDE"};

    public BanditDecision think(String version, BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        // バージョンごとの推論へ（内部は全てintとfloatで完結）
        return switch (version) {
            case "v3" -> thinkV3Optimized(context,brain,bukkitEntity);
            case "v2" -> thinkV2Optimized(context,brain,bukkitEntity);
            default   -> thinkV1Optimized(context,brain,bukkitEntity);
        };
    }

    /**
     * thinkV1の全機能を維持しつつ、量子化とループ最適化を適用した軽量版
     */
    private BanditDecision thinkV1Optimized(BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        // 1. 基本数値の量子化 (double -> float)
        float hpStress = 1.0f - ((float) context.entity.hp_pct / 100.0f);
        float enemyDist = 20.0f;
        float currentDist = 20.0f;
        float predictedDist = 20.0f;
        Player targetPlayer = null;

        // 2. 最寄りの敵の探索 (Stream/Comparatorを廃止し、プリミティブなループへ)
        List<BanditContext.EnemyInfo> enemies = context.environment.nearby_enemies;
        if (!enemies.isEmpty()) {
            float minSafeDist = Float.MAX_VALUE;
            for (int i = 0; i < enemies.size(); i++) {
                BanditContext.EnemyInfo info = enemies.get(i);
                float d = (float) info.dist;
                if (d < minSafeDist) {
                    minSafeDist = d;
                    // info.playerInstance を保持 (後の計算用)
                    if (info.playerInstance instanceof Player p) {
                        targetPlayer = p;
                        enemyDist = d;
                    }
                }
            }
        }

        // 3. 攻撃切迫度の計算
        float attackImminence = (float) calculateAttackImminence(targetPlayer, (double) enemyDist, bukkitEntity);

        // 4. アドレナリンと緊急度の計算 (float演算)
        float urgency = (hpStress * 0.3f) + ((float) brain.adrenaline * 0.7f);
        if (attackImminence > 0.5f) urgency = 1.0f;
        if (urgency > 1.0f) urgency = 1.0f;

        // 5. 予測モデルの適用 (オブジェクト生成を抑えた座標計算)
        if (targetPlayer != null) {
            // 現在の距離
            currentDist = (float) bukkitEntity.getLocation().distance(targetPlayer.getLocation());

            // ターゲットの未来位置予測 (0.5秒後)
            Vector targetFuture = predictFutureLocationImproved(targetPlayer, 0.5);

            // 自分の未来位置をスタック上の変数で計算 (new Vector().add() を回避)
            Vector myVel = bukkitEntity.getVelocity();
            double myFutureX = bukkitEntity.getLocation().getX() + (myVel.getX() * 10);
            double myFutureY = bukkitEntity.getLocation().getY() + (myVel.getY() * 10);
            double myFutureZ = bukkitEntity.getLocation().getZ() + (myVel.getZ() * 10);

            // 予測距離の計算
            double dx = targetFuture.getX() - myFutureX;
            double dy = targetFuture.getY() - myFutureY;
            double dz = targetFuture.getZ() - myFutureZ;
            predictedDist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        // 6. 反射(Reflex)の更新
        float futureImminence = (float) calculateAttackImminence(targetPlayer, (double) predictedDist, bukkitEntity);
        float imminenceDelta = Math.max(0.0f, futureImminence - attackImminence);
        brain.reflex.update(futureImminence + (imminenceDelta * 2.0f), 1.0f);

        // 7. 脳内状態の更新 (CollectiveKnowledgeの量子化アクセス)
        float globalFear = 0.0f;
        if (targetPlayer != null) {
            // 直接取得メソッドがあればそれを使用、なければ既存マップから
            CollectiveKnowledge.PlayerTacticalProfile profile = CollectiveKnowledge.playerProfiles.get(targetPlayer.getUniqueId());
            if (profile != null) {
                globalFear = (float) profile.dangerLevel;
            }
        }
        float collectiveShock = (float) CollectiveKnowledge.globalFearBias;

        // リキッドニューロン群の更新
        brain.fear.update(1.0f, (globalFear * 0.5f) + (collectiveShock * 0.3f) + (hpStress > 0.5f || attackImminence > 0.6f ? 0.2f : 0.0f));
        brain.aggression.update((enemyDist < 10.0f ? 0.8f : 0.2f), (double) urgency);
        brain.tactical.update((enemyDist < 6.0f ? 1.0f : 0.3f), (double) (urgency * 0.5f));

        // 8. 士気の計算 (float版)
        brain.morale = (double) (brain.aggression.get() - (brain.fear.get() * (1.0f - (float) brain.composure * 0.3f))
                + (float) CollectiveKnowledge.globalAggressionBias - collectiveShock);

        // 9. 意思決定の解決 (既存メソッドへ繋ぐ)
        return resolveDecisionV1(brain, context, (double) enemyDist);
    }

    private BanditDecision resolveDecisionV1(LiquidBrain brain, BanditContext context, double enemyDist) {
        BanditDecision d = new BanditDecision();
        d.engine_version = "v1.0";
        d.decision = new BanditDecision.DecisionCore();
        d.movement = new BanditDecision.MovementPlan();
        d.communication = new BanditDecision.Communication();

        // 不満度(Frustration)の蓄積: 下がっているのに敵が近い＝ハメられている
        if (enemyDist < 6.0 && brain.morale < 0.3) {
            brain.frustration += 0.05;
        }

        d.reasoning = String.format("M:%.2f A:%.2f R:%.2f Ad:%.2f Fr:%.2f",
                brain.morale, brain.aggression.get(), brain.reflex.get(), brain.adrenaline, brain.frustration);

        // A. 逆上（AMBUSH）: 不満が冷静さを超えた時
        if (brain.frustration > brain.composure) {
            d.decision.action_type = "AMBUSH";
            d.movement.strategy = "SPRINT_ZIGZAG";
            d.decision.use_skill = "Four_consecutive_attacks";
            d.communication.voice_line = "ENOUGH OF THIS!";
            brain.frustration = 0;
            brain.adrenaline = 1;
            return d;
        }

        // B. 反射回避
        if (brain.reflex.get() > 0.8) {
            d.decision.action_type = "EVADE";
            d.movement.strategy = (brain.fear.get() > 0.5) ? "BACKSTEP" : "SIDESTEP";
            return d;
        }

        // C. 様子見（ハメ対策）
        if (brain.morale < 0.2 && enemyDist < 5.0) {
            d.decision.action_type = "OBSERVE";
            d.movement.strategy = "MAINTAIN_DISTANCE";
            return d;
        }

        // D. 通常行動
        if (brain.morale > 0.5) {
            d.decision.action_type = "ATTACK";
            d.movement.destination = "ENEMY";
        } else {
            d.decision.action_type = "RETREAT";
            d.movement.destination = "NEAREST_COVER";
        }

        return d;
    }

    private double calculateAttackImminence(Player player, double dist, Mob entity) {
        if (player == null) return 0.0;
        double score = 0.0;

        // 武器リーチ判定 (槍などは6m)
        double weaponReach = 3.5;
        String mainHand = player.getInventory().getItemInMainHand().getType().name().toLowerCase();
        if (mainHand.contains("spear") || mainHand.contains("needle") || mainHand.contains("trident")) weaponReach = 6.0;
        score += Math.max(0, 1.0 - (dist / weaponReach)) * 0.4;

        // 接近速度
        Vector relativeVelocity = player.getVelocity().subtract(entity.getVelocity());
        Vector toEntity = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
        if (relativeVelocity.dot(toEntity) > 0.2) score += 0.3;

        // エイムチェック
        if (player.getLocation().getDirection().dot(toEntity) > 0.98) score += 0.3;

        return Math.max(0.0, Math.min(1.0, score));
    }

    private Vector predictFutureLocationImproved(Player player, double seconds) {
        Vector currentLoc = player.getLocation().toVector();
        Vector currentVelocity = player.getVelocity();
        Vector acceleration = currentVelocity.clone().subtract(lastPlayerVelocity);
        lastPlayerVelocity = currentVelocity.clone();

        double stability = 1.0 / (1.0 + acceleration.lengthSquared() * 5.0);
        stability = Math.max(0.2, stability);

        double ticks = seconds * 20;
        Vector predictedMovement = currentVelocity.clone().multiply(ticks)
                .add(acceleration.clone().multiply(0.5 * Math.pow(ticks, 2)))
                .multiply(stability);

        return currentLoc.add(predictedMovement);
    }

    private BanditDecision thinkV2Optimized(BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        // 1. 基底となるV1ロジックの呼び出し (量子化版)
        BanditDecision d = thinkV1Optimized(context, brain, bukkitEntity);
        d.engine_version = "v3.1-Elastic-DSR";

        // --- [新理論実装] 脳の構造的再編 (DSR/AM-QL) ---
        // 思考を開始する前に、現在のアドレナリンやフラストレーションに基づき脳回路を組み替える
        brain.reshapeTopology();
        // 経験の消化（ニューロンの更新 & 疲労の代謝）もここで行い、最新の状態を反映
        brain.digestExperience();

        brain.updateTacticalAdvantage();
        float advantage = (float) brain.tacticalMemory.combatAdvantage;

        List<BanditContext.EnemyInfo> enemies = context.environment.nearby_enemies;
        if (enemies.isEmpty()) return d;

        // --- 1. 多角的ターゲッティング (プリミティブ・ループによる最適化) ---
        Entity currentTarget = bukkitEntity.getTarget();
        BanditContext.EnemyInfo bestTargetInfo = null;
        float maxScore = -999.0f;

        for (int i = 0; i < enemies.size(); i++) {
            BanditContext.EnemyInfo enemy = enemies.get(i);
            float score = 0.0f;

            score += (20.0f - (float) enemy.dist) * 1.0f; // 距離
            if (enemy.playerInstance instanceof Player p) {
                float hpRatio = (float) (p.getHealth() / p.getMaxHealth());
                score += (1.0f - hpRatio) * 15.0f; // 低HP優先
            }
            if (enemy.in_sight) score += 5.0f; // 視界
            if (currentTarget != null && enemy.playerInstance.getUniqueId().equals(currentTarget.getUniqueId())) {
                score += 8.0f; // ターゲット維持バイアス
            }

            if (score > maxScore) {
                maxScore = score;
                bestTargetInfo = enemy;
            }
        }

        // ターゲットの切り替え判定
        if (bestTargetInfo != null) {
            Player bestPlayer = (Player) bestTargetInfo.playerInstance;
            if (currentTarget == null || !bestPlayer.getUniqueId().equals(currentTarget.getUniqueId())) {
                bukkitEntity.setTarget(bestPlayer);
                d.reasoning += " | TGT_SWITCH:" + bestPlayer.getName();
                if (maxScore > 25.0f && brain.lastStateIdx != -1) {
                    // 新仕様: 学習更新にも疲労度（この場合は0）を考慮
                    brain.qTable.update(brain.lastStateIdx, 0, 0.1f, brain.lastStateIdx, 0.0f);
                }
            }
        }

        Player target = (Player) bukkitEntity.getTarget();
        if (target == null) return d;

        // 集合知プロファイル
        float globalFear = (float) CollectiveKnowledge.getDangerLevel(target.getUniqueId());
        String globalWeakness = CollectiveKnowledge.getGlobalWeakness(target.getUniqueId());

        float enemyDist = (float) bukkitEntity.getLocation().distance(target.getLocation());
        LiquidBrain.AttackPattern pattern = brain.enemyPatterns.get(target.getUniqueId());

        // --- 2. 敵のパターンマッチング & 自己同期 ---
        float patternMatchScore = 0.0f;
        if (pattern != null && pattern.sampleCount > 2) {
            long ticksSinceLast = bukkitEntity.getTicksLived() - pattern.lastAttackTick;
            float timingScore = Math.max(0.0f, 1.0f - Math.abs(ticksSinceLast - (float) pattern.averageInterval) / 20.0f);
            float distScore = Math.max(0.0f, 1.0f - Math.abs(enemyDist - (float) pattern.preferredDist) / 2.0f);
            patternMatchScore = (timingScore * 0.5f) + (distScore * 0.5f);
        }

        boolean isRecovering = (brain.selfPattern.averageInterval > 0 && (bukkitEntity.getTicksLived() - brain.selfPattern.lastAttackTick) < 15);

        // --- 3. Elastic Action Selection & 量子化状態パッキング ---
        float hpPct = (float) context.entity.hp_pct / 100.0f;
        int stateIdx = brain.qTable.packState(advantage, enemyDist, hpPct, isRecovering, enemies.size());

        int bestAIdx = -1;
        float bestExpectation = -999.0f;

        // シミュレーション回数 (冷静さによって分岐)
        int visionCount = (brain.composure > 0.7) ? 5 : (brain.composure > 0.3 ? 3 : 2);

        for (int i = 0; i < visionCount; i++) {
            // [新仕様] QTableから疲労度を加味した実質的なベストアクション候補を取得
            int candidateIdx = (i == 0) ? brain.qTable.getBestActionIdx(stateIdx, brain.fatigueMap)
                    : ThreadLocalRandom.current().nextInt(ACTIONS.length);

            // 未来期待値計算
            double expectation = evaluateTimeline(candidateIdx, brain, target, bukkitEntity, globalWeakness);

            // [Elastic] 活動電位疲労による期待値の動的減衰
            float fatigue = brain.fatigueMap[candidateIdx];
            expectation -= (fatigue * 2.0); // alpha=2.0

            // 戦術的分野：スパム防止 (Tactical Boredom)
            if (candidateIdx == brain.lastActionIdx) {
                String actionName = ACTIONS[candidateIdx];
                float penaltyMultiplier = (float) Math.pow(actionName.equals("BURST_DASH") || actionName.equals("OVERWHELM") ? 0.6 : 0.9, brain.actionRepeatCount);
                if (expectation > 0) expectation *= penaltyMultiplier;
                if (actionName.equals("BURST_DASH")) expectation -= (brain.actionRepeatCount * 1.5);
            }

            if (expectation > bestExpectation) {
                bestExpectation = (float) expectation;
                bestAIdx = candidateIdx;
            }
        }

        // 集合知バイアス (Epsilon)
        float epsilon = Math.min(0.6f, 0.1f + (brain.frustration * 0.4f) + (globalFear * 0.1f));
        if (ThreadLocalRandom.current().nextFloat() < epsilon) {
            bestAIdx = (globalWeakness.equals("CLOSE_QUARTERS") && ThreadLocalRandom.current().nextFloat() < 0.3f) ? 6 : ThreadLocalRandom.current().nextInt(ACTIONS.length);
            d.reasoning += " | Q:EXPLORING";
        }

        // --- 4. 戦術的分岐 & DSRによる行動補正 ---
        float reflexIntensity = (float) brain.reflex.get();
        String recommendedAction = ACTIONS[bestAIdx];

        if (brain.adrenaline > 0.85f && reflexIntensity > 0.7f) {
            // 【サージバイパス発動】 疲労を焼き切り本能(DSR経路)が上書き
            d.decision.action_type = "OVERWHELM";
            d.movement.strategy = "BURST_DASH";
            d.reasoning += " | DSR_BYPASS:SURGE";
        } else if (advantage < 0.3f || (globalFear > 0.8f && advantage < 0.5f)) {
            d.decision.action_type = recommendedAction.equals("RETREAT") ? "RETREAT" : "DESPERATE_DEFENSE";
            d.movement.strategy = d.decision.action_type.equals("RETREAT") ? "RETREAT" : "MAINTAIN_DISTANCE";
        } else if (patternMatchScore > 0.7f && brain.composure > 0.6 && !isRecovering && enemies.size() == 1) {
            d.decision.action_type = "COUNTER";
            d.movement.strategy = "SIDESTEP_COUNTER";
            d.decision.use_skill = "Counter_Stance";
        } else if (advantage > 0.7f || globalWeakness.equals("CLOSE_QUARTERS")) {
            d.decision.action_type = "OVERWHELM";
            d.movement.strategy = (enemies.size() > 1 || globalFear > 0.5f) ? "SPRINT_ZIGZAG" : "BURST_DASH";
            d.decision.use_skill = "Execution_Strike";
        } else {
            d.decision.action_type = recommendedAction;
            switch (recommendedAction) {
                case "EVADE" -> d.movement.strategy = "SIDESTEP";
                case "BURST_DASH" -> d.movement.strategy = "BURST_DASH";
                case "ORBITAL_SLIDE" -> d.movement.strategy = "ORBITAL_SLIDE";
                default -> d.movement.strategy = "MAINTAIN_DISTANCE";
            }
            // 疲労が選択に影響している場合のデバッグ
            if (brain.fatigueMap[bestAIdx] > 0.4f) d.reasoning += " | ELASTIC:FATIGUED";
        }

        // 最終更新処理
        if (bestAIdx == brain.lastActionIdx) {
            brain.actionRepeatCount++;
        } else {
            brain.actionRepeatCount = 0;
        }

        brain.lastStateIdx = stateIdx;
        brain.lastActionIdx = bestAIdx;
        applyMobilityRewards(bukkitEntity, brain, d, (double) enemyDist);

        return d;
    }

    private void applyMobilityRewards(Mob bukkitEntity, LiquidBrain brain, BanditDecision d, double currentDist) {
        if (brain.lastStateIdx < 0 || brain.lastActionIdx < 0) return;

        Player target = (Player) bukkitEntity.getTarget();
        if (target == null) return;

        float totalProcessReward = 0.0f;
        StringBuilder rewardDebug = new StringBuilder();

        // =========================================================
        // [新概念] 相関スケーラー: 信頼度・冷静さ・疲労を統合
        // =========================================================
        // 予測が当たるほど、冷静なほど報酬は増幅し、疲労している行動ほど減衰する
        float currentFatigue = brain.fatigueMap[brain.lastActionIdx];
        float correlationFactor = (brain.velocityTrust * 0.5f + brain.composure * 0.5f) * (1.0f - currentFatigue);

        // 1. 背後・側面奪取 (Flanking) - 信頼度が高いほど「読み勝ち」として高評価
        Vector toSelf = bukkitEntity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        Vector targetFacing = target.getLocation().getDirection();
        float dot = (float) toSelf.dot(targetFacing);

        if (dot < -0.3f) {
            // 信頼度と冷静さが高い時の背面取りは最大 +0.4f までスケーリング
            float rwd = 0.1f + (0.3f * correlationFactor);
            totalProcessReward += rwd;
            rewardDebug.append(String.format("FLANK(+%.2f) ", rwd));
        } else if (dot < 0.2f) {
            float rwd = 0.05f + (0.1f * correlationFactor);
            totalProcessReward += rwd;
            rewardDebug.append(String.format("SIDE(+%.2f) ", rwd));
        }

        // 2. リーチ・スペーシング (Spacing) - 冷静な距離維持を評価
        String weakness = CollectiveKnowledge.getGlobalWeakness(target.getUniqueId());
        if (weakness.equals("CLOSE_QUARTERS")) {
            if (currentDist < 2.5) {
                // インファイト維持は冷静さ(Composure)をより重視
                float rwd = 0.2f * brain.composure;
                totalProcessReward += rwd;
                rewardDebug.append(String.format("STICKY(+%.2f) ", rwd));
            }
        } else {
            if (currentDist > 3.0 && currentDist < 5.0) {
                // 適切な距離維持。予測が安定(Trust)しているなら報酬アップ
                float rwd = 0.05f + (0.1f * brain.velocityTrust);
                totalProcessReward += rwd;
                rewardDebug.append(String.format("DIST(+%.2f) ", rwd));
            }
        }

        // 3. 視線誘導 (Baiting Success)
        if (brain.lastActionIdx == 2 && brain.composure > 0.8f) {
            // BAITINGは「相手をハメた」判定のため、現在の信頼度が高い(＝相手が予測通り動いた)なら高評価
            float rwd = 0.1f + (0.2f * brain.velocityTrust);
            totalProcessReward += rwd;
            rewardDebug.append(String.format("BAIT_WIN(+%.2f) ", rwd));
        }

        // =========================================================
        // [新概念] コンボ・チェーン評価 (Action Linkage)
        // =========================================================
        if (brain.secondLastActionIdx >= 0) {
            float comboBonus = 0.0f;

            // A. 追撃チェーン: BURST_DASH -> ATTACK (距離を一気に詰めて殴る)
            if (brain.secondLastActionIdx == 6 && brain.lastActionIdx == 0) { // 6:BURST_DASH, 0:ATTACK
                if (currentDist < 3.0) {
                    comboBonus += 0.25f * correlationFactor;
                    rewardDebug.append("CHASE_HIT ");
                }
            }

            // B. 回避反撃チェーン: EVADE -> COUNTER (避けてから即座に返す)
            if (brain.secondLastActionIdx == 1 && brain.lastActionIdx == 7) { // 1:EVADE, 7:COUNTER
                comboBonus += 0.3f * brain.velocityTrust; // 予測が当たっていればさらに高評価
                rewardDebug.append("EVADE_COUNTER ");
            }

            // C. 撹乱パルクール: ORBITAL_SLIDE -> BURST_DASH (カオス軌道からの急接近)
            if (brain.secondLastActionIdx == 5 && brain.lastActionIdx == 6) { // 5:ORBITAL, 6:BURST
                comboBonus += 0.2f * brain.composure;
                rewardDebug.append("CHAOS_DASH ");
            }

            totalProcessReward += comboBonus;
        }

        // 4. 量子化Q-Tableへの反映
        if (totalProcessReward > 0) {
            // 相関学習により、同じ「背面取り」でも状況が悪い（疲労中、予測ミス中）ならQ値の伸びを自動抑制
            brain.qTable.update(brain.lastStateIdx, brain.lastActionIdx, totalProcessReward, brain.lastStateIdx, currentFatigue);
            d.reasoning += " | RWD: " + rewardDebug.toString();
        }

        // 次のターンのために行動履歴をシフト
        brain.secondLastActionIdx = brain.lastActionIdx;
        brain.secondLastStateIdx = brain.lastStateIdx;
    }

    private double evaluateTimeline(int actionIdx, LiquidBrain brain, Player target, Mob self, String globalWeakness) {
        float qValue = brain.qTable.getQ(brain.lastStateIdx, actionIdx, brain.fatigueMap[brain.lastActionIdx]);
        float score = qValue;

        // 現在の座標と時間
        Vector currentTargetLoc = target.getLocation().toVector();
        long currentTick = self.getTicksLived();

        // =========================================================
        // A. 予測精度の学習 (Reality Check)
        // =========================================================
        if (brain.lastPredictedLocation != null && (currentTick - brain.lastPredictionTick) >= 15) {
            double errorDistance = currentTargetLoc.distance(brain.lastPredictedLocation);

            if (errorDistance < 2.0) {
                brain.velocityTrust = Math.min(1.0f, brain.velocityTrust + 0.1f);
            } else {
                brain.velocityTrust = Math.max(0.0f, brain.velocityTrust - 0.15f);
            }
            brain.lastPredictedLocation = null;
        }

        // =========================================================
        // [新実装] B. 視線（Gaze）とFOVによる「死角」の量子化
        // =========================================================
        Vector targetLookDir = target.getLocation().getDirection(); // プレイヤーの視線
        Vector toSelf = self.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();

        // ドット積により、プレイヤーがどれくらい自分を直視しているか算出 (1.0 = 直視, -1.0 = 背面)
        float attentionLevel = (float) targetLookDir.dot(toSelf);

        // FOV判定（一般的な90度を基準）。視界外なら不意打ちチャンス
        boolean isVisible = attentionLevel > 0.707; // cos(45度)

        // =========================================================
        // C. 信頼度と視線で補正された未来位置予測
        // =========================================================
        Vector enemyVel = target.getVelocity();

        // 相手が背を向けて逃げている（attentionLevel < 0）なら、予測をより前方に伸ばす
        double directionBias = (attentionLevel < -0.3f) ? 1.4 : 1.0;
        double predictionScale = 20.0 * brain.velocityTrust * directionBias;

        double predX = target.getLocation().getX() + (enemyVel.getX() * predictionScale);
        double predZ = target.getLocation().getZ() + (enemyVel.getZ() * predictionScale);
        double predDist = Math.sqrt(Math.pow(predX - self.getLocation().getX(), 2) + Math.pow(predZ - self.getLocation().getZ(), 2));

        if (brain.lastPredictedLocation == null) {
            brain.lastPredictedLocation = new Vector(predX, target.getLocation().getY(), predZ);
            brain.lastPredictionTick = currentTick;
        }

        // =========================================================
        // D. 既存の同期・リズムロジック
        // =========================================================
        long ticksSinceLastSelf = self.getTicksLived() - brain.selfPattern.lastAttackTick;
        float selfRhythmScore = 0.0f;
        if (brain.selfPattern.averageInterval > 0) {
            selfRhythmScore = Math.max(0.0f, 1.0f - Math.abs(ticksSinceLastSelf - (float)brain.selfPattern.averageInterval) / 20.0f);
        }

        LiquidBrain.AttackPattern pattern = brain.enemyPatterns.get(target.getUniqueId());
        boolean enemyLikelyToAttack = false;
        if (pattern != null) {
            long ticksSinceEnemyLast = self.getTicksLived() - pattern.lastAttackTick;
            enemyLikelyToAttack = Math.abs(ticksSinceEnemyLast - (long)pattern.averageInterval) < 10;
        }

        // =========================================================
        // E. インデックス別・多次元スコアリング
        // =========================================================
        switch (actionIdx) {
            case 0 -> { // ATTACK
                score += selfRhythmScore * 0.4f;
                if (predDist < (3.0 * brain.velocityTrust)) score += 0.3f;
                // 視界外（背後）からの攻撃を高く評価
                if (!isVisible) score += 0.5f;
            }
            case 1 -> { // EVADE
                if (enemyLikelyToAttack) score += 0.6f;
                // 見られている時ほど回避の重要度アップ
                if (isVisible) score += 0.2f;
            }
            case 2 -> { // BAITING (おとり)
                if (brain.velocityTrust < 0.4f) score += 0.5f;
            }
            case 4 -> { // OBSERVE (観察)
                if (brain.velocityTrust < 0.3f) score += 0.6f;
            }
            case 5 -> { // RETREAT (撤退)
                if (brain.velocityTrust > 0.7f && predDist < 5.0) score += 0.4f;
            }
            case 6 -> { // BURST_DASH
                // ガン逃げ（背を向けている）相手には、一気に距離を詰める
                if (attentionLevel < -0.5f) score += 0.9f;
                if (predDist > 5.0 && brain.velocityTrust > 0.5f) score += 0.4f;
                if (globalWeakness.equals("CLOSE_QUARTERS")) score += 0.3f;
            }
            case 7 -> { // ORBITAL_SLIDE (回り込み)
                // プレイヤーが凝視しているなら、視線を外すようにスライド
                if (attentionLevel > 0.8f) score += 0.7f;
                if (brain.velocityTrust > 0.6f) score += 0.5f;
            }
            case 3 -> { // COUNTER
                if (enemyLikelyToAttack && selfRhythmScore > 0.5f) score += 0.8f;
            }
        }

        return (double) score;
    }

    private BanditDecision thinkV3Optimized(BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        BanditDecision d = new BanditDecision();
        d.engine_version = "v3.1-System-Breaker-Quantized";

        List<BanditContext.EnemyInfo> enemies = context.environment.nearby_enemies;
        if (enemies.isEmpty()) return d;

        // 1. 多角的ターゲッティング (プリミティブ・ループによる最適化済み)
        BanditContext.EnemyInfo bestTargetInfo = selectBestTargetV3(enemies, bukkitEntity.getTarget());
        if (bestTargetInfo == null) return d;
        Player target = (Player) bestTargetInfo.playerInstance;
        bukkitEntity.setTarget(target);

        // 2. 基本パラメータの量子化
        float hpStress = 1.0f - ((float) context.entity.hp_pct / 100.0f);
        float enemyDist = (float) bestTargetInfo.dist;
        brain.updateTacticalAdvantage();
        float advantage = (float) brain.tacticalMemory.combatAdvantage;

        // サージ判定（量子化された frustration と adrenaline を使用）
        boolean isSurging = (brain.frustration > 0.9f && brain.adrenaline > 0.8f);

        // 3. FOV計算の最適化 (acosを避け、ドット積を直接使用)
        // ターゲットの正面方向と自分へのベクトルの重なり具合
        Vector targetLook = target.getLocation().getDirection().normalize();
        Vector toSelf = bukkitEntity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        float dotProduct = (float) targetLook.dot(toSelf);
        // dotProduct: 1.0=正面, 0.0=真横(90度), -1.0=真後ろ

        String globalWeakness = CollectiveKnowledge.getGlobalWeakness(target.getUniqueId());
        float globalFear = (float) CollectiveKnowledge.getDangerLevel(target.getUniqueId());

        boolean isRec = (bukkitEntity.getTicksLived() - brain.selfPattern.lastAttackTick) < 15;

        // 4. 量子化状態パッキング (512状態へ圧縮)
        int stateIdx = brain.qTable.packState(advantage, enemyDist, 1.0f - hpStress, isRec, enemies.size());

        // 5. 多次元推論 (Multiverse Reasoning - 配列インデックスで超高速化)
        int visionCount = isSurging ? 5 : (brain.composure > 0.7f ? 3 : (brain.composure > 0.3f ? 2 : 1));
        int bestActionIdx = 4; // Default: OBSERVE
        float maxExpectation = -Float.MAX_VALUE;

        // 6. 意思決定の適用
        //applyTacticalBranchV3(d, ACTIONS[bestActionIdx], (double)advantage, (double)globalFear, isSurging, globalWeakness, enemies.size());

        // 7. カオス注入 (軽量ベクトル生成)
        if (ThreadLocalRandom.current().nextFloat() < 0.2f) {
            d.movement.jitter_vector = new Vector(ThreadLocalRandom.current().nextFloat()-0.5f, 0, ThreadLocalRandom.current().nextFloat()-0.5f).multiply(0.4f);
        }

        // 8. 記録と報酬処理
        brain.lastStateIdx = stateIdx;
        brain.lastActionIdx = bestActionIdx;
        applyV3ProcessRewardsOptimized(brain, d, dotProduct, isSurging, enemyDist);

        // デバッグ情報
        d.reasoning += String.format(" | MV:%d(%s) | DOT:%.2f", visionCount, ACTIONS[bestActionIdx], dotProduct);
        if (isSurging) d.reasoning += " | !!! SURGE !!!";

        return d;
    }

    /**
     * プロセス報酬 (量子化版)
     */
    private void applyV3ProcessRewardsOptimized(LiquidBrain brain, BanditDecision d, float dotProduct, boolean isSurging, float dist) {
        if (brain.lastStateIdx < 0 || brain.lastActionIdx < 0) return;

        float processReward = 0.0f;
        StringBuilder debugRwd = new StringBuilder();

        // 1. 【FOVハック報酬】ドット積による死角維持判定
        if (dotProduct > 0.10f && dotProduct < 0.70f) {
            processReward += 0.15f;
            debugRwd.append("VISUAL_SHADOW(+0.15) ");
        } else if (dotProduct < -0.5f) { // 真後ろ付近
            processReward += 0.25f;
            debugRwd.append("BACKSTAB_POS(+0.25) ");
        }

        // 2. 【サージ最適化】
        if (isSurging && brain.lastActionIdx == 6 && dist < 4.0f) {
            processReward += 0.3f;
            debugRwd.append("SURGE_DRIVE(+0.3) ");
        }

        // 3. 【スペーシング】
        if (dist >= 3.0f && dist <= 5.0f) {
            processReward += 0.05f;
            debugRwd.append("SPACING(+0.05) ");
        }

        if (processReward > 0) {
            //brain.qTable.update(brain.lastStateIdx, brain.lastActionIdx, processReward, brain.lastStateIdx);
            d.reasoning += " | RWD: " + debugRwd.toString();
        }
    }

    /**
     * v3専用：多角的ターゲット選定ロジック (量子化最適化版)
     * 距離、HP、視覚、粘着バイアスを総合的に評価して最適な敵を抽出する
     */
    private BanditContext.EnemyInfo selectBestTargetV3(List<BanditContext.EnemyInfo> enemies, Entity currentTarget) {
        BanditContext.EnemyInfo bestTarget = null;
        float maxScore = -Float.MAX_VALUE;

        // UUIDの取得回数を減らすため、現在のターゲットIDをキャッシュ
        UUID currentId = (currentTarget != null) ? currentTarget.getUniqueId() : null;

        for (int i = 0; i < enemies.size(); i++) {
            BanditContext.EnemyInfo enemy = enemies.get(i);
            if (!(enemy.playerInstance instanceof Player p)) continue;

            float score = 0.0f;
            float dist = (float) enemy.dist;

            // A. 距離スコア (20m以内。近接戦ほど価値が高い。係数1.5)
            score += (20.0f - dist) * 1.5f;

            // B. 処刑バイアス (HPが低い敵を執拗に追う)
            // p.getHealth() / p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() を推奨
            float hpRatio = (float) (p.getHealth() / p.getMaxHealth());
            score += (1.0f - hpRatio) * 15.0f;

            // C. 視覚情報 (見えている敵を優先)
            if (enemy.in_sight) score += 5.0f;

            // D. 粘着バイアス (ターゲットが頻繁に変わる「迷い」を防止)
            if (currentId != null && p.getUniqueId().equals(currentId)) {
                score += 8.0f;
            }

            // --- スコア更新 ---
            if (score > maxScore) {
                maxScore = score;
                bestTarget = enemy;
            }
        }
        return bestTarget;
    }
}