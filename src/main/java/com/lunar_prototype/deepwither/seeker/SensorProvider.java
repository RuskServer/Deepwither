package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.stream.Collectors;

public class SensorProvider {

    // 周囲何ブロックまでをスキャン対象にするか
    private static final int SCAN_RADIUS = 15;

    public BanditContext scan(ActiveMob activeMob) {
        BanditContext context = new BanditContext();
        if (activeMob.getEntity() == null || !(activeMob.getEntity().getBukkitEntity() instanceof Mob)) {
            return null;
        }
        Mob entity = (Mob) activeMob.getEntity().getBukkitEntity();

        // 1. 自身のステータス
        context.entity = new BanditContext.EntityState();
        context.entity.id = activeMob.getUniqueId().toString();
        context.entity.hp_pct = (int) ((entity.getHealth() / entity.getAttribute(Attribute.MAX_HEALTH).getValue()) * 100);
        context.entity.max_hp = (int) entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        context.entity.stance = activeMob.getStance();

        // 2. 環境情報の収集
        context.environment = new BanditContext.EnvironmentState();

        // 敵の情報を取得
        List<Entity> nearby = entity.getNearbyEntities(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS);
        context.environment.nearby_enemies = scanEnemies(entity, nearby);

        // 味方の情報を取得
        context.environment.nearby_allies = scanAllies(entity, nearby);

        // 最寄りの遮蔽物を計算
        context.environment.nearest_cover = findNearestCover(entity,getEnemyLocation(entity));

        // 3. 性格パラメータ (固定値、あるいはMobの設定から取得)
        context.personality = new BanditContext.Personality();
        context.personality.bravery = 0.3;
        context.personality.aggressiveness = 0.6;

        return context;
    }

    private List<BanditContext.AllyInfo> scanAllies(Mob self, List<Entity> nearby) {
        return nearby.stream()
                .filter(e -> e instanceof LivingEntity && e != self) // 自分以外の生きたエンティティ
                .filter(e -> {
                    // MythicMobsの名前（あるいはDisplayName）に "bandit" が含まれるかチェック
                    String name = e.getCustomName();
                    return name != null && name.toLowerCase().contains("bandit");
                })
                .map(e -> {
                    LivingEntity ally = (LivingEntity) e;
                    BanditContext.AllyInfo info = new BanditContext.AllyInfo();

                    // 距離の計算
                    info.dist = self.getLocation().distance(ally.getLocation());

                    // ステータス判定
                    double healthRatio = ally.getHealth() / ally.getAttribute(Attribute.MAX_HEALTH).getValue();

                    if (ally.isDead() || ally.getHealth() <= 0) {
                        info.status = "DEAD";
                    } else if (healthRatio < 0.4) { // 残りHP40%未満を負傷とみなす
                        info.status = "WOUNDED";
                    } else {
                        info.status = "HEALTHY";
                    }

                    return info;
                })
                .collect(Collectors.toList());
    }

    private List<BanditContext.EnemyInfo> scanEnemies(Mob self, List<Entity> nearby) {
        return nearby.stream()
                .filter(e -> e instanceof Player) // ひとまずプレイヤーを敵とする
                .map(e -> {
                    BanditContext.EnemyInfo info = new BanditContext.EnemyInfo();
                    info.dist = self.getLocation().distance(e.getLocation());
                    info.in_sight = self.hasLineOfSight(e);
                    info.holding = ((Player) e).getInventory().getItemInMainHand().getType().name();
                    // HPは大まかにラベル化
                    double p = ((Player) e).getHealth() / 20.0;
                    info.health = p > 0.7 ? "high" : (p > 0.3 ? "mid" : "low");
                    return info;
                }).collect(Collectors.toList());
    }

    // SeekerAIEngineから座標取得のために呼ばれる
    public Location findNearestCoverLocation(ActiveMob activeMob) {
        // MythicMobsのエンティティからBukkitのエンティティを取得し、それをMobにキャストする
        if (!(activeMob.getEntity().getBukkitEntity() instanceof Mob)) {
            return null; // Mob（クリーチャー）でない場合は処理を中断
        }
        Mob self = (Mob) activeMob.getEntity().getBukkitEntity();
        Block coverBlock = findBestCoverBlock(self);

        if (coverBlock == null) return null;

        // 敵の逆側に回り込む座標を計算して返す
        Location enemyLoc = getEnemyLocation(self);
        Vector directionToEnemy = enemyLoc.toVector().subtract(coverBlock.getLocation().toVector()).normalize();
        return coverBlock.getLocation().add(directionToEnemy.multiply(-1.1));
    }

    // scanメソッド内でLLM用のデータを作るために呼ばれる
    public BanditContext.CoverInfo findNearestCover(Mob self, Location coverLoc) {
        if (coverLoc == null) return null;

        BanditContext.CoverInfo info = new BanditContext.CoverInfo();
        info.dist = self.getLocation().distance(coverLoc);
        info.safety_score = 1.0;
        return info;
    }

    /**
     * 最適な遮蔽物（ブロック）を探索するコアロジック
     */
    private Block findBestCoverBlock(Mob self) {
        Location selfLoc = self.getLocation();
        Location enemyLoc = getEnemyLocation(self);
        if (enemyLoc == null) return null;

        Block closestCover = null;
        double minDistance = Double.MAX_VALUE;
        int radius = 8;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = selfLoc.clone().add(x, y, z).getBlock();
                    if (!block.getType().isOccluding()) continue;

                    Vector directionToEnemy = enemyLoc.toVector().subtract(block.getLocation().toVector()).normalize();
                    Location hidingSpot = block.getLocation().add(directionToEnemy.multiply(-1.1));

                    if (isSafeSpot(hidingSpot, enemyLoc)) {
                        double dist = selfLoc.distance(hidingSpot);
                        if (dist < minDistance) {
                            minDistance = dist;
                            closestCover = block;
                        }
                    }
                }
            }
        }
        return closestCover;
    }

    private Location getEnemyLocation(Mob self) {
        if (self.getTarget() != null) return self.getTarget().getLocation();
        // ターゲットがいない場合、近くのプレイヤーを探すなどのフォールバック
        return self.getNearbyEntities(15, 15, 15).stream()
                .filter(e -> e instanceof org.bukkit.entity.Player)
                .map(org.bukkit.entity.Entity::getLocation)
                .findFirst().orElse(null);
    }

    // 実際に射線が通らないかを確認する補助メソッド
    private boolean isSafeSpot(Location spot, Location enemyEye) {
        // 1. スポットが空気（立てる場所）か確認
        if (!spot.getBlock().isEmpty()) return false;

        // 2. 敵の目線からスポットへの視線がブロックで遮られているか
        RayTraceResult result = spot.getWorld().rayTraceBlocks(
                enemyEye,
                spot.toVector().subtract(enemyEye.toVector()).normalize(),
                enemyEye.distance(spot),
                FluidCollisionMode.NEVER,
                true
        );

        // ヒットした＝間に何かのブロックがある＝安全
        return result != null && result.getHitBlock() != null;
    }

    public double getPlayerAttackImminence(Mob self, Player target) {
        double imminence = 0.0;

        // 1. 距離が急激に詰まっているか（加速度）
        double dist = self.getLocation().distance(target.getLocation());
        if (dist < 4.0 && target.isSprinting()) imminence += 0.4;

        // 2. プレイヤーが武器を振った直後か（再攻撃までの警戒）
        // プレイヤーのクールダウンを利用
        if (target.getAttackCooldown() < 0.2) imminence += 0.5;

        return Math.min(1.0, imminence);
    }
}
