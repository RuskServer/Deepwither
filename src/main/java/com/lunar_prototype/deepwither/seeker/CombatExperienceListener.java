package com.lunar_prototype.deepwither.seeker;

import com.lunar_prototype.deepwither.Deepwither;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CombatExperienceListener implements Listener {
    private final SeekerAIEngine aiEngine;

    public CombatExperienceListener(SeekerAIEngine aiEngine) {
        this.aiEngine = aiEngine;
    }

    @EventHandler
    public void onCombat(EntityDamageByEntityEvent event) {
        float damage = (float) event.getFinalDamage();

        // --- 1. Mobがダメージを与えた場合 (成功体験) ---
        if (event.getDamager() instanceof Mob attacker) {
            // 量子化学習の適用 (damageDealtに値を渡す)
            applyLearning(attacker, damage, 0.0f, false, null);

            // 既存のReward処理
            handleReward(attacker, 0.3f);

            getBrain(attacker).ifPresent(brain -> {
                brain.tacticalMemory.myHits++;
            });
        }

        // --- 2. Mobがダメージを受けた場合 (失敗体験 & 複数戦の学習) ---
        if (event.getEntity() instanceof Mob victim) {
            Entity damager = event.getDamager();

            // Q-Learning: ダメージを受けた報酬処理 (damageTakenに値を渡す)
            applyLearning(victim, 0.0f, damage, false, damager);

            getBrain(victim).ifPresent(brain -> {
                brain.tacticalMemory.takenHits++;
                brain.tacticalMemory.lastHitTime = victim.getTicksLived();

                // 既存のPenalty処理
                handlePenaltyAndPattern(victim, damager, 0.5f);

                // 相手がプレイヤーなら攻撃パターンを詳細に記録
                if (damager instanceof Player p) {
                    float dist = (float) p.getLocation().distance(victim.getLocation());

                    // ターゲット切り替えロジック
                    if (victim.getTarget() == null || victim.getLocation().distance(victim.getTarget().getLocation()) > 10.0) {
                        if (ThreadLocalRandom.current().nextFloat() < 0.5f) victim.setTarget(p);
                    }

                    // 攻撃パターンのサンプリング (最新仕様: UUID, distance, isMiss)
                    brain.recordAttack(p.getUniqueId(), dist, false);
                }
            });
        }
    }

    private void handleReward(Mob mob, float amount) {
        getBrain(mob).ifPresent(brain -> {
            brain.accumulatedReward += amount;
            // 自己攻撃リズムの記録
            brain.recordSelfAttack(mob.getTicksLived());
        });
    }

    /**
     * 失敗体験の蓄積に加え、最新の攻撃リズム・距離学習を実行する
     */
    private void handlePenaltyAndPattern(Mob mob, Entity damager, float amount) {
        getBrain(mob).ifPresent(brain -> {
            // 失敗体験（ペナルティ）の蓄積
            brain.accumulatedPenalty += amount;

            // 攻撃者がプレイヤーの場合のみ記録
            if (damager instanceof Player player) {
                float distance = (float) player.getLocation().distance(mob.getLocation());
                // recordAttack(UUID, dist, isMiss)
                // 被弾しているため isMiss は当然 false
                brain.recordAttack(player.getUniqueId(), distance, false);
            }
        });
    }

    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Location playerLoc = player.getLocation();
        Vector lookDir = playerLoc.getDirection(); // プレイヤーの視線

        // 8m以内のエンティティを取得
        List<Entity> nearby = player.getNearbyEntities(8, 8, 8);

        for (int i = 0; i < nearby.size(); i++) {
            Entity e = nearby.get(i);

            // ActiveMob判定とBrainの取得を統合
            if (e instanceof Mob mob) {
                Optional.ofNullable(aiEngine.getBrain(mob.getUniqueId())).ifPresent(brain -> {

                    // 1. ベクトル計算 (float化)
                    Vector toMob = mob.getLocation().toVector().subtract(playerLoc.toVector()).normalize();
                    float dot = (float) lookDir.dot(toMob);
                    float dist = (float) playerLoc.distance(mob.getLocation());

                    // 2. 回避成功の学習判定 (ドット積によるエイム精度チェック)
                    // dot > 0.9f は約25度以内。プレイヤーがMobを捉えて振っている場合
                    if (dot > 0.9f) {
                        // 回避成功報酬 (量子化版 applyLearning)
                        // 引数: mob, damageDealt, damageTaken, isEvaded, damager
                        applyLearning(mob, 0.0f, 0.0f, true, player);

                        brain.tacticalMemory.avoidedHits++; // 戦術メモリ更新
                    }

                    // 3. 空振りリズムの学習 (最新の recordAttack シグネチャ)
                    // recordAttack(UUID, distance, isMiss)
                    // このイベントは「当たっていない（振っただけ）」ので isMiss = true
                    brain.recordAttack(playerUUID, (double) dist, true);
                });
            }
        }
    }

    private Optional<LiquidBrain> getBrain(Entity entity) {
        return MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId())
                .map(am -> aiEngine.getBrain(am.getUniqueId()));
    }

    // B. メインロジック：TQH統合版（冷却と加熱の物理シミュレーション）
    public void applyLearning(Mob mob, float damageDealt, float damageTaken, boolean evaded, Entity damager) {
        Optional.ofNullable(aiEngine.getBrain(mob.getUniqueId())).ifPresent(brain -> {
            float rawReward = 0.0f;
            float thermalStress = 0.0f; // [TQH] このアクションで発生した「熱」

            // =========================================================
            // [動的スケーラー] 状況(信頼度・冷静さ)と疲労の相関
            // =========================================================
            float currentFatigue = brain.lastActionIdx >= 0 ? brain.fatigueMap[brain.lastActionIdx] : 0.0f;
            float correlationFactor = (brain.velocityTrust * 0.5f + brain.composure * 0.5f) * (1.0f - currentFatigue);

            // 1. 基本報酬の計算 (冷却材の生成)
            // 攻撃成功はシステムを冷却し、現在の「勝ちパターン」を脳に結晶化させる
            rawReward += (damageDealt * (2.0f + 1.0f * correlationFactor));

            // ダメージによるペナルティ
            rawReward -= (damageTaken * (1.0f + 1.0f * brain.composure));

            // 回避成功
            if (evaded) {
                rawReward += (1.0f + 1.0f * brain.velocityTrust);
            }

            // 2. 横槍判定と熱暴走 (Thermal Stress)
            if (damageTaken > 0 && damager != null) {
                Entity target = mob.getTarget();
                if (target != null && !damager.getUniqueId().equals(target.getUniqueId())) {
                    rawReward -= 5.0f;
                    // 横槍は「予測不能な不快感」として、システム温度を直接上昇させる
                    thermalStress += 0.5f;
                    brain.frustration += 0.2f;
                    brain.composure = Math.max(0.0f, brain.composure - 0.1f);
                }
            }

            // 4. [TQH Core] 量子化Q-Update & 熱力学フィードバック
            if (brain.lastStateIdx >= 0 && brain.lastActionIdx >= 0) {

                // 【冷却】正の報酬が得られた場合、システムを冷却して構造を固定
                if (rawReward > 0) {
                    brain.systemTemperature -= (rawReward * 0.45f);
                }
            }

            // 5. 蓄積報酬の反映（digestExperienceでの後処理用）
            brain.accumulatedReward += Math.max(0, rawReward);
            brain.accumulatedPenalty += Math.max(0, -rawReward);

            // [2026-01-12] 状態に応じた構造再編のトリガー
            // 学習の直後に相転移をチェックすることで、即座にParticle.FLASHの色に反映させる
            brain.reshapeTopology();
        });
    }

    /**
     * ケース1: 山賊が死んだ時 (DEFEAT分析)
     */
    @EventHandler
    public void onSeekerDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        LiquidBrain brain = aiEngine.getBrain(entity.getUniqueId());

        if (brain != null) {
            String killerName = entity.getKiller() != null ? entity.getKiller().getName() : "Unknown";

            // 敗北として分析レポートを生成
            CombatAnalyst.review(brain, killerName, CombatAnalyst.Result.DEFEAT);
        }
    }

    /**
     * プレイヤーが殺された時 (VICTORY分析)
     * getKiller() が Mob の場合は null を返す Minecraft の仕様を回避
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // 直近のダメージ原因を確認
        if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent nlev) {
            // ダメージを与えたのがMob（山賊）かチェック
            if (nlev.getDamager() instanceof Mob killer) {
                LiquidBrain brain = aiEngine.getBrain(killer.getUniqueId());

                if (brain != null) {
                    // 勝利レポート生成
                    CombatAnalyst.review(brain, victim.getName(), CombatAnalyst.Result.VICTORY);
                }
            }
        }
    }
}