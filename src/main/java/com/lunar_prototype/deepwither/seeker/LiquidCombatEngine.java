package com.lunar_prototype.deepwither.seeker;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;

public class LiquidCombatEngine {

    private Vector lastPlayerVelocity = new Vector(0, 0, 0);

    /**
     * バージョン指定付きの思考メソッド
     */
    public BanditDecision think(String version, BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        switch (version) {
            case "v2":
                return thinkV2(context, brain, bukkitEntity);
            case "v1":
            default:
                return thinkV1(context, brain, bukkitEntity);
        }
    }

    /**
     * 現行の全機能を搭載したV1思考ロジック
     */
    private BanditDecision thinkV1(BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        double hpStress = 1.0 - (context.entity.hp_pct / 100.0);
        double enemyDist = 20.0;
        double currentDist = 20.0;
        double predictedDist = 20.0;
        Player targetPlayer = null;

        // 最寄りの敵の情報を取得
        if (!context.environment.nearby_enemies.isEmpty()) {
            BanditContext.EnemyInfo nearestInfo = context.environment.nearby_enemies.stream()
                    .min(Comparator.comparingDouble(e -> e.dist)).orElse(null);
            if (nearestInfo != null) {
                enemyDist = nearestInfo.dist;
                if (bukkitEntity.getTarget() instanceof Player) {
                    targetPlayer = (Player) bukkitEntity.getTarget();
                }
            }
        }

        double attackImminence = calculateAttackImminence(targetPlayer, enemyDist, bukkitEntity);

        // 1. アドレナリンと緊急度の計算
        double urgency = (hpStress * 0.3) + (brain.adrenaline * 0.7);
        if (attackImminence > 0.5) urgency = 1.0;
        urgency = Math.min(1.0, urgency);

        // 2. 予測モデル (カルマンフィルタ風) の適用
        if (targetPlayer != null) {
            currentDist = bukkitEntity.getLocation().distance(targetPlayer.getLocation());
            // 0.5秒後を予測
            Vector targetFuture = predictFutureLocationImproved(targetPlayer, 0.5);
            Vector myFuture = bukkitEntity.getLocation().toVector().add(bukkitEntity.getVelocity().multiply(10));
            predictedDist = myFuture.distance(targetFuture);
        }

        // 未来の予兆を計算して反射(Reflex)を更新
        double futureImminence = calculateAttackImminence(targetPlayer, predictedDist, bukkitEntity);
        double imminenceDelta = Math.max(0, futureImminence - attackImminence);
        brain.reflex.update(futureImminence + (imminenceDelta * 2.0), 1.0);

        // 3. 脳内状態の更新
        double globalFear = targetPlayer != null ? CollectiveKnowledge.playerDangerLevel.getOrDefault(targetPlayer.getUniqueId(), 0.0) : 0.0;
        double collectiveShock = CollectiveKnowledge.globalFearBias;

        brain.fear.update(1.0, (globalFear * 0.5) + (collectiveShock * 0.3) + (hpStress > 0.5 || attackImminence > 0.6 ? 0.2 : 0.0));
        brain.aggression.update((enemyDist < 10 ? 0.8 : 0.2), urgency);
        brain.tactical.update((enemyDist < 6 ? 1.0 : 0.3), urgency * 0.5);

        // 士気の計算
        brain.morale = brain.aggression.get() - (brain.fear.get() * (1.0 - brain.composure * 0.3)) + CollectiveKnowledge.globalAggressionBias - collectiveShock;

        return resolveDecisionV1(brain, context, enemyDist);
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
            brain.adrenaline = 1.0;
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

    private BanditDecision thinkV2(BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        // V1の物理層・リキッド演算をベースとして実行
        BanditDecision d = thinkV1(context, brain, bukkitEntity);
        d.engine_version = "v2.0-Tactical-Sync";

        // 戦術的優位性の更新（ヒット率や被弾率から計算）
        brain.updateTacticalAdvantage();
        double advantage = brain.tacticalMemory.combatAdvantage;

        if (bukkitEntity.getTarget() instanceof Player) {
            Player target = (Player) bukkitEntity.getTarget();
            LiquidBrain.AttackPattern pattern = brain.enemyPatterns.get(target.getUniqueId());

            // --- 1. 敵の時空間パターンマッチング (V2核心) ---
            double patternMatchScore = 0.0;
            if (pattern != null && pattern.sampleCount > 2) {
                long ticksSinceLast = bukkitEntity.getTicksLived() - pattern.lastAttackTick;
                double timingScore = Math.max(0, 1.0 - Math.abs(ticksSinceLast - pattern.averageInterval) / 20.0);
                double distScore = Math.max(0, 1.0 - Math.abs(context.environment.nearby_enemies.get(0).dist - pattern.preferredDist) / 2.0);
                patternMatchScore = (timingScore * 0.5) + (distScore * 0.5);
            }

            // --- 2. 自己同期ロジック (Self-Sync) ---
            // 自分の攻撃リズムを解析し、行動を補正する
            long ticksSinceSelfLast = bukkitEntity.getTicksLived() - brain.selfPattern.lastAttackTick;
            double selfAvgInterval = brain.selfPattern.averageInterval;

            boolean isRecovering = false; // 攻撃後の硬直中フラグ

            if (selfAvgInterval > 0) {
                // A. 【攻撃準備・踏み込み】: 次の自律攻撃/スキルが発動する直前 (5~10tick前)
                if (ticksSinceSelfLast > (selfAvgInterval - 10) && ticksSinceSelfLast < selfAvgInterval) {
                    // 有利または均衡時なら、攻撃を当てるために一気に詰める
                    if (advantage > 0.4) {
                        d.movement.strategy = "CHARGE";
                        d.reasoning += " | SELF_SYNC: PRE-ATTACK_CHARGE";
                    }
                }
                // B. 【攻撃後・離脱】: 攻撃直後 (15tick以内)
                else if (ticksSinceSelfLast >= 0 && ticksSinceSelfLast < 15) {
                    isRecovering = true;
                    // 攻撃した直後はバニラやスキルの硬直があるため、
                    // 棒立ちせず、ヒットアンドアウェイのために距離を取る
                    if (advantage < 0.8) { // 圧倒的優勢でなければ離脱を優先
                        d.movement.strategy = "POST_ATTACK_EVADE";
                        d.reasoning += " | SELF_SYNC: RECOVERY_MOVE";
                    }
                }
            }

            // --- 3. 戦術的分岐 (Tactical Branching) ---
            // 自己同期による移動補正を維持しつつ、戦術方針を決定

            // A. 【圧倒的劣勢】: 徹底防御 & 分析モード
            if (advantage < 0.3) {
                d.decision.action_type = "DESPERATE_DEFENSE";
                d.movement.strategy = "MAINTAIN_DISTANCE";
                brain.reflex.update(1.0, 0.9);
                d.reasoning += " | TACTICAL: DEFENSIVE";
            }
            // B. 【カウンター狙い】: 敵のパターンが読み切れている時（硬直中でない場合のみ）
            else if (patternMatchScore > 0.7 && brain.composure > 0.4 && !isRecovering) {
                d.decision.action_type = "COUNTER";
                d.movement.strategy = "SIDESTEP_COUNTER";
                d.decision.use_skill = "Counter_Stance";
                d.reasoning += " | TACTICAL: PATTERN_READ";
            }
            // C. 【圧倒的優勢】: 殲滅モード
            else if (advantage > 0.7) {
                d.decision.action_type = "OVERWHELM";
                // 殲滅モードなら硬直中もジグザグに追い回す
                d.movement.strategy = "SPRINT_ZIGZAG";
                d.decision.use_skill = "Execution_Strike";
                d.reasoning += " | TACTICAL: AGGRESSIVE";
            }
            // D. 【誘い受け】
            else if (advantage >= 0.4 && advantage <= 0.6 && brain.frustration < 0.3 && !isRecovering) {
                if (Math.random() < 0.2) {
                    d.decision.action_type = "BAITING";
                    d.movement.strategy = "NONE";
                    d.communication.voice_line = "Is that all you've got?";
                    d.reasoning += " | TACTICAL: BAITING";
                }
            }
        }

        return d;
    }
}