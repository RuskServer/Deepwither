package com.lunar_prototype.deepwither.seeker;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import java.util.Comparator;

public class LiquidCombatEngine {

    /**
     * コンテキストと脳の状態を受け取り、意思決定を行う
     */
    public BanditDecision think(BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        double hpStress = 1.0 - (context.entity.hp_pct / 100.0);
        double enemyDist = 20.0;
        Player targetPlayer = null;

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

        double attackImminence = calculateAttackImminence(targetPlayer, enemyDist);

        // --- 1. アドレナリンによるUrgencyのブースト ---
        // アドレナリンが高いほど、環境変化への反応速度（粘性）が極限まで上がる
        double urgency = (hpStress * 0.3) + (brain.adrenaline * 0.7);
        if (attackImminence > 0.5) urgency = 1.0;
        urgency = Math.min(1.0, urgency);

        // --- 2. 各パラメーターの更新 ---
        brain.reflex.update(attackImminence, urgency);

        // 士気(Morale)の計算：攻撃性と恐怖の差分
        brain.morale = brain.aggression.get() - (brain.fear.get() * (1.0 - brain.composure * 0.3));

        brain.aggression.update((enemyDist < 10 ? 0.8 : 0.2), urgency);
        brain.fear.update((hpStress > 0.5 || attackImminence > 0.6 ? 1.0 : 0.0), urgency);
        brain.tactical.update((enemyDist < 6 ? 1.0 : 0.3), urgency * 0.5);

        return resolveDecision(brain, context, enemyDist);
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

    private BanditDecision resolveDecision(LiquidBrain brain, BanditContext context, double enemyDist) {
        BanditDecision d = new BanditDecision();
        d.decision = new BanditDecision.DecisionCore();
        d.movement = new BanditDecision.MovementPlan();

        double agg = brain.aggression.get();
        double fear = brain.fear.get();
        double ref = brain.reflex.get();
        double morale = brain.morale;

        d.reasoning = String.format("M:%.2f A:%.2f R:%.2f Ad:%.2f", morale, agg, ref, brain.adrenaline);

        // --- 戦略：ハメ殺し対策の「様子見（OBSERVE）」 ---
        // 士気が低く、かつ敵が近い（槍の間合い）場合、あえて突っ込まず距離を維持する
        if (morale < 0.2 && enemyDist < 5.0) {
            d.decision.action_type = "OBSERVE";
            d.movement.strategy = "MAINTAIN_DISTANCE";
            d.communication.voice_line = "I'm not falling for that...";
            return d;
        }

        // --- 反射回避 ---
        if (ref > 0.8) {
            d.decision.action_type = "EVADE";
            d.movement.strategy = (fear > 0.5) ? "BACKSTEP" : "SIDESTEP";
            return d;
        }

        // --- 通常行動 ---
        if (morale > 0.5) {
            d.decision.action_type = "ATTACK";
            d.movement.destination = "ENEMY";
            // アドレナリン全開ならバースト攻撃
            if (brain.adrenaline > 0.8) d.decision.use_skill = "BurstDash";
        } else {
            d.decision.action_type = "RETREAT";
            d.movement.destination = "NEAREST_COVER";
        }

        return d;
    }
}