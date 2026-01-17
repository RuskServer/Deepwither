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
     * [TQH Integrated] thinkV1Optimized
     * 既存の予測・士気計算・疲労系を維持しつつ、システムの熱力学的状態を反映。
     */
    private BanditDecision thinkV1Optimized(BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        // 1. 基本数値の量子化 (既存)
        float hpStress = 1.0f - ((float) context.entity.hp_pct / 100.0f);
        float enemyDist = 20.0f;
        float currentDist = 20.0f;
        float predictedDist = 20.0f;
        Player targetPlayer = null;

        // 2. 最寄りの敵の探索 (既存)
        List<BanditContext.EnemyInfo> enemies = context.environment.nearby_enemies;
        if (!enemies.isEmpty()) {
            float minSafeDist = Float.MAX_VALUE;
            for (int i = 0; i < enemies.size(); i++) {
                BanditContext.EnemyInfo info = enemies.get(i);
                float d = (float) info.dist;
                if (d < minSafeDist) {
                    minSafeDist = d;
                    if (info.playerInstance instanceof Player p) {
                        targetPlayer = p;
                        enemyDist = d;
                    }
                }
            }
        }

        // 3. 攻撃切迫度の計算 (既存)
        float attackImminence = (float) calculateAttackImminence(targetPlayer, (double) enemyDist, bukkitEntity);

        // 4. アドレナリンと緊急度の計算 (既存)
        float urgency = (hpStress * 0.3f) + ((float) brain.adrenaline * 0.7f);
        if (attackImminence > 0.5f) urgency = 1.0f;
        if (urgency > 1.0f) urgency = 1.0f;

        // 5. 予測モデルの適用 (既存)
        if (targetPlayer != null) {
            currentDist = (float) bukkitEntity.getLocation().distance(targetPlayer.getLocation());
            Vector targetFuture = predictFutureLocationImproved(targetPlayer, 0.5);
            Vector myVel = bukkitEntity.getVelocity();
            double myFutureX = bukkitEntity.getLocation().getX() + (myVel.getX() * 10);
            double myFutureY = bukkitEntity.getLocation().getY() + (myVel.getY() * 10);
            double myFutureZ = bukkitEntity.getLocation().getZ() + (myVel.getZ() * 10);
            double dx = targetFuture.getX() - myFutureX;
            double dy = targetFuture.getY() - myFutureY;
            double dz = targetFuture.getZ() - myFutureZ;
            predictedDist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        // 6. 反射(Reflex)の更新 (TQH版 update に差し替え)
        float futureImminence = (float) calculateAttackImminence(targetPlayer, (double) predictedDist, bukkitEntity);
        float imminenceDelta = Math.max(0.0f, futureImminence - attackImminence);
        // [TQH] systemTemperature を引数に追加
        brain.reflex.update(futureImminence + (imminenceDelta * 2.0f), 1.0f, brain.systemTemperature);

        // 7. 脳内状態の更新
        float globalFear = 0.0f;
        if (targetPlayer != null) {
            CollectiveKnowledge.PlayerTacticalProfile profile = CollectiveKnowledge.playerProfiles.get(targetPlayer.getUniqueId());
            if (profile != null) globalFear = (float) profile.dangerLevel;
        }
        float collectiveShock = (float) CollectiveKnowledge.globalFearBias;

        // [TQH Integrated Updates]
        // 既存の入力計算を維持しつつ、systemTemperature による流動性を付与
        brain.fear.update(1.0f, (globalFear * 0.5f) + (collectiveShock * 0.3f) + (hpStress > 0.5f || attackImminence > 0.6f ? 0.2f : 0.0f), brain.systemTemperature);
        brain.aggression.update((enemyDist < 10.0f ? 0.8f : 0.2f), (double) urgency, brain.systemTemperature);
        brain.tactical.update((enemyDist < 6.0f ? 1.0f : 0.3f), (double) (urgency * 0.5f), brain.systemTemperature);

        // 8. 士気の計算 (既存ロジックを維持)
        brain.morale = (double) (brain.aggression.get() - (brain.fear.get() * (1.0f - (float) brain.composure * 0.3f))
                + (float) CollectiveKnowledge.globalAggressionBias - collectiveShock);

        // 9. [2026-01-12] Particle.FLASH 演出の準備
        // 温度状態に応じた色を取得（脳内で定義した getTQHFlashColor を想定）
        int[] rgb = brain.getTQHFlashColor();
        BanditDecision decision = resolveDecisionV1(brain, context, (double) enemyDist);

        // 決定オブジェクトにColorデータを注入（※BanditDecisionにColorフィールドがあると想定）
        // もしくはここで直接パーティクルを呼ぶ設計なら：
        // triggerTQHFlashEffect(bukkitEntity, Color.fromRGB(rgb[0], rgb[1], rgb[2]));

        return decision;
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
        d.engine_version = "v3.2-Surprise-Boost"; // バージョンアップ

        // --- [新理論実装] 脳の構造的再編 & 経験消化 ---
        brain.reshapeTopology();
        brain.digestExperience();

        updateTacticalAdvantage(bukkitEntity,brain,brain.tacticalMemory);
        float advantage = (float) brain.tacticalMemory.combatAdvantage;

        List<BanditContext.EnemyInfo> enemies = context.environment.nearby_enemies;
        if (enemies.isEmpty()) return d;

        // --- 1. 多角的ターゲッティング ---
        Entity currentTarget = bukkitEntity.getTarget();
        BanditContext.EnemyInfo bestTargetInfo = null;
        float maxScore = -999.0f;

        for (int i = 0; i < enemies.size(); i++) {
            BanditContext.EnemyInfo enemy = enemies.get(i);
            float score = 0.0f;
            score += (20.0f - (float) enemy.dist) * 1.0f;
            if (enemy.playerInstance instanceof Player p) {
                score += (1.0f - (float) (p.getHealth() / p.getMaxHealth())) * 15.0f;
            }
            if (enemy.in_sight) score += 5.0f;
            if (currentTarget != null && enemy.playerInstance.getUniqueId().equals(currentTarget.getUniqueId())) {
                score += 8.0f;
            }
            if (score > maxScore) {
                maxScore = score;
                bestTargetInfo = enemy;
            }
        }

        if (bestTargetInfo != null) {
            Player bestPlayer = (Player) bestTargetInfo.playerInstance;
            if (currentTarget == null || !bestPlayer.getUniqueId().equals(currentTarget.getUniqueId())) {
                bukkitEntity.setTarget(bestPlayer);
                d.reasoning += " | TGT_SWITCH:" + bestPlayer.getName();
            }
        }

        Player target = (Player) bukkitEntity.getTarget();
        if (target == null) return d;

        // 集合知プロファイル
        float globalFear = (float) CollectiveKnowledge.getDangerLevel(target.getUniqueId());
        String globalWeakness = CollectiveKnowledge.getGlobalWeakness(target.getUniqueId());
        float enemyDist = (float) bukkitEntity.getLocation().distance(target.getLocation());

        // =========================================================
        // [新概念] B. 予測誤差（Surprise）による学習ブースト
        // =========================================================
        float prevTrust = brain.velocityTrust;
        // evaluateTimeline内で reality check が走り、trustが更新される
        // 行動選択の前に「今の予測がどれだけ外れているか」をスコアリングに反映
        double expectationBase = evaluateTimeline(brain.lastActionIdx, brain, target, bukkitEntity, globalWeakness);

        float surpriseScore = 0.0f;
        if (prevTrust > brain.velocityTrust) {
            // 信頼度が下がった＝「自分の予測が裏切られた」
            surpriseScore = (prevTrust - brain.velocityTrust);
            brain.frustration += surpriseScore * 0.5f; // 驚きを不満（ストレス）に変換
            d.reasoning += String.format(" | SURPRISE(+%.2f)", surpriseScore);
        }

        // --- 3. Elastic Action Selection & 量子化状態パッキング ---
        boolean isRecovering = (brain.selfPattern.averageInterval > 0 && (bukkitEntity.getTicksLived() - brain.selfPattern.lastAttackTick) < 15);
        float hpPct = (float) context.entity.hp_pct / 100.0f;
        int stateIdx = brain.qTable.packState(advantage, enemyDist, hpPct, isRecovering, enemies.size());

        int bestAIdx = -1;
        float bestExpectation = -999.0f;

        // 冷静さ（Composure）が高いほど深く考え、不満（Frustration）が高いほど探索回数を増やす
        int visionCount = (brain.composure > 0.7) ? 5 : (brain.composure > 0.3 ? 3 : 2);
        if (brain.frustration > 0.5f) visionCount += 2; // ストレス時は「何か別の手」を必死に探す

        for (int i = 0; i < visionCount; i++) {
            // 探索率（Epsilon）: 不満度が高いほど、既存のベストアクションを無視してランダムな手を試す
            float currentEpsilon = Math.min(0.8f, 0.1f + (brain.frustration * 0.6f));

            int candidateIdx;
            if (i == 0 && ThreadLocalRandom.current().nextFloat() > currentEpsilon) {
                candidateIdx = brain.qTable.getBestActionIdx(stateIdx, brain.fatigueMap);
            } else {
                candidateIdx = ThreadLocalRandom.current().nextInt(ACTIONS.length);
            }

            double expectation = evaluateTimeline(candidateIdx, brain, target, bukkitEntity, globalWeakness);

            // [Elastic] 疲労による減衰
            expectation -= (brain.fatigueMap[candidateIdx] * 2.0);

            // 予測誤差が大きい時、未知の行動（疲労していない行動）にボーナス
            if (surpriseScore > 0.2f) {
                expectation += (1.0f - brain.fatigueMap[candidateIdx]) * surpriseScore * 2.0f;
            }

            if (expectation > bestExpectation) {
                bestExpectation = (float) expectation;
                bestAIdx = candidateIdx;
            }
        }

        // 最終的な探索フラグの付与
        if (ThreadLocalRandom.current().nextFloat() < (brain.frustration * 0.4f)) {
            d.reasoning += " | Q:EXPLORING_BY_STRESS";
        }

        // --- 4. 戦術的分岐 & DSRによる行動補正 ---
        String recommendedAction = ACTIONS[bestAIdx];
        float reflexIntensity = (float) brain.reflex.get();

        if (brain.adrenaline > 0.85f && reflexIntensity > 0.7f) {
            d.decision.action_type = "OVERWHELM";
            d.movement.strategy = "BURST_DASH";
            d.reasoning += " | DSR_BYPASS:SURGE";
        } else if (advantage < 0.3f) {
            d.decision.action_type = recommendedAction.equals("RETREAT") ? "RETREAT" : "DESPERATE_DEFENSE";
            d.movement.strategy = d.decision.action_type.equals("RETREAT") ? "RETREAT" : "MAINTAIN_DISTANCE";
        } else if (advantage > 0.7f || globalWeakness.equals("CLOSE_QUARTERS")) {
            d.decision.action_type = "OVERWHELM";
            d.movement.strategy = (enemies.size() > 1) ? "SPRINT_ZIGZAG" : "BURST_DASH";
        } else {
            d.decision.action_type = recommendedAction;
            switch (recommendedAction) {
                case "EVADE" -> d.movement.strategy = "SIDESTEP";
                case "BURST_DASH" -> d.movement.strategy = "BURST_DASH";
                case "ORBITAL_SLIDE" -> d.movement.strategy = "ORBITAL_SLIDE";
                default -> d.movement.strategy = "MAINTAIN_DISTANCE";
            }
            if (brain.fatigueMap[bestAIdx] > 0.4f) d.reasoning += " | ELASTIC:FATIGUED";
        }

        // インデックス更新と学習報酬の適用
        if (bestAIdx == brain.lastActionIdx) brain.actionRepeatCount++;
        else brain.actionRepeatCount = 0;

        brain.lastStateIdx = stateIdx;
        brain.lastActionIdx = bestAIdx;

        // [Surprise Boost] 予測が外れている時は報酬の反映強度を上げる
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
        // [相関スケーラー] 信頼度・冷静さ・疲労を統合 (既存仕様)
        // =========================================================
        float currentFatigue = brain.fatigueMap[brain.lastActionIdx];
        float correlationFactor = (brain.velocityTrust * 0.5f + brain.composure * 0.5f) * (1.0f - currentFatigue);

        // 1. 背後・側面奪取 (Flanking)
        Vector toSelf = bukkitEntity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        Vector targetFacing = target.getLocation().getDirection();
        float dot = (float) toSelf.dot(targetFacing);

        if (dot < -0.3f) {
            float rwd = 0.1f + (0.3f * correlationFactor);
            totalProcessReward += rwd;
            rewardDebug.append(String.format("FLANK(+%.2f) ", rwd));
        } else if (dot < 0.2f) {
            float rwd = 0.05f + (0.1f * correlationFactor);
            totalProcessReward += rwd;
            rewardDebug.append(String.format("SIDE(+%.2f) ", rwd));
        }

        // 2. リーチ・スペーシング (Spacing)
        String weakness = CollectiveKnowledge.getGlobalWeakness(target.getUniqueId());
        if (weakness.equals("CLOSE_QUARTERS")) {
            if (currentDist < 2.5) {
                float rwd = 0.2f * brain.composure;
                totalProcessReward += rwd;
                rewardDebug.append(String.format("STICKY(+%.2f) ", rwd));
            }
        } else if (currentDist > 3.0 && currentDist < 5.0) {
            float rwd = 0.05f + (0.1f * brain.velocityTrust);
            totalProcessReward += rwd;
            rewardDebug.append(String.format("DIST(+%.2f) ", rwd));
        }

        // 3. 視線誘導 (Baiting Success)
        if (brain.lastActionIdx == 2 && brain.composure > 0.8f) {
            float rwd = 0.1f + (0.2f * brain.velocityTrust);
            totalProcessReward += rwd;
            rewardDebug.append(String.format("BAIT_WIN(+%.2f) ", rwd));
        }

        // =========================================================
        // [Action Linkage] コンボ・チェーン評価 (既存仕様)
        // =========================================================
        if (brain.secondLastActionIdx >= 0) {
            float comboBonus = 0.0f;
            if (brain.secondLastActionIdx == 6 && brain.lastActionIdx == 0 && currentDist < 3.0) { // BURST -> ATTACK
                comboBonus += 0.25f * correlationFactor;
                rewardDebug.append("CHASE_HIT ");
            }
            if (brain.secondLastActionIdx == 1 && brain.lastActionIdx == 7) { // EVADE -> COUNTER
                comboBonus += 0.3f * brain.velocityTrust;
                rewardDebug.append("EVADE_COUNTER ");
            }
            if (brain.secondLastActionIdx == 5 && brain.lastActionIdx == 6) { // ORBITAL -> BURST
                comboBonus += 0.2f * brain.composure;
                rewardDebug.append("CHAOS_DASH ");
            }
            totalProcessReward += comboBonus;
        }

        // =========================================================
        // [TQH Core] 熱力学的Q更新と冷却（結晶化）
        // =========================================================
        if (totalProcessReward > 0) {
            // Q値の更新とTD誤差（驚き）の取得
            // 良い動きができた＝予測が当たった、あるいは良い発見をした
            float tdError = brain.qTable.updateTQH(brain.lastStateIdx, brain.lastActionIdx, totalProcessReward, brain.lastStateIdx, currentFatigue);

            // 【冷却】立ち回りの成功は系を冷却し、現在のトポロジー（脳構造）を「固体」として固定する
            // 0.45fは冷却係数。報酬が多いほどシステムは冷徹(SOLID)になる。
            brain.systemTemperature -= (totalProcessReward * 0.45f);

            // 自然放熱とのバランスを取り、最低値をクランプ
            if (brain.systemTemperature < 0.0f) brain.systemTemperature = 0.0f;

            d.reasoning += String.format(" | RWD:%s | TEMP:%.2f", rewardDebug.toString(), brain.systemTemperature);

            // [2026-01-12] 報酬獲得の瞬間に冷却色のFLASHをトリガーするための相転移チェック
            brain.reshapeTopology();
        }

        // 履歴シフト
        brain.secondLastActionIdx = brain.lastActionIdx;
        brain.secondLastStateIdx = brain.lastStateIdx;
    }

    /**
     * 戦術的優位性の更新
     * 判定を厳しくし、膠着状態やハメ状態を即座に「劣勢」と判定するように改良
     */
    public void updateTacticalAdvantage(Mob self, LiquidBrain brain, LiquidBrain.TacticalMemory tacticalMemory) {
        if (self.getTarget() == null || !(self.getTarget() instanceof Player)) {
            tacticalMemory.combatAdvantage *= 0.95; // ターゲット喪失時は徐々に減衰
            return;
        }

        Player target = (Player) self.getTarget();

        // --- [ここが重要] 視線計算のロジック ---
        Vector targetLookDir = target.getLocation().getDirection(); // 敵が見ている方向
        Vector toSelf = self.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();

        // ドット積で「視線の重なり」を算出
        brain.attentionLevel = (float) targetLookDir.dot(toSelf);

        // FOV約90度（cos45度 ≒ 0.707）を基準に可視判定
        brain.isVisibleFromEnemy = brain.attentionLevel > 0.707;

        // 1. ヒット・回避率 (基本性能)
        double offense = (double) tacticalMemory.myHits / Math.max(1, tacticalMemory.myHits + tacticalMemory.myMisses);
        double defense = (double) tacticalMemory.avoidedHits / Math.max(1, tacticalMemory.takenHits + tacticalMemory.avoidedHits);

        // 2. 生命力収支 (HP差によるプレッシャー)
        // 判定を厳しく: HPが半分以下になると急激にアドバンテージを失う
        double myHpPct = self.getHealth() / self.getMaxHealth();
        double targetHpPct = target.getHealth() / target.getMaxHealth();
        double hpAdvantage = myHpPct / (myHpPct + targetHpPct + 0.01);

        // 3. ポジショニング (視線と距離の相関)
        // 相手が自分を見ていない(attention < 0) ほど優位、かつ距離が近いほどその価値が高い
        double dist = self.getLocation().distance(target.getLocation());
        double distFactor = Math.exp(-dist / 10.0); // 遠いとポジションの価値が薄れる
        double positionAdvantage = ((1.0 - brain.attentionLevel) / 2.0) * distFactor;

        // 4. 予測精度 (Reality Checkの結果)
        // 自分の予測が当たっている＝相手を完全にコントロール下に置いている
        double predictionAdvantage = brain.velocityTrust;

        // --- 加重合成 (Weight Matrix) ---
        // HP差を最重視 (35%)、予測精度とポジションで「攻めの勢い」を測る
        double currentSnapshot = (offense * 0.15) +
                (defense * 0.10) +
                (hpAdvantage * 0.35) +
                (positionAdvantage * 0.20) +
                (predictionAdvantage * 0.20);

        // 以前の値との平滑化
        // 係数を0.3に上げることで、魔法の連撃を受けた際の「優勢から劣勢への転落」を早める
        tacticalMemory.combatAdvantage = (tacticalMemory.combatAdvantage * 0.7) + (currentSnapshot * 0.3);

        // 劣勢時のSurpriseトリガーへのフィードバック
        if (tacticalMemory.combatAdvantage < 0.3) {
            brain.frustration += 0.05f; // 劣勢が続くとイライラが募る
        }
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
        updateTacticalAdvantage(bukkitEntity,brain,brain.tacticalMemory);
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