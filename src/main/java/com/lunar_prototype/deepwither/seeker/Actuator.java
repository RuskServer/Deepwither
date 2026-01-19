package com.lunar_prototype.deepwither.seeker;

import com.lunar_prototype.deepwither.Deepwither;
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

        // 3. 慣性攻撃チェック (New!)
        // Velocityがかかっている状態でも、一定距離内に敵がいれば「すれ違いざま」に殴る
        if (entity.getTarget() != null) {
            double dist = entity.getLocation().distance(entity.getTarget().getLocation());
            double reach = 3.5; // 少し広めに設定

            // バックステップ中やダッシュ中、相手が射程内にいれば攻撃トリガー
            if (dist <= reach) {
                // 高温(GAS)時は、狂ったように振り回す
                if (Deepwither.getInstance().getAiEngine().getBrain(activeMob.getUniqueId()).systemTemperature > 1.2f || Math.random() < 0.3) {
                    entity.attack(entity.getTarget());
                }
            }
        }

        // 4. スキル・コミュニケーションの実行
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
                performEvasiveStep(entity, move.strategy,Deepwither.getInstance().getAiEngine().getBrain(entity.getUniqueId()));
                return;
            }

            // --- V2: 自己同期・踏み込み (CHARGE) ---
            if (move.strategy.equals("CHARGE")) {
                performDirectCharge(entity); // 揺れのない最短距離での突進
                return;
            }

            if  (move.strategy.equals("BURST_DASH")) {
                performBurstDash(entity,1.4, Deepwither.getInstance().getAiEngine().getBrain(entity.getUniqueId())); // 揺れのない最短距離での突進
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
     * 距離15ブロック以内なら未来予想位置を算出して確実に敵を捉えるバーストダッシュ
     */
    private void performBurstDash(Mob entity, double power, LiquidBrain brain) {
        if (entity.getTarget() == null) return;

        Location myLoc = entity.getLocation();
        Entity target = entity.getTarget();
        double dist = myLoc.distance(target.getLocation());

        Vector targetDir;

        // --- 未来位置予測ロジック (15ブロック以内かつ予測データがある場合) ---
        if (dist < 15.0 && brain.lastPredictedLocation != null && brain.velocityTrust > 0.3) {
            // 脳内に保存されている「20Tick後の予測位置」を利用
            // ただし、ダッシュの到達時間に合わせて重みを調整
            Vector predictedPos = brain.lastPredictedLocation;
            targetDir = predictedPos.clone().subtract(myLoc.toVector()).normalize();

            // 予測地点へのベクトルを強調（偏差撃ちに近い感覚）
            targetDir.multiply(1.2).add(target.getLocation().toVector().subtract(myLoc.toVector()).normalize()).normalize();
        } else {
            // 通常の追跡
            targetDir = target.getLocation().toVector().subtract(myLoc.toVector()).normalize();
        }

        boolean isOnGround = entity.getLocation().subtract(0, 0.1, 0).getBlock().getType().isSolid();

        if (isOnGround) {
            // ターゲット位置を捉えるためのブースト
            Vector boost = targetDir.multiply(power).setY(0.42);

            // 障害物判定（既存ロジック）
            if (isPathBlocked(entity, targetDir)) {
                boost.setY(0.55); // 少し高く
                boost.multiply(0.85);
            }

            entity.setVelocity(boost);

            // 演出: 2026-01-12 指定の Color データを適用した Particle.FLASH
            int[] rgb = brain.getTQHFlashColor();
            entity.getWorld().spawnParticle(
                    org.bukkit.Particle.DUST, // または FLASH
                    entity.getLocation().add(0, 1, 0),
                    10, 0.2, 0.2, 0.2,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(rgb[0], rgb[1], rgb[2]), 1.5f)
            );
        } else {
            // 空中制御（エアストレイフ）
            entity.setVelocity(entity.getVelocity().add(targetDir.multiply(0.08)));
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
     * TQH相転移と空間検知を組み合わせた高精度回避 + カウンター
     */
    private void performEvasiveStep(Mob entity, String strategy, LiquidBrain brain) {
        if (entity.getTarget() == null) return;

        Location selfLoc = entity.getLocation();
        Entity target = entity.getTarget();
        Location targetLoc = target.getLocation();
        Vector awayDir = selfLoc.toVector().subtract(targetLoc.toVector()).setY(0).normalize();

        Location bestDest = null;

        // 1. 基本戦略の座標計算
        if (strategy.equals("BACKSTEP")) {
            bestDest = findSafeDestination(selfLoc, awayDir, 3.5);
        }

        if (bestDest == null || strategy.equals("SIDESTEP")) {
            Vector leftDir = new Vector(-awayDir.getZ(), 0, awayDir.getX());
            Vector rightDir = leftDir.clone().multiply(-1);
            Location leftDest = findSafeDestination(selfLoc, leftDir, 4.0);
            Location rightDest = findSafeDestination(selfLoc, rightDir, 4.0);
            bestDest = (leftDest != null) ? leftDest : rightDest;
        }

        if (bestDest != null) {
            // --- カウンター処理 (New!) ---
            // バックステップ時かつ、ターゲットが射程内(4m以内)にいる場合
            if (strategy.equals("BACKSTEP") && selfLoc.distance(targetLoc) < 4.0) {
                executeCounterStrike(entity, target, brain);
            }

            // --- 移動の実行 ---
            entity.getPathfinder().stopPathfinding();

            if (brain.systemTemperature > 1.0f) { // GAS相: 爆発的な回避
                Vector jumpDir = bestDest.toVector().subtract(selfLoc.toVector()).normalize().multiply(0.6);
                entity.setVelocity(entity.getVelocity().add(jumpDir.setY(0.25)));
            }

            entity.getPathfinder().moveTo(bestDest, 1.8);

            // 演出: 回避のクラウドパーティクル
            entity.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, selfLoc, 3, 0.2, 0.1, 0.2, 0.02);
        }
    }

    /**
     * 回避中のカウンター攻撃
     */
    private void executeCounterStrike(Mob entity, Entity target, LiquidBrain brain) {
        // 1. 強制的にターゲットを向かせ、攻撃をトリガー
        entity.attack(target);

        Location eyeLoc = entity.getEyeLocation();

        // 斬撃エフェクト
        entity.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, eyeLoc.add(entity.getLocation().getDirection().multiply(1.2)), 1);

        // 3. 高温(GAS)時は、カウンターの衝撃で相手を少しノックバックさせる（追撃阻止）
        if (brain.systemTemperature > 1.2f) {
            Vector push = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(0.5).setY(0.2);
            target.setVelocity(target.getVelocity().add(push));
        }
    }

    /**
     * 指定方向が安全（ブロックがない、かつ落下しない）かを確認して座標を返す
     */
    private Location findSafeDestination(Location start, Vector dir, double dist) {
        Location dest = start.clone().add(dir.multiply(dist));

        // 1. 壁判定 (Raytrace)
        var ray = start.getWorld().rayTraceBlocks(start.add(0, 1, 0), dir, dist);
        if (ray != null && ray.getHitBlock() != null) return null;

        // 2. 足場判定 (落下防止)
        if (!dest.subtract(0, 1, 0).getBlock().getType().isSolid()) {
            // 1ブロック下までなら段差として許容
            if (!dest.subtract(0, 1, 0).getBlock().getType().isSolid()) return null;
        }

        return dest.add(0, 1, 0); // 足場より1つ上の座標を返す
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