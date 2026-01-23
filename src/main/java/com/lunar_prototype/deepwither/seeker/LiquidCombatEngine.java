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
            case "v2" -> thinkV2Optimized(context,brain,bukkitEntity);
            default   -> thinkV1Optimized(context,brain,bukkitEntity);
        };
    }

    /**
     * Rust 側の Q-Table (512状態) に適合するように、現在の状況を 0-511 の整数に圧縮する
     */
    private int packStateForRust(double advantage, double dist, float hp, boolean recovering, int enemyCount) {
        int bits = 0;
        bits |= (advantage > 0.6 ? 2 : (advantage > 0.4 ? 1 : 0)) << 7; // 優位性 (2bit)
        bits |= (dist < 3 ? 0 : (dist < 7 ? 1 : 2)) << 5;              // 距離 (2bit)
        bits |= (hp < 0.3 ? 0 : (hp < 0.7 ? 1 : 2)) << 3;              // 体力 (2bit)
        bits |= (recovering ? 1 : 0) << 2;                             // 回復中 (1bit)
        bits |= (Math.min(enemyCount, 3)) ;                            // 敵の数 (2bit)
        return bits & 0x1FF; // 511以下に収める
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
        d.engine_version = "v3.3-Astro-Interception";

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
        float[] contextData = new float[5];
        contextData[0] = (float) advantage;
        contextData[1] = (float) enemyDist;
        contextData[2] = (float) context.entity.hp_pct / 100.0f;
        contextData[3] = isRecovering ? 1.0f : 0.0f;
        contextData[4] = (float) enemies.size();

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
                candidateIdx = brain.think(contextData);
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

        if (prevTrust > brain.velocityTrust) {
            float surprise = (prevTrust - brain.velocityTrust);
            // 驚きが 0.3 を超えると、脳が「GAS相」へ強制遷移。リミッター解除。
            if (surprise > 0.3f) {
                brain.systemTemperature += surprise * 2.0f;
                d.reasoning += " | METABOLIC_BURST!";
            }
        }

        // --- 4. 戦術的分岐 & DSRによる行動補正 ---
        String recommendedAction = ACTIONS[bestAIdx];
        float reflexIntensity = (float) brain.reflex.get();

        // =========================================================
        // [最優先] アドバンテージと温度に基づいた「相」の強制介入
        // =========================================================
        if (advantage < 0.2f) {
            // 【絶望的劣勢：EMERGENCY SOLID】
            // グリアが緊急抑制。脳を冷却し、生存のための最小・最適行動に固定する
            brain.systemTemperature *= 0.5f;
            d.decision.action_type = "RETREAT";
            d.movement.strategy = "BACKSTEP_COUNTER"; // カウンター付き回避で仕切り直し
            d.reasoning += " | EMERGENCY_SOLID_MODE";
        }
        else if (advantage > 0.8f && brain.systemTemperature > 1.0f) {
            // 【圧倒的優勢：HUNTING GAS】
            // 相手を完全に呑み込んでいる状態。リミッターを外して偏差ダッシュで仕留める
            d.decision.action_type = "OVERWHELM";
            d.movement.strategy = "INTERCEPTION_DASH";
            d.reasoning += " | HUNTING_PHASE(GAS_MAX)";
        }
        // =========================================================
        // [通常] 既存の adrenaline/advantage ロジック
        // =========================================================
        else if (brain.adrenaline > 0.85f && reflexIntensity > 0.7f) {
            d.decision.action_type = "OVERWHELM";
            d.movement.strategy = "BURST_DASH";
            d.reasoning += " | DSR_BYPASS:SURGE";
        }
        else if (advantage < 0.3f) {
            // 緩やかな劣勢
            d.decision.action_type = recommendedAction.equals("RETREAT") ? "RETREAT" : "DESPERATE_DEFENSE";
            d.movement.strategy = d.decision.action_type.equals("RETREAT") ? "RETREAT" : "MAINTAIN_DISTANCE";
        }
        else if (advantage > 0.7f || globalWeakness.equals("CLOSE_QUARTERS")) {
            // 緩やかな優勢
            d.decision.action_type = "OVERWHELM";
            d.movement.strategy = (enemies.size() > 1) ? "SPRINT_ZIGZAG" : "BURST_DASH";
        }
        else {
            // 標準的なQ学習ベースの選択
            d.decision.action_type = recommendedAction;
            switch (recommendedAction) {
                case "EVADE" -> d.movement.strategy = "SIDESTEP";
                case "BURST_DASH" -> d.movement.strategy = "BURST_DASH";
                case "ORBITAL_SLIDE" -> d.movement.strategy = "ORBITAL_SLIDE";
                default -> d.movement.strategy = "MAINTAIN_DISTANCE";
            }
            if (brain.fatigueMap[bestAIdx] > 0.4f) d.reasoning += " | ELASTIC:FATIGUED";
        }

        brain.lastActionIdx = bestAIdx;

        // [Surprise Boost] 予測が外れている時は報酬の反映強度を上げる
        applyMobilityRewards(bukkitEntity, brain, d, (double) enemyDist);

        brain.recordSnapshot(d.movement.strategy);

        return d;
    }

    private void applyMobilityRewards(Mob bukkitEntity, LiquidBrain brain, BanditDecision d, double currentDist) {
        if (brain.lastStateIdx < 0 || brain.lastActionIdx < 0) return;

        Player target = (Player) bukkitEntity.getTarget();
        if (target == null) return;

        float totalProcessReward = 0.0f;
        StringBuilder rewardDebug = new StringBuilder();

        // 相関スケーラー（Rustから取得した値を使用）
        float correlationFactor = (brain.velocityTrust * 0.5f + brain.composure * 0.5f) * (1.0f - brain.fatigueMap[brain.lastActionIdx]);

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
            if (currentDist < 2.5) totalProcessReward += 0.2f * brain.composure;
        } else if (currentDist > 3.0 && currentDist < 5.0) {
            totalProcessReward += 0.05f + (0.1f * brain.velocityTrust);
        }

        // =========================================================
        // [Rust 連携] 蓄積報酬としてセット。digestExperience() で Rust 側へ一括送信される
        // =========================================================
        if (totalProcessReward > 0) {
            brain.accumulatedReward += totalProcessReward;

            // デバッグ表示用に現在の温度をシミュレート（実際にはRust側で正確に計算される）
            d.reasoning += String.format(" | RWD:%s | TEMP:%.2f", rewardDebug.toString(), brain.systemTemperature);
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

        // 1. 【認知重み】 相手との「視線の交差」と「予測信頼度」
        Vector toSelf = self.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        brain.attentionLevel = (float) target.getLocation().getDirection().dot(toSelf);

        // 予測が当たっているほど冷静（優勢）、外れるほど動揺（劣勢）
        double cognitiveAdvantage = brain.velocityTrust;

        // 2. 【グリア・空間評価】 周囲の閉塞感 (Glia Spatial Mapping)
        // 背後に壁がある、または移動が制限されているとアドバンテージが急落する
        float gliaStress = brain.getGliaActivity(); // 0.0~1.0 (過剰興奮抑制レベル)
        double spatialFreedom = 1.0 - gliaStress;

        // 3. 【生命維持バイアス】 HPが減るほど「恐怖（Fear）」が重みを増す
        double myHpPct = self.getHealth() / self.getMaxHealth();
        double targetHpPct = target.getHealth() / target.getMaxHealth();

        // 4. 【ハメ判定】 短時間に連続でダメージを受けているか？ (Momentum Check)
        // LiquidBrainのselfPatternから直近の被弾間隔を参照
        boolean beingComboed = (System.currentTimeMillis() - brain.tacticalMemory.lastHitTime < 500);

        // --- ダイナミック・ウェイト (状況に応じて重みが変わる) ---
        double wHp = (myHpPct < 0.4) ? 0.5 : 0.2; // ピンチならHPを最優先で考える
        double wSpatial = 0.3; // 空間の広さは常に重要
        double wCognitive = 0.3; // 「読み切っているか」の比重
        double wCombo = beingComboed ? -0.4 : 0.0; // ハメられている間は一気に劣勢判定

        double currentSnapshot = (myHpPct * wHp) +
                (spatialFreedom * wSpatial) +
                (cognitiveAdvantage * wCognitive) +
                wCombo;

        // 転落をよりダイナミックに (反応速度の向上)
        float learningRate = (gliaStress > 0.5f) ? 0.5f : 0.2f;
        tacticalMemory.combatAdvantage = (tacticalMemory.combatAdvantage * (1.0 - learningRate)) + (currentSnapshot * learningRate);
    }

    private double evaluateTimeline(int actionIdx, LiquidBrain brain, Player target, Mob self, String globalWeakness) {
        float qValue = (float) brain.getNativeScore(actionIdx);
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
}