package com.lunar_prototype.deepwither.seeker;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import java.util.Comparator;

public class LiquidCombatEngine {

    /**
     * コンテキストと脳の状態を受け取り、意思決定を行う
     */
    public BanditDecision think(BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        // --- 1. 環境情報の正規化 (既存) ---
        double hpStress = 1.0 - (context.entity.hp_pct / 100.0);
        double enemyDist = 20.0;
        boolean hasSight = false;
        Player targetPlayer = null;

        if (!context.environment.nearby_enemies.isEmpty()) {
            // 最寄りの敵を取得
            BanditContext.EnemyInfo nearestInfo = context.environment.nearby_enemies.stream()
                    .min(Comparator.comparingDouble(e -> e.dist)).orElse(null);

            if (nearestInfo != null) {
                enemyDist = nearestInfo.dist;
                hasSight = nearestInfo.in_sight;

                // 物理的な回避計算のためにBukkitのPlayerオブジェクトを特定
                if (bukkitEntity.getTarget() instanceof Player) {
                    targetPlayer = (Player) bukkitEntity.getTarget();
                }
            }
        }

        // --- 2. 攻撃予兆 (Attack Imminence) の計算 ---
        // プレイヤーの「溜め」や「武器の振り」から、攻撃のタイミングを予測する
        double attackImminence = calculateAttackImminence(targetPlayer, enemyDist);

        // --- 3. 緊迫度 (Urgency) の計算 (拡張) ---
        // 攻撃が飛んできそうな瞬間、AIの「時間の流れ(粘性)」を極限まで速める
        double urgency = 0.0;
        if (hpStress > 0.7) urgency += 0.4;
        if (enemyDist < 5.0) urgency += 0.3;
        if (attackImminence > 0.5) urgency += 0.6; // 攻撃予兆は最優先の緊急事態
        urgency = Math.min(1.0, urgency);

        // --- 4. ニューロンの動적更新 ---

        // 反射ニューロン(reflex)の更新: 予兆があれば即座に1.0を目指す
        // baseDecayが非常に高い(0.3など)設定を想定し、瞬時に反応させる
        brain.reflex.update(attackImminence, urgency);

        // 攻撃・恐怖・戦術の更新 (既存ロジックを維持)
        double aggressionInput = (hasSight ? 0.6 : 0.0) + (1.0 - hpStress) * 0.4;
        if (enemyDist < 8.0) aggressionInput += 0.3;
        brain.aggression.update(aggressionInput, urgency);

        double fearInput = hpStress + (attackImminence * 0.4); // 攻撃予兆は恐怖も煽る
        brain.fear.update(fearInput, urgency);

        double coverAvail = (context.environment.nearest_cover != null) ? 0.8 : 0.0;
        double tacticalInput = (enemyDist > 5.0 && enemyDist < 15.0) ? 0.8 : 0.0;
        tacticalInput += coverAvail * 0.5;
        brain.tactical.update(tacticalInput, urgency * 0.5);

        brain.lastEnemyDist = enemyDist;

        // --- 5. 意思決定 ---
        return resolveDecision(brain, context);
    }

    /**
     * プレイヤーの行動から攻撃の「予兆」を数値化する
     */
    private double calculateAttackImminence(Player player, double dist) {
        if (player == null) return 0.0;

        double score = 0.0;

        // 1. 武器の間合いチェック (槍:ヴァリアント・スピア等を想定)
        // リーチが長い武器の場合、4~6mでの接近は非常に危険
        if (dist < 6.0 && player.isSprinting()) {
            score += 0.4;
        }

        // 2. 攻撃のクールダウンチェック
        // クールダウンが完了している(1.0)＝いつでも振れる状態
        if (player.getAttackCooldown() > 0.9) {
            score += 0.3;
        }

        // 3. 視線チェック
        // プレイヤーがこちらを真っ直ぐ見ているか
        if (score > 0) {
            score += 0.2; // 狙われている感覚
        }

        return Math.min(1.0, score);
    }

    private BanditDecision resolveDecision(LiquidBrain brain, BanditContext context) {
        BanditDecision d = new BanditDecision();
        d.decision = new BanditDecision.DecisionCore();
        d.movement = new BanditDecision.MovementPlan();
        d.communication = new BanditDecision.Communication();

        double agg = brain.aggression.get();
        double fear = brain.fear.get();
        double tac = brain.tactical.get();
        double ref = brain.reflex.get();

        // 意思決定の理由に反射値を追加
        d.reasoning = String.format("A:%.2f F:%.2f T:%.2f R:%.2f", agg, fear, tac, ref);

        // --- 回避行動の優先判定 ---
        if (ref > 0.8) {
            // 反射値が高い＝攻撃が来る！
            d.decision.action_type = "EVADE";
            d.decision.new_stance = "EVASIVE";

            // 恐怖が高いならバックステップ、戦術が高いならサイドステップを好む
            if (fear > tac) {
                d.movement.strategy = "BACKSTEP";
                d.movement.destination = "NONE"; // Velocityで制御するため
            } else {
                d.movement.strategy = "SIDESTEP";
                d.movement.destination = "NONE";
            }
            d.communication.voice_line = "Watch out!";
            return d;
        }

        // --- 以下、既存の攻撃/戦術/逃走ロジック ---
        if (fear > agg && fear > 0.6) {
            d.decision.action_type = "RETREAT";
            d.decision.new_stance = "DEFENSIVE";
            d.movement.destination = "NEAREST_COVER";
            d.communication.voice_line = "Falling back!";
        } else if (tac > agg && tac > 0.4) {
            d.decision.action_type = "TACTICAL";
            d.decision.new_stance = "DEFAULT";
            d.movement.destination = context.environment.nearest_cover != null && context.environment.nearest_cover.dist < 2.0 ? "ENEMY" : "NEAREST_COVER";
            d.communication.voice_line = "Taking position.";
        } else {
            d.decision.action_type = "ATTACK";
            d.decision.new_stance = "AGGRESSIVE";
            d.movement.destination = "ENEMY";
            if (agg > 0.75) {
                d.decision.use_skill = "HeavySmash";
                d.communication.voice_line = "Die!";
            }
        }

        return d;
    }
}