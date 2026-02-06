package com.lunar_prototype.deepwither.seeker;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@DependsOn({})
public class SeekerAIEngine implements IManager {

    private final SensorProvider sensorProvider;
    private final LiquidCombatEngine liquidEngine;
    private final Actuator actuator;
    private final Map<UUID, LiquidBrain> brainStorage = new HashMap<>();

    public SeekerAIEngine() {
        this.sensorProvider = new SensorProvider();
        this.liquidEngine = new LiquidCombatEngine();
        this.actuator = new Actuator();
    }

    @Override
    public void init() {
    }

    @Override
    public void shutdown() {
        for (LiquidBrain brain : brainStorage.values()) {
            brain.dispose();
        }
        brainStorage.clear();
    }

    public void tick(ActiveMob activeMob) {
        if (activeMob.getEntity() == null || !(activeMob.getEntity().getBukkitEntity() instanceof Mob)) return;
        Mob bukkitMob = (Mob) activeMob.getEntity().getBukkitEntity();
        UUID uuid = activeMob.getUniqueId();

        // 1. 環境感知
        BanditContext context = sensorProvider.scan(activeMob);
        Location nearestCover = sensorProvider.findNearestCoverLocation(activeMob);

        // 2. 脳の取得と学習
        LiquidBrain brain = brainStorage.computeIfAbsent(uuid, k -> new LiquidBrain(uuid));
        observeAndLearn(activeMob, brain);
        brain.digestExperience();

        // 3. バージョン選択
        String version = (activeMob.getLevel() >= 20) ? "v3" : (activeMob.getLevel() >= 10 ? "v2" : "v1");

        // --- 推論時間の計測開始 ---
        long startTime = System.nanoTime();

        BanditDecision decision = liquidEngine.think(version, context, brain, bukkitMob);

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0; // ナノ秒をミリ秒に変換
        // -----------------------

        // 4. ログ出力 (推論時間を追加)
        String uuidShort = uuid.toString().substring(0, 4);
        // 0.05ms以下なら非常に軽量、1.0msを超え始めると最適化の検討が必要な目安です
        System.out.println(String.format("[%s-%s][%s] Action: %s | Time: %.3fms | %s",
                activeMob.getType().getInternalName(),
                uuidShort,
                decision.engine_version,
                decision.decision.action_type,
                durationMs,
                decision.reasoning));

        // 5. 行動実行
        if (!bukkitMob.isDead()) {
            actuator.execute(activeMob, decision, nearestCover);
        } else {
            brain.dispose();
            brainStorage.remove(uuid);
        }
    }

    /**
     * [TQH Integrated] 仲間からの模倣学習
     * 成功体験の模倣だけでなく、システム温度（Temperature）の伝播と平衡化を行う。
     */
    private void observeAndLearn(ActiveMob self, LiquidBrain myBrain) {
        Mob bukkitSelf = (Mob) self.getEntity().getBukkitEntity();

        // 1. 周囲のエンティティを取得 (12m範囲)
        List<Entity> nearby = bukkitSelf.getNearbyEntities(12, 12, 12);

        for (int i = 0; i < nearby.size(); i++) {
            Entity e = nearby.get(i);
            UUID peerId = e.getUniqueId();

            LiquidBrain peerBrain = brainStorage.get(peerId);
            if (peerBrain == null || peerBrain == myBrain) continue;

            // --- 1. [TQH] 熱力学的平衡 (Thermal Equilibrium) ---
            // 仲間との間で「温度」が伝播する。
            // 平静な個体は周囲を冷やし、パニック（GAS状態）の個体は周囲を加熱する。
            float tempDiff = peerBrain.systemTemperature - myBrain.systemTemperature;
            myBrain.systemTemperature += tempDiff * 0.15f; // 熱伝導率 0.15

            // --- 2. 成功体験の模倣 (Q-Table Transfer) ---
            float myAdv = (float) myBrain.tacticalMemory.combatAdvantage;
            float peerAdv = (float) peerBrain.tacticalMemory.combatAdvantage;

            if (peerAdv > myAdv + 0.2f) {
                int peerSIdx = peerBrain.lastStateIdx;
                int peerAIdx = peerBrain.lastActionIdx;

                if (peerSIdx >= 0 && peerAIdx >= 0) {
                    // オフポリス学習：仲間が成功した行動を統合
                    // TQH版 updateTQH を使用。成功体験の模倣は「小規模な冷却」を伴う。
                    float imitationReward = 0.05f;
                    myBrain.accumulatedReward += imitationReward;

                    // 成功体験を学ぶことで、システムはわずかに安定（冷却）する
                    myBrain.systemTemperature -= 0.02f;
                }
            }

            // --- 3. 集合知の再確認 ---
            Entity myTarget = bukkitSelf.getTarget();
            if (myTarget != null) {
                String peerWeakness = CollectiveKnowledge.getGlobalWeakness(myTarget.getUniqueId());
                if (!peerWeakness.equals("NONE")) {
                    // 攻略法を知ることで迷い（Frustration）を軽減し、温度を安定させる
                    myBrain.frustration *= 0.8f;
                    myBrain.systemTemperature *= 0.95f;
                }
            }

            // --- 4. リキッドパラメータの同期 (TQH拡張) ---
            // 冷静さ (Composure) の伝播
            float composureDiff = peerBrain.composure - myBrain.composure;
            myBrain.composure += composureDiff * 0.1f;

            // Aggression / Fear の同期 (LiquidNeuron.mimic)
            // [注意] mimicによって、ニューロンのbaseDecay（結晶化時のベースライン）が同期されます
            myBrain.aggression.mimic(peerBrain.aggression, 0.1f);
            myBrain.fear.mimic(peerBrain.fear, 0.1f);
        }

        // [2026-01-12] 模倣による温度変化後、即座に相転移をチェック
        // これにより、仲間に同調して FLASH の色が変わる演出が成立する
        myBrain.reshapeTopology();
    }

    public void clearBrain(UUID uuid) { brainStorage.remove(uuid); }

    public LiquidBrain getBrain(UUID uuid) {
        // 脳がまだない場合は作成して返す（これによってリスナー経由でも脳が初期化される）
        return brainStorage.computeIfAbsent(uuid, k -> new LiquidBrain(uuid));
    }

    public boolean hasBrain(UUID uuid) {
        return brainStorage.containsKey(uuid);
    }
}