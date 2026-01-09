package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

public class Actuator {

    public void execute(ActiveMob activeMob, BanditDecision decision, Location coverLoc) {
        if (activeMob.getEntity() == null || !(activeMob.getEntity().getBukkitEntity() instanceof Mob)) {
            return;
        }
        Mob entity = (Mob) activeMob.getEntity().getBukkitEntity();

        // 1. Stance（状態）の反映
        if (decision.decision.new_stance != null) {
            activeMob.setStance(decision.decision.new_stance);
        }

        // 2. 移動戦略の実行
        handleMovement(entity, decision.movement, coverLoc);

        // 3. スキル・コミュニケーションの実行
        handleActions(activeMob, decision);
    }

    private void handleMovement(Mob entity, BanditDecision.MovementPlan move, Location coverLoc) {
        if (move.strategy != null) {
            // --- 既存の回避ロジック ---
            if (move.strategy.equals("BACKSTEP") || move.strategy.equals("SIDESTEP")) {
                performEvasiveStep(entity, move.strategy);
                return;
            }

            // --- V2: 自己同期・踏み込み (CHARGE) ---
            if (move.strategy.equals("CHARGE")) {
                performDirectCharge(entity); // 揺れのない最短距離での突進
                return;
            }

            if  (move.strategy.equals("BURST_DASH")) {
                performBurstDash(entity,1.4); // 揺れのない最短距離での突進
                return;
            }

            if  (move.strategy.equals("ORBITAL_SLIDE")) {
                performOrbitalSlide(entity); // 揺れのない最短距離での突進
                return;
            }

            // --- V2: 攻撃後離脱 (POST_ATTACK_EVADE) ---
            if (move.strategy.equals("POST_ATTACK_EVADE")) {
                performPostAttackEvade(entity);
                return;
            }

            // --- 既存のジグザグ ---
            if (move.strategy.equals("SPRINT_ZIGZAG")) {
                performSprintZigzag(entity);
                return;
            }
        }

        if (move.strategy != null && move.strategy.equals("MAINTAIN_DISTANCE")) {
            maintainDistance(entity);
            return;
        }

        if (move.destination == null) return;

        switch (move.destination) {
            case "NEAREST_COVER":
                if (coverLoc != null) {
                    entity.getPathfinder().moveTo(coverLoc, 1.2);
                }
                break;
            case "ENEMY":
                if (entity.getTarget() != null) {
                    entity.getPathfinder().moveTo(entity.getTarget().getLocation(), 1.0);
                }
                break;
            case "NONE":
                entity.getPathfinder().stopPathfinding();
                break;
        }
    }

