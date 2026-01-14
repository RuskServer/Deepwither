package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.util.List;

public class Actuator {

    private double chaosX = 0.1, chaosY = 0.0, chaosZ = 0.0;
    private static final double SIGMA = 10.0;
    private static final double RHO = 28.0;
    private static final double BETA = 8.0 / 3.0;
    private static final double DT = 0.02; // 1Tick(0.05s)より少し細かい時間刻み

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
        if (entity.getVelocity().length() > 0.5 && entity.getVelocity().length() < 0.01) {
            // 強制的にジャンプさせてスタック解除を試みる
            entity.setVelocity(entity.getVelocity().setY(0.4));
        }

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

            if (move.strategy.equals("ESCAPE_SQUEEZE")) {
                performEscapeSqueeze(entity,new SensorProvider().scanEnemies(entity,entity.getNearbyEntities(32, 32, 32)));
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
     * 敵に囲まれた際、最も敵が薄い方向、または味方がいる方向へ抜ける
     */
    private void performEscapeSqueeze(Mob entity, List<BanditContext.EnemyInfo> enemies) {
        Vector escapeVec = new Vector(0, 0, 0);
        for (BanditContext.EnemyInfo enemy : enemies) {
            // 敵から遠ざかるベクトルの合計
            Vector diff = entity.getLocation().toVector().subtract(enemy.playerInstance.getLocation().toVector());
            escapeVec.add(diff.normalize().multiply(1.0 / enemy.dist)); // 近い敵ほど強く反発
        }

        // 地形チェックをしつつ加速
        if (!isPathBlocked(entity, escapeVec.normalize())) {
            entity.setVelocity(escapeVec.normalize().multiply(1.2).setY(0.1));
        } else {
            entity.getPathfinder().moveTo(entity.getLocation().add(escapeVec.multiply(3)), 2.0);
        }
    }

    /**
     * 進行方向に壁があるか、または足場がないかをチェックする
     */
    private boolean isPathBlocked(Mob entity, Vector direction) {
        Location eyeLoc = entity.getEyeLocation();
        // 進行方向 1.5m 先をチェック
        Location targetCheck = eyeLoc.clone().add(direction.clone().multiply(1.5));

        // 1. 壁判定（目の高さが空気でないならブロックがある）
        if (!targetCheck.getBlock().getType().isAir()) return true;

        // 2. 崖判定（足元が深すぎるなら止まる）
        Location floorCheck = targetCheck.clone().subtract(0, 2, 0);
        if (floorCheck.getBlock().getType().isAir()) return true;

        return false;
    }

    /**
     * 瞬間的なベクトル加速
     */
    private void performBurstDash(Mob entity, double power) {
        if (entity.getTarget() == null) return;

        Location targetLoc = entity.getTarget().getLocation();
        Vector dir = targetLoc.toVector().subtract(entity.getLocation().toVector()).normalize();

        // 1. 接地判定（OnGround）の擬似チェック
        // entity.isOnGround() が使える場合はそれを使用。ない場合は高度差で判定。
        boolean isOnGround = entity.getLocation().subtract(0, 0.1, 0).getBlock().getType().isSolid();

        if (isOnGround) {
            // 地面に足がついた瞬間に、ターゲット方向への慣性を乗せて跳ぶ
            // Y軸に 0.42 (プレイヤーの通常ジャンプ力) を与えつつ、水平方向を加速
            Vector boost = dir.multiply(power).setY(0.42);

            // 障害物が目の前にある場合は、より高く跳んで飛び越えを試みる
            if (isPathBlocked(entity, dir)) {
                boost.setY(0.5);
                boost.multiply(0.8); // 上に振る分、横は少し抑える
            }

            entity.setVelocity(boost);

            // 演出：踏み込みのパーティクル
            entity.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, entity.getLocation(), 5, 0.1, 0.05, 0.1, 0.01);
        } else {
            // 空中にいる間は、Pathfinderを止めて慣性移動を邪魔させない
            // ただし、空中でターゲット方向に微調整の力を加える（エアストレイフ）
            Vector currentVel = entity.getVelocity();
            Vector airSteer = dir.multiply(0.05); // 微細な空中制御
            entity.setVelocity(currentVel.add(airSteer));
        }
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

        // 1. カオス方程式の更新 (Lorentz Attractor)
        // 毎Tick、わずかに状態を遷移させることで「蝶の羽ばたき」のような軌道を生む
        double dx = SIGMA * (chaosY - chaosX) * DT;
        double dy = (chaosX * (RHO - chaosZ) - chaosY) * DT;
        double dz = (chaosX * chaosY - BETA * chaosZ) * DT;
        chaosX += dx; chaosY += dy; chaosZ += dz;

        Location self = entity.getLocation();
        Vector toTarget = entity.getTarget().getLocation().toVector().subtract(self.toVector()).normalize();

        // 2. 基底となるサイドベクトルの生成
        Vector side = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();

        // 3. 【核心】カオス項による変調
        // chaosX の値をサイドベクトルの倍率に、chaosY を前後方向の微細な揺らぎに変換
        // 速度を 1.2 -> 0.7 程度に落とし、その分「軌道の歪み」を最大化
        double lateralMod = chaosX * 0.15; // 左右への予測不能な振れ幅
        double longitudinalMod = chaosY * 0.1; // 前後への微細な揺らぎ（フェイント）

        // 速度を抑えつつ、カオス的な合成ベクトルを生成
        Vector chaoticVel = toTarget.multiply(0.4 + longitudinalMod)
                .add(side.multiply(lateralMod));

        // 4. スタック（壁）回避ロジックは維持しつつ、カオス状態を反転させる
        if (isPathBlocked(entity, chaoticVel.clone().normalize())) {
            chaosX *= -1; // 物理的にぶつかったらカオスの極性を反転（アトラクタの別翼へジャンプ）
            chaoticVel = toTarget.multiply(0.4).add(side.multiply(chaosX * 0.15));
        }

        entity.setVelocity(chaoticVel.setY(0.1));
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