package com.lunar_prototype.deepwither.seeker;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class LiquidCombatEngine {

    private Vector lastPlayerVelocity = new Vector(0, 0, 0);

    /**
     * バージョン指定付きの思考メソッド
     */
    public BanditDecision think(String version, BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        switch (version) {
            case "v2":
                return thinkV2(context, brain, bukkitEntity);
            case "v3":
                return thinkV3(context,brain,bukkitEntity);
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
        // v2対応版：CollectiveKnowledge からプロファイルを参照する
        double globalFear = 0.0;
        if (targetPlayer != null) {
            // プレイヤーごとのプロファイルを取得
            CollectiveKnowledge.PlayerTacticalProfile profile = CollectiveKnowledge.playerProfiles.get(targetPlayer.getUniqueId());

            // プロファイルが存在すれば dangerLevel を取得、なければ 0.0
            if (profile != null) {
                globalFear = profile.dangerLevel;
            }
        }
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
        BanditDecision d = thinkV1(context, brain, bukkitEntity);
        d.engine_version = "v2.6-Multiverse-Light";

        brain.updateTacticalAdvantage();
        double advantage = brain.tacticalMemory.combatAdvantage;

        List<BanditContext.EnemyInfo> enemies = context.environment.nearby_enemies;
        if (enemies.isEmpty()) return d;

        // --- 1. 多角的ターゲッティング (動的評価モデル) ---
        Entity currentTarget = bukkitEntity.getTarget();
        BanditContext.EnemyInfo bestTargetInfo = null;
        double maxScore = -999.0;

        for (BanditContext.EnemyInfo enemy : enemies) {
            double score = 0.0;

            // A. 距離スコア (近いほど加点: 20mを基準)
            score += (20.0 - enemy.dist) * 1.0;

            // B. 低HP優先スコア (処刑ロジック: 弱っている敵を執拗に狙う)
            if (enemy.playerInstance instanceof Player p) {
                double hpRatio = p.getHealth() / p.getMaxHealth();
                score += (1.0 - hpRatio) * 15.0; // HPが低いほど最大15点加点
            }

            // C. 視界スコア
            if (enemy.in_sight) score += 5.0;

            // D. ターゲット維持バイアス (チャカチャカとターゲットが変わるのを防ぐ)
            if (currentTarget != null && enemy.playerInstance.getUniqueId().equals(currentTarget.getUniqueId())) {
                score += 8.0;
            }

            // E. ヘイトスコア (自分を攻撃した直後の敵への報復: 必要に応じてbrainから参照)

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

                // 【Q学習への報酬】適切なターゲット（弱っている敵など）を選んだことへのフィードバック
                if (maxScore > 25.0) { // 高い評価値のターゲットに切り替えた場合
                    brain.qTable.update(brain.lastStateKey, "TARGET_SELECT", 0.1, "TACTICAL_SWITCH");
                }
            }
        }

        Player target = (Player) bukkitEntity.getTarget();
        if (target == null) return d;

        // --- 【新規】集合知プロファイルの参照 ---
        // 影響が強すぎないよう、まずは「情報の取得」のみ
        double globalFear = CollectiveKnowledge.getDangerLevel(target.getUniqueId());
        String globalWeakness = CollectiveKnowledge.getGlobalWeakness(target.getUniqueId());

        double enemyDist = bukkitEntity.getLocation().distance(target.getLocation());
        LiquidBrain.AttackPattern pattern = brain.enemyPatterns.get(target.getUniqueId());

        // --- 2. 敵のパターンマッチング & 自己同期 ---
        // (既存ロジック継続)
        double patternMatchScore = 0.0;
        if (pattern != null && pattern.sampleCount > 2) {
            long ticksSinceLast = bukkitEntity.getTicksLived() - pattern.lastAttackTick;
            double timingScore = Math.max(0, 1.0 - Math.abs(ticksSinceLast - pattern.averageInterval) / 20.0);
            double distScore = Math.max(0, 1.0 - Math.abs(enemyDist - pattern.preferredDist) / 2.0);
            patternMatchScore = (timingScore * 0.5) + (distScore * 0.5);
        }

        long ticksSinceSelfLast = bukkitEntity.getTicksLived() - brain.selfPattern.lastAttackTick;
        boolean isRecovering = (brain.selfPattern.averageInterval > 0 && ticksSinceSelfLast < 15);

        // --- 3. 動的 Epsilon-Greedy (集合知によるバイアス) ---
        String currentStateKey = brain.qTable.getStateKey(advantage, enemyDist, isRecovering, enemies);

        String[] options = {"ATTACK", "EVADE", "BAITING", "COUNTER", "OBSERVE", "RETREAT", "BURST_DASH", "ORBITAL_SLIDE"};

        String recommendedAction = "OBSERVE";
        double bestExpectation = -999.0;

        // シミュレーション回数（軽量化のため最大3回）
        int visionCount = (brain.composure > 0.7) ? 3 : (brain.composure > 0.3 ? 2 : 1);

        for (int i = 0; i < visionCount; i++) {
            // 現在のQテーブルから上位の候補をピックアップ
            String candidate = (i == 0) ? brain.qTable.getBestAction(currentStateKey, options) : options[new Random().nextInt(options.length)];

            // --- 仮想世界での期待値計算 (Lightweight Simulation) ---
            double expectation = evaluateTimeline(candidate, brain, target, bukkitEntity,globalWeakness);

            if (expectation > bestExpectation) {
                bestExpectation = expectation;
                recommendedAction = candidate;
            }
        }

        d.reasoning += " | MV_VISION:" + visionCount + "(" + recommendedAction + ")";

        // 集合知の影響：仲間が殺されまくっている(globalFearが高い)なら、少し慎重に(Epsilon増)
        double epsilon = 0.1 + (brain.frustration * 0.4) + (globalFear * 0.1);
        epsilon = Math.min(0.6, epsilon); // 最大60%までに制限

        if (Math.random() < epsilon) {
            // 集合知に「弱点」が登録されている場合、探索中にその行動を少しだけ選びやすくする
            if (globalWeakness.equals("CLOSE_QUARTERS") && Math.random() < 0.3) {
                recommendedAction = "BURST_DASH";
                d.reasoning += " | Q:COLLECTIVE_HINT(CLOSE_QUARTERS)";
            } else {
                recommendedAction = options[new Random().nextInt(options.length)];
                d.reasoning += " | Q:EXPLORING(e:" + String.format("%.2f", epsilon) + ")";
            }
        } else {
            recommendedAction = brain.qTable.getBestAction(currentStateKey, options);
            d.reasoning += " | Q:BEST_" + recommendedAction;
        }

        // --- 4. 戦術的分岐 ---

        // A. 【圧倒的劣勢 or 群れの恐怖】
        // 自分の不利だけでなく、仲間の死(globalFear)も撤退判断の材料にする
        if (advantage < 0.3 || (globalFear > 0.8 && advantage < 0.5)) {
            d.decision.action_type = recommendedAction.equals("RETREAT") ? "RETREAT" : "DESPERATE_DEFENSE";
            d.movement.strategy = d.decision.action_type.equals("RETREAT") ? "RETREAT" : "MAINTAIN_DISTANCE";
            d.reasoning += " | TACTICAL: CAUTIOUS_BY_ANNIHILATION";
        }
        // B. 【カウンター狙い】
        else if (patternMatchScore > 0.7 && brain.composure > 0.6 && !isRecovering && enemies.size() == 1) {
            d.decision.action_type = "COUNTER";
            d.movement.strategy = "SIDESTEP_COUNTER";
            d.decision.use_skill = "Counter_Stance";
            d.reasoning += " | TACTICAL: PATTERN_READ";
        }
        // C. 【圧倒的優勢 or 弱点露呈】
        else if (advantage > 0.7 || globalWeakness.equals("CLOSE_QUARTERS")) {
            d.decision.action_type = "OVERWHELM";
            // 複数人か、あるいは相手が強い(Fearが高い)ならよりトリッキーに
            d.movement.strategy = (enemies.size() > 1 || globalFear > 0.5) ? "SPRINT_ZIGZAG" : "BURST_DASH";
            d.decision.use_skill = "Execution_Strike";
            d.reasoning += " | TACTICAL: EXPLOIT_WEAKNESS";
        }
        // D. 【均衡状態】
        else {
            d.decision.action_type = recommendedAction;
            // ... (既存の switch 文と同様の処理) ...
            switch (recommendedAction) {
                case "EVADE": d.movement.strategy = "SIDESTEP"; break;
                case "BURST_DASH": d.movement.strategy = "BURST_DASH"; break;
                case "ORBITAL_SLIDE": d.movement.strategy = "ORBITAL_SLIDE"; break;
                default: d.movement.strategy = "MAINTAIN_DISTANCE"; break;
            }
            d.reasoning += " | TACTICAL: Q_BALANCED";
        }

        brain.lastStateKey = currentStateKey;
        brain.lastActionType = d.decision.action_type;

        double dist = bukkitEntity.getLocation().distance(target.getLocation());

        applyMobilityRewards(bukkitEntity,brain,d,dist);

        return d;
    }

    private void applyMobilityRewards(Mob bukkitEntity, LiquidBrain brain, BanditDecision d, double currentDist) {
        if (brain.lastStateKey == null || brain.lastActionType == null) return;

        Player target = (Player) bukkitEntity.getTarget();
        if (target == null) return;

        double totalProcessReward = 0.0;
        StringBuilder rewardDebug = new StringBuilder();

        // 1. 背後・側面奪取（Flanking）
        // ターゲットの視線方向と、自分へのベクトルのドット積で判定
        Vector toSelf = bukkitEntity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        Vector targetFacing = target.getLocation().getDirection();
        double dot = toSelf.dot(targetFacing);

        if (dot < -0.3) { // 背後側
            totalProcessReward += 0.25;
            rewardDebug.append("FLANK(+0.25) ");
        } else if (dot < 0.2) { // 側面
            totalProcessReward += 0.1;
            rewardDebug.append("SIDE(+0.1) ");
        }

        // 2. リーチ・スペーシング（Spacing）
        // 集合知で「槍対策(CLOSE_QUARTERS)」が出ている場合、密着に加点
        String weakness = CollectiveKnowledge.getGlobalWeakness(target.getUniqueId());
        if (weakness.equals("CLOSE_QUARTERS")) {
            if (currentDist < 2.5) {
                totalProcessReward += 0.2;
                rewardDebug.append("STICKY(+0.2) ");
            }
        } else {
            // 通常は「付かず離れず」の維持に微加点
            if (currentDist > 3.0 && currentDist < 5.0) {
                totalProcessReward += 0.05;
                rewardDebug.append("DIST(+0.05) ");
            }
        }

        // 3. 視線誘導（Baiting Success）
        // BAITINGを選択中に敵が攻撃をミスした、あるいは敵を立ち止まらせたら加点
        if (brain.lastActionType.equals("BAITING") && brain.composure > 0.8) {
            totalProcessReward += 0.15;
            rewardDebug.append("BAIT_WIN(+0.15) ");
        }

        // Q-Tableへの反映
        if (totalProcessReward > 0) {
            brain.qTable.update(brain.lastStateKey, brain.lastActionType, totalProcessReward, "PROCESS");
            d.reasoning += " | RWD: " + rewardDebug.toString();
        }
    }

    private double evaluateTimeline(String action, LiquidBrain brain, Player target, Mob self, String globalWeakness) {
        double qValue = brain.qTable.getQValue(brain.lastStateKey, action);
        double score = qValue; // Q値をベースにする

        // 1. 【自己同期】自分の攻撃リズムとの適合性
        long ticksSinceLastSelf = self.getTicksLived() - brain.selfPattern.lastAttackTick;
        double selfRhythmScore = 0.0;
        if (brain.selfPattern.averageInterval > 0) {
            // 自分の理想的な攻撃間隔に近いほど「ATTACK」系への期待値を高める
            selfRhythmScore = Math.max(0, 1.0 - Math.abs(ticksSinceLastSelf - brain.selfPattern.averageInterval) / 20.0);
        }

        // 2. 【未来予測位置】ターゲットが1秒後(20ticks)にどこにいるか
        // ベクトルを用いて簡易的に予測：現在地 + (速度ベクトル * 20)
        Vector enemyVelocity = target.getVelocity();
        Location predictedLoc = target.getLocation().add(enemyVelocity.multiply(20));
        double predictedDist = self.getLocation().distance(predictedLoc);

        // 3. 【敵の未来行動予測】パターンスコアの反映
        LiquidBrain.AttackPattern pattern = brain.enemyPatterns.get(target.getUniqueId());
        boolean enemyLikelyToAttack = false;
        if (pattern != null) {
            long ticksSinceEnemyLast = self.getTicksLived() - pattern.lastAttackTick;
            // 敵の平均間隔に近づいていたら「敵が攻撃してくる世界線」と予測
            enemyLikelyToAttack = Math.abs(ticksSinceEnemyLast - pattern.averageInterval) < 10;
        }

        // --- アクション別・多次元スコアリング ---
        switch (action) {
            case "ATTACK":
                // 自分のリズムが整っており、かつ敵の予測位置が射程内なら高スコア
                score += selfRhythmScore * 0.4;
                if (predictedDist < 3.0) score += 0.3;
                // 敵が攻撃してきそうなら、あえて相打ちを狙うかどうかのQ値判断に任せる
                break;

            case "EVADE":
                // 敵が攻撃してくる可能性が高い世界線では、回避の期待値を大幅に上げる
                if (enemyLikelyToAttack) score += 0.6;
                // 自分が壁を背負っている予測位置なら、さらに加点
                break;

            case "BURST_DASH":
                // 敵の未来位置が遠ざかっているなら、追いかけるためのダッシュに価値が出る
                if (predictedDist > 5.0) score += 0.4;
                // 集合知の弱点が CLOSE_QUARTERS ならさらに加点
                if (globalWeakness.equals("CLOSE_QUARTERS")) score += 0.3;
                break;

            case "COUNTER":
                // 敵の攻撃タイミングと自分のリズムが合う「完璧な瞬間」を予見
                if (enemyLikelyToAttack && selfRhythmScore > 0.5) score += 0.8;
                break;
        }

        return score;
    }


    private BanditDecision thinkV3(BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        // 1. 基本変数の初期化 (thinkV1, V2の物理計算を統合)
        BanditDecision d = new BanditDecision();
        d.engine_version = "v3.0-System-Breaker";

        List<BanditContext.EnemyInfo> enemies = context.environment.nearby_enemies;
        if (enemies.isEmpty()) return d;

        // 2. 多角的ターゲッティング (インライン化による高速化)
        BanditContext.EnemyInfo bestTargetInfo = selectBestTargetV3(enemies, bukkitEntity.getTarget());
        if (bestTargetInfo == null) return d;
        Player target = (Player) bestTargetInfo.playerInstance;
        bukkitEntity.setTarget(target);

        // 3. パラメータ計算 & リキッド・ステート更新
        double hpStress = 1.0 - (context.entity.hp_pct / 100.0);
        double enemyDist = bestTargetInfo.dist;
        brain.updateTacticalAdvantage();
        double advantage = brain.tacticalMemory.combatAdvantage;

        // サージ判定（リミッター解除）
        boolean isSurging = (brain.frustration > 0.9 && brain.adrenaline > 0.8);

        // 4. システム・メタデータの計算（FOV, 予測位置, リズム）
        double viewAngle = calculateFOVAngle(target, bukkitEntity); // ターゲットの視界角
        String globalWeakness = CollectiveKnowledge.getGlobalWeakness(target.getUniqueId());
        double globalFear = CollectiveKnowledge.getDangerLevel(target.getUniqueId());

        long ticksSinceSelfLast = bukkitEntity.getTicksLived() - brain.selfPattern.lastAttackTick;
        boolean isRecovering = (brain.selfPattern.averageInterval > 0 && ticksSinceSelfLast < 15);

        // 5. 多次元推論 (Multiverse Reasoning)
        String currentStateKey = brain.qTable.getStateKey(advantage, enemyDist, isRecovering, enemies);
        String[] options = {"ATTACK", "EVADE", "BAITING", "COUNTER", "OBSERVE", "RETREAT", "BURST_DASH", "ORBITAL_SLIDE"};

        // 演算リソースの決定：サージ時は5並行、冷静なら3並行
        int visionCount = isSurging ? 5 : (brain.composure > 0.7 ? 3 : (brain.composure > 0.3 ? 2 : 1));
        String bestAction = "OBSERVE";
        double maxExpectation = -Double.MAX_VALUE;

        for (int i = 0; i < visionCount; i++) {
            // 初手はQ-Best、以降はランダム探索（思考実験）
            String candidate = (i == 0) ? brain.qTable.getBestAction(currentStateKey, options)
                    : options[ThreadLocalRandom.current().nextInt(options.length)];

            // 未来報酬評価 (evaluateTimelineV3)
            double expectation = evaluateV3Timeline(candidate, brain, target, bukkitEntity, viewAngle, isSurging);

            if (expectation > maxExpectation) {
                maxExpectation = expectation;
                bestAction = candidate;
            }
        }

        // 6. 意思決定の適用（戦術的分岐）
        // サージ中、または圧倒的優勢時は攻撃的に、それ以外はQ学習の判断を尊重
        applyTacticalBranchV3(d, bestAction, advantage, globalFear, isSurging, globalWeakness, enemies.size());

        // 7. 【システムの隙】カオス注入 & 報酬処理
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            d.movement.jitter_vector = new Vector(Math.random()-0.5, 0, Math.random()-0.5).multiply(0.4);
            d.reasoning += " | CHAOS";
        }

        // 8. 記録とプロセス報酬
        brain.lastStateKey = currentStateKey;
        brain.lastActionType = d.decision.action_type;
        applyV3ProcessRewards(brain, d, viewAngle, isSurging, enemyDist);

        d.reasoning += String.format(" | MV:%d(%s) | FOV:%.0f", visionCount, bestAction, viewAngle);
        if (isSurging) d.reasoning += " | !!! SURGE !!!";

        return d;
    }

    private double calculateFOVAngle(Player target, Mob self) {
        Vector targetLook = target.getLocation().getDirection().normalize();
        Vector toSelf = self.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, targetLook.dot(toSelf)))));
    }

    private void applyTacticalBranchV3(BanditDecision d, String action, double adv, double fear, boolean surge, String weakness, int enemyCount) {
        if (surge || adv > 0.8) {
            d.decision.action_type = "OVERWHELM";
            d.movement.strategy = (enemyCount > 1 || fear > 0.5) ? "SPRINT_ZIGZAG" : "BURST_DASH";
            d.decision.use_skill = "Execution_Strike";
        } else {
            d.decision.action_type = action;
            switch (action) {
                case "ORBITAL_SLIDE": d.movement.strategy = "CIRCLE_TO_BLINDSPOT"; break;
                case "EVADE": d.movement.strategy = "SIDESTEP"; break;
                default: d.movement.strategy = "MAINTAIN_DISTANCE"; break;
            }
        }
    }

    /**
     * v3専用：未来予測スコアリング
     */
    private double evaluateV3Timeline(String action, LiquidBrain brain, Player target, Mob self, double viewAngle, boolean isSurging) {
        double score = brain.qTable.getQValue(brain.lastStateKey, action);

        // A. 【死角報酬】プレイヤーの画面端（FOV 40~70度）を維持するアクションを高く評価
        if (viewAngle > 40 && viewAngle < 80) {
            if (action.equals("ORBITAL_SLIDE") || action.equals("EVADE")) score += 0.5;
        }

        // B. 【サージ・バイアス】リミッター解除時は「相打ち」を最高善とする
        if (isSurging) {
            if (action.equals("ATTACK") || action.equals("BURST_DASH")) score += 2.0;
            if (action.equals("RETREAT")) score -= 5.0; // 逃げは選択肢から消える
        }

        // C. 【カオス予測】予測不能な距離の詰め
        if (action.equals("BURST_DASH") && viewAngle > 90) { // 背後からの急接近
            score += 0.7;
        }

        return score;
    }

    private BanditContext.EnemyInfo selectBestTargetV3(List<BanditContext.EnemyInfo> enemies, Entity currentTarget) {
        BanditContext.EnemyInfo bestTarget = null;
        double maxScore = -Double.MAX_VALUE;

        for (BanditContext.EnemyInfo enemy : enemies) {
            if (!(enemy.playerInstance instanceof Player p)) continue;

            double score = 0.0;

            // A. 距離スコア (20m以内。近接戦ほど価値が高い)
            score += (20.0 - enemy.dist) * 1.5;

            // B. 処刑バイアス (HPが低い敵を執拗に追う)
            double hpRatio = p.getHealth() / p.getMaxHealth();
            score += (1.0 - hpRatio) * 15.0; // HP残りわずかな敵には強烈な加点

            // C. 視覚情報 (見えている敵を優先)
            if (enemy.in_sight) score += 5.0;

            // D. 粘着バイアス (ターゲットが頻繁に変わる「迷い」を防止)
            if (currentTarget != null && p.getUniqueId().equals(currentTarget.getUniqueId())) {
                score += 8.0;
            }

            // E. 逆恨みスコア (自分を直近で殴った敵へのヘイト：必要に応じて追加)

            if (score > maxScore) {
                maxScore = score;
                bestTarget = enemy;
            }
        }
        return bestTarget;
    }

    private void applyV3ProcessRewards(LiquidBrain brain, BanditDecision d, double viewAngle, boolean isSurging, double dist) {
        if (brain.lastStateKey == null || brain.lastActionType == null) return;

        double processReward = 0.0;
        StringBuilder debugRwd = new StringBuilder();

        // 1. 【FOVハック報酬】視界の端（45度〜80度）に居続けたら加点
        // プレイヤーにとって一番エイムがしづらく、消えやすい位置
        if (viewAngle > 40 && viewAngle < 85) {
            processReward += 0.15;
            debugRwd.append("VISUAL_SHADOW(+0.15) ");
        } else if (viewAngle > 120) {
            // 完全に背後を取っている場合
            processReward += 0.25;
            debugRwd.append("BACKSTAB_POS(+0.25) ");
        }

        // 2. 【サージ最適化】リミッター解除中に距離を詰めていたら加点
        if (isSurging && d.decision.action_type.equals("BURST_DASH") && dist < 4.0) {
            processReward += 0.3;
            debugRwd.append("SURGE_DRIVE(+0.3) ");
        }

        // 3. 【スペーシング】適切な間合いの維持
        if (dist >= 3.0 && dist <= 5.0) {
            processReward += 0.05;
            debugRwd.append("SPACING(+0.05) ");
        }

        // Q-Tableへの即時反映 (過程の重み付け)
        if (processReward > 0) {
            brain.qTable.update(brain.lastStateKey, brain.lastActionType, processReward, "PROCESS_STEP");
            d.reasoning += " | RWD: " + debugRwd.toString();
        }
    }
}