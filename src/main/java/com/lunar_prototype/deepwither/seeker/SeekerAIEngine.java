package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SeekerAIEngine {

    private final SensorProvider sensorProvider;
    private final LiquidCombatEngine liquidEngine;
    private final Actuator actuator;
    private final Map<UUID, LiquidBrain> brainStorage = new HashMap<>();

    public SeekerAIEngine() {
        this.sensorProvider = new SensorProvider();
        this.liquidEngine = new LiquidCombatEngine();
        this.actuator = new Actuator();
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
            brainStorage.remove(uuid);
        }
    }

    /**
     * 仲間からの模倣学習 (量子化最適化版)
     * Streamを排除し、インデックスベースのQ-Table同期を行う
     */
    private void observeAndLearn(ActiveMob self, LiquidBrain myBrain) {
        Mob bukkitSelf = (Mob) self.getEntity().getBukkitEntity();

        // 1. 周囲のエンティティを取得 (12m範囲)
        List<Entity> nearby = bukkitSelf.getNearbyEntities(12, 12, 12);

        for (int i = 0; i < nearby.size(); i++) {
            Entity e = nearby.get(i);
            UUID peerId = e.getUniqueId();

            // brainStorage から仲間の脳を取得 (Map.get は依然必要だが、中身の演算を軽量化)
            LiquidBrain peerBrain = brainStorage.get(peerId);
            if (peerBrain == null || peerBrain == myBrain) continue;

            // --- 1. 成功体験の模倣 (Q-Table Transfer) ---
            // 仲間が自分より明らかに優勢（CombatAdvantageが高い）な場合
            float myAdv = (float) myBrain.tacticalMemory.combatAdvantage;
            float peerAdv = (float) peerBrain.tacticalMemory.combatAdvantage;

            if (peerAdv > myAdv + 0.2f) {
                // インデックスベースで「状態」と「行動」をコピー
                int peerSIdx = peerBrain.lastStateIdx;
                int peerAIdx = peerBrain.lastActionIdx;

                if (peerSIdx >= 0 && peerAIdx >= 0) {
                    // オフポリス学習：仲間が成功した行動を自分のQ-Tableに 0.05f の報酬として統合
                    // 直接 update を呼ぶことで、Stringキー生成を完全に回避
                    myBrain.qTable.update(peerSIdx, peerAIdx, 0.05f, peerSIdx);
                }
            }

            // --- 2. 集合知の再確認 ---
            Entity myTarget = bukkitSelf.getTarget();
            if (myTarget != null) {
                UUID targetId = myTarget.getUniqueId();
                // 集合知に登録された弱点があるか確認
                String peerWeakness = CollectiveKnowledge.getGlobalWeakness(targetId);
                if (!peerWeakness.equals("NONE")) {
                    // 攻略法を知ることでフラストレーション（迷い）を軽減
                    myBrain.frustration *= 0.8f;
                }
            }

            // --- 3. 感情・リキッドパラメータの同期 ---
            // 冷静さ (Composure) の伝播
            float composureDiff = peerBrain.composure - myBrain.composure;
            myBrain.composure += composureDiff * 0.1f;

            // Aggression / Fear の同期 (LiquidNeuron.mimic を使用)
            // 引数が float, float に最適化されていることを想定
            myBrain.aggression.mimic(peerBrain.aggression, 0.1f);
            myBrain.fear.mimic(peerBrain.fear, 0.1f);
        }
    }

    public void clearBrain(UUID uuid) { brainStorage.remove(uuid); }

    public LiquidBrain getBrain(UUID uuid) {
        // 脳がまだない場合は作成して返す（これによってリスナー経由でも脳が初期化される）
        return brainStorage.computeIfAbsent(uuid, k -> new LiquidBrain(uuid));
    }

    public boolean hasBrain(UUID uuid) {
        return brainStorage.containsKey(uuid);
    }

    public void shutdown() {
        brainStorage.clear();
    }
}