    /**
     * 瞬間的なベクトル加速 (Arc Raiders風)
     */
    private void performBurstDash(Mob entity, double power) {
        if (entity.getTarget() == null) return;

        Vector dir = entity.getTarget().getLocation().toVector()
                .subtract(entity.getLocation().toVector()).normalize();

        // Y軸を少し浮かせることで摩擦を減らし、爆発的な推進力を得る
        entity.setVelocity(dir.multiply(power).setY(0.15));

        // 安全なパーティクル演出（Colorデータ不要なもの）
        entity.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, entity.getLocation(), 10, 0.2, 0.1, 0.2, 0.05);
    }

    /**
     * 最短距離で一気に間合いを詰める。
     * スキルや近接攻撃のリーチに入れるための、遊びのない突撃。
     */
    private void performDirectCharge(Mob entity) {
        if (entity.getTarget() == null) return;

        // ターゲットの足元ではなく、少し先を目的地にすることで慣性を乗せる
        Location targetLoc = entity.getTarget().getLocation();
        Vector direction = targetLoc.toVector().subtract(entity.getLocation().toVector()).normalize();
        Location destination = targetLoc.clone().add(direction.multiply(1.5));

        entity.getPathfinder().moveTo(destination, 2.5); // 最高速度
    }

    /**
     * 敵の視線を外しつつ回り込むスライディング
     */
    private void performOrbitalSlide(Mob entity) {
        if (entity.getTarget() == null) return;

        Location self = entity.getLocation();
        Vector toTarget = entity.getTarget().getLocation().toVector().subtract(self.toVector()).normalize();

        // 垂直ベクトルを作成（右か左か）
        Vector side = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();
        if (entity.getTicksLived() % 40 < 20) side.multiply(-1); // 20tickごとに方向転換

        // 前進と横移動をミックスした高速ベクトル
        Vector finalVel = toTarget.multiply(0.6).add(side.multiply(1.2)).setY(0.1);
        entity.setVelocity(finalVel);
    }

    /**
     * 攻撃直後のヒットアンドアウェイ挙動。
     * 斜め後ろに下がりながら、敵の反撃ラインから外れる。
     */
    private void performPostAttackEvade(Mob entity) {
        if (entity.getTarget() == null) return;

        Location selfLoc = entity.getLocation();
        Location targetLoc = entity.getTarget().getLocation();

        // 敵から離れるベクトル
        Vector awayVec = selfLoc.toVector().subtract(targetLoc.toVector()).normalize();

        // 単純に下がるのではなく、左右どちらかにランダムに逸れる
        // entityのUUID等をシードにして、個体ごとに避ける方向を固定するとより自然
        Vector sideVec = new Vector(-awayVec.getZ(), 0, awayVec.getX()).normalize();
        if (entity.getUniqueId().getMostSignificantBits() % 2 == 0) sideVec.multiply(-1);

        // 斜め後ろ 4m 地点
        Location destination = selfLoc.clone().add(awayVec.multiply(3.0)).add(sideVec.multiply(2.0));

        entity.getPathfinder().moveTo(destination, 1.8);
    }

    /**
     * Pathfinderを利用して、地形を考慮した回避運動を行う
     */
    private void performEvasiveStep(Mob entity, String strategy) {
        if (entity.getTarget() == null) return;

        Location selfLoc = entity.getLocation();
        Location targetLoc = entity.getTarget().getLocation();

        // ターゲットから自分への方向ベクトル
        Vector awayVec = selfLoc.toVector().subtract(targetLoc.toVector()).normalize();
        Location destination;

        if (strategy.equals("BACKSTEP")) {
            // 現在地からターゲットの反対方向へ3m地点を計算
            destination = selfLoc.clone().add(awayVec.multiply(3.0));
        } else {
            // サイドステップ（垂直方向）へ3m地点を計算
            Vector sideVec = new Vector(-awayVec.getZ(), 0, awayVec.getX());
            if (Math.random() > 0.5) sideVec.multiply(-1);
            destination = selfLoc.clone().add(sideVec.multiply(3.0));
        }

        // --- 地形対応のポイント ---
        // 計算した地点が「空中」や「壁の中」である可能性を考慮し、
        // Pathfinderにその地点、あるいはその周辺の安全な場所を探させる
        entity.getPathfinder().moveTo(destination, 2.0); // 通常の2倍の速度(Sprint)で回避
    }

    private void handleActions(ActiveMob activeMob, BanditDecision decision) {
        Entity entity = activeMob.getEntity().getBukkitEntity();
        // スキルの強制発動
        if (decision.decision.use_skill != null && !decision.decision.use_skill.equalsIgnoreCase("NONE")) {
            MythicBukkit.inst().getAPIHelper().castSkill(entity, decision.decision.use_skill);
        }

        // 音声（セリフ）の再生
        if (decision.communication.voice_line != null) {
            String message = "§7[" + activeMob.getType().getInternalName() + "] §f" + decision.communication.voice_line;
            entity.getNearbyEntities(10, 10, 10).forEach(e -> {
                if (e instanceof org.bukkit.entity.Player) {
                    e.sendMessage(message);
                }
            });
        }
    }

    /**
     * ターゲットから一定の距離(約6m)を保ちつつ、左右に揺れる
     */
    private void maintainDistance(Mob entity) {
        if (entity.getTarget() == null) return;

        Location targetLoc = entity.getTarget().getLocation();
        Location selfLoc = entity.getLocation();
        double dist = selfLoc.distance(targetLoc);

        Vector direction;
        if (dist < 6.0) {
            // 近すぎるなら離れる（斜め後ろへ）
            direction = selfLoc.toVector().subtract(targetLoc.toVector()).normalize();
        } else {
            // 遠いなら少し近づく（斜め前へ）
            direction = targetLoc.toVector().subtract(selfLoc.toVector()).normalize();
        }

        // 左右への「揺れ」を加えて、ハメ（エイム）を困難にする
        Vector sideStep = new Vector(-direction.getZ(), 0, direction.getX())
                .multiply(Math.sin(entity.getTicksLived() * 0.1) * 2.0);

        Location dest = selfLoc.clone().add(direction.multiply(3.0)).add(sideStep);
        entity.getPathfinder().moveTo(dest, 1.2);
    }

    /**
     * 敵に向かって高速でジグザグに接近し、直線的な攻撃（槍など）を回避する
     */
    private void performSprintZigzag(Mob entity) {
        if (entity.getTarget() == null) return;

        Location selfLoc = entity.getLocation();
        Location targetLoc = entity.getTarget().getLocation();

        // 敵への方向ベクトル
        Vector toTarget = targetLoc.toVector().subtract(selfLoc.toVector()).normalize();

        // 敵への方向に対して垂直なベクトル（横移動用）
        Vector sideVec = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();

        // 時間（Ticks）経過によるサイン波で左右の揺れ幅を計算
        // 周期を早めに設定し（* 0.4）、振幅を 2.5ブロック程度に設定
        double wave = Math.sin(entity.getTicksLived() * 0.4) * 2.5;

        // 「前方へのベクトル」+「左右の揺れベクトル」を合成
        Vector finalMove = toTarget.multiply(4.0).add(sideVec.multiply(wave));
        Location destination = selfLoc.clone().add(finalMove);

        // スプリント速度（2.0以上）でターゲットに向かわせる
        // Pathfinderを使うことで、ジグザグ移動の途中に障害物があってもスタックしにくくなる
        entity.getPathfinder().moveTo(destination, 2.2);
    }
}