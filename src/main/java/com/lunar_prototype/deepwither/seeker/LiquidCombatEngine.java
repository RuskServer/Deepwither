package com.lunar_prototype.deepwither.seeker;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;

public class LiquidCombatEngine {

    /**
     * コンテキストと脳の状態を受け取り、意思決定を行う
     */
    public BanditDecision think(BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        double hpStress = 1.0 - (context.entity.hp_pct / 100.0);
        double enemyDist = 20.0;
        double currentDist = 20.0;
        double predictedDist = 20.0;
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

        double attackImminence = calculateAttackImminence(targetPlayer,enemyDist,bukkitEntity);

        // --- 1. アドレナリンによるUrgencyのブースト ---
        // アドレナリンが高いほど、環境変化への反応速度（粘性）が極限まで上がる
        double urgency = (hpStress * 0.3) + (brain.adrenaline * 0.7);
        if (attackImminence > 0.5) urgency = 1.0;
        urgency = Math.min(1.0, urgency);

        // --- 2. 各パラメーターの更新 ---
        if (targetPlayer != null) {
            currentDist = bukkitEntity.getLocation().distance(targetPlayer.getLocation());

            // --- 予測モデルの適用 (0.5秒後を予測) ---
            Vector myFuture = bukkitEntity.getLocation().toVector().add(bukkitEntity.getVelocity().multiply(10));
            Vector targetFuture = predictFutureLocation(targetPlayer, 0.5);
            predictedDist = myFuture.distance(targetFuture);
        }

        // 現在の殺気と、0.5秒後の殺気の両方を計算
        double currentImminence = calculateAttackImminence(targetPlayer, currentDist, bukkitEntity);
        double futureImminence = calculateAttackImminence(targetPlayer, predictedDist, bukkitEntity);

        // 未来の危険度が今の危険度より急激に上がっている場合、それは「踏み込み」と判断
        double imminenceDelta = Math.max(0, futureImminence - currentImminence);

        // 反射(Reflex)ニューロンに「未来の予兆」を強く入力する
        // これにより、実際に殴られる数チック前に回避行動の閾値を超えるようになる
        brain.reflex.update(futureImminence + (imminenceDelta * 2.0), 1.0);

        // --- 集合知による補正の安全な取得 ---
        double globalFear = 0.0;
        if (targetPlayer != null) {
            // ターゲットがいる場合のみ、そのプレイヤーに対する危険度を取得
            globalFear = CollectiveKnowledge.playerDangerLevel.getOrDefault(targetPlayer.getUniqueId(), 0.0);
        }

        // 仲間の死による全体的なバイアス（こちらはターゲットがいなくても適用される）
        double collectiveShock = CollectiveKnowledge.globalFearBias;

        // 恐怖(Fear)を集合知と個体知の合算で更新
        // ターゲットがいない場合でも、集団がパニックなら少し Fear が上がる設計
        brain.fear.update(1.0, (globalFear * 0.5) + (collectiveShock * 0.3));

        // 士気の計算にバイアスを反映
        brain.morale += CollectiveKnowledge.globalAggressionBias - collectiveShock;

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
    private double calculateAttackImminence(Player player, double dist, Mob entity) {
        if (player == null) return 0.0;

        double score = 0.0;

        // --- 1. 武器の脅威リーチ判定 ---
        double weaponReach = 3.5; // デフォルトのリーチ
        String mainHand = player.getInventory().getItemInMainHand().getType().name().toLowerCase();
        if (mainHand.contains("spear") || mainHand.contains("needle") || mainHand.contains("trident")) {
            weaponReach = 6.0; // 槍系の武器は警戒距離を伸ばす
        }

        // 間合いに入っている度合い (0.0 ~ 1.0)
        double reachFactor = Math.max(0, 1.0 - (dist / weaponReach));
        score += reachFactor * 0.4;

        // --- 2. 物理的な「踏み込み」速度の検知 ---
        // プレイヤーがこちらに向かって移動しているベクトル強度
        Vector relativeVelocity = player.getVelocity().subtract(entity.getVelocity());
        Vector toEntity = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
        double approachSpeed = relativeVelocity.dot(toEntity); // 内積で接近速度を算出

        if (approachSpeed > 0.2) {
            score += 0.3; // 突っ込んできている時は危険
        }

        // --- 3. 精密な視線（エイム）チェック ---
        // プレイヤーの視線ベクトルと自分への方向ベクトルの合致度
        Vector lookVec = player.getLocation().getDirection();
        double aimConcentration = lookVec.dot(toEntity); // 1.0に近いほど正確に自分を向いている

        if (aimConcentration > 0.98) { // ほぼ正面
            score += 0.3;
        } else if (aimConcentration < 0.8) {
            score -= 0.2; // 横を向いているなら隙がある
        }

        // --- 4. 操作入力による「殺気」の検知 ---
        if (player.getAttackCooldown() > 0.9) {
            score += 0.2;
        }
        if (player.isSneaking()) {
            score += 0.1; // 溜め動作の警戒
        }

        // --- 5. Sキー引き撃ち（Kiting）の検知 ---
        // プレイヤーが後ろに下がりながら攻撃準備をしている場合
        double backpedalSpeed = relativeVelocity.dot(lookVec);
        if (backpedalSpeed < -0.1 && player.getAttackCooldown() > 0.8) {
            // これは「ハメ」の典型的な動き。あえてImminenceを高くして「様子見(OBSERVE)」を誘発させる
            score += 0.2;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * 相手の現在速度と方向から、指定秒数後の予測位置を算出する
     */
    private Vector predictFutureLocation(Player player, double seconds) {
        // 現在の位置ベクトル
        Vector currentLoc = player.getLocation().toVector();
        // 速度ベクトル（1秒あたりの移動量）に時間をかける
        Vector velocity = player.getVelocity();

        // プレイヤーの速度が 0 の場合、Velocity が正確に取れないことがあるため
        // 必要に応じて前回の座標との差分をとるロジックを挟むとより正確になります
        return currentLoc.add(velocity.multiply(seconds * 20)); // 20 ticks = 1s
    }

    private BanditDecision resolveDecision(LiquidBrain brain, BanditContext context, double enemyDist) {
        BanditDecision d = new BanditDecision();
        d.decision = new BanditDecision.DecisionCore();
        d.movement = new BanditDecision.MovementPlan();
        d.communication = new BanditDecision.Communication();

        double agg = brain.aggression.get();
        double fear = brain.fear.get();
        double ref = brain.reflex.get();
        double morale = brain.morale;

        // デバッグログ用にFrustrationも追加
        d.reasoning = String.format("M:%.2f A:%.2f R:%.2f Ad:%.2f Fr:%.2f",
                morale, agg, ref, brain.adrenaline, brain.frustration);

        // --- 【新設】不満度（Frustration）の蓄積ロジック ---
        // 「後退しているのに敵がまだ近い」＝ハメられている可能性が高い
        if (d.decision.action_type != null && d.decision.action_type.equals("RETREAT") && enemyDist < 6.0) {
            brain.frustration += 0.05; // 毎チック蓄積
        }

        // --- 【超重要】バースト・カウンター（キレる挙動） ---
        // 不満が冷静さを超えた時、恐怖を無視して「死なば諸共」の突撃を開始する
        if (brain.frustration > brain.composure) {
            d.decision.action_type = "AMBUSH";
            d.movement.strategy = "SPRINT_ZIGZAG"; // ジグザグ突撃で槍を避けやすく
            d.decision.use_skill = "Four_consecutive_attacks";    // スキルを強制使用
            d.communication.voice_line = "ENOUGH OF THIS!";

            // 一度発動したら不満をリセットし、アドレナリンを最大にする
            brain.frustration = 0;
            brain.adrenaline = 1.0;
            return d;
        }

        // --- 予測回避（Pre-emptive Evasion） ---
        // 実際に殴られる（Ref > 0.8）前でも、未来の危険度が高いなら「予備動作」に入る
        if (brain.reflex.get() > 0.6 && brain.reflex.get() < 0.8) {
            d.decision.action_type = "OBSERVE";
            d.movement.strategy = "MAINTAIN_DISTANCE";
            d.communication.voice_line = "I see what you're doing...";
            return d;
        }

        // --- 戦略：ハメ殺し対策の「様子見（OBSERVE）」 ---
        if (morale < 0.2 && enemyDist < 5.0) {
            d.decision.action_type = "OBSERVE";
            d.movement.strategy = "MAINTAIN_DISTANCE";
            // 様子見中は不満が少しずつ溜まる（イライラしてくる）
            brain.frustration += 0.02;
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
        } else {
            d.decision.action_type = "RETREAT";
            d.movement.destination = "NEAREST_COVER";
        }

        return d;
    }
}