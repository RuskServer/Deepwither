package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import java.util.HashMap;
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

        // 1. 環境感知 (15m以内LoS無視ロジックはSensorProvider内に実装されている前提)
        BanditContext context = sensorProvider.scan(activeMob);
        Location nearestCover = sensorProvider.findNearestCoverLocation(activeMob);

        // 2. 脳の取得と学習
        LiquidBrain brain = brainStorage.computeIfAbsent(uuid, k -> new LiquidBrain(uuid));
        observeAndLearn(activeMob, brain);
        brain.digestExperience();

        // 3. バージョン選択と意思決定
        // 例: レベル10以上の個体は将来的にV2エンジンを使用する準備
        String version;
        if (activeMob.getLevel() >= 20) {
            version = "v3";
        } else if (activeMob.getLevel() >= 10) {
            version = "v2";
        } else {
            version = "v1";
        }
        BanditDecision decision = liquidEngine.think(version, context, brain, bukkitMob);

        // 4. ログ出力
        String uuidShort = uuid.toString().substring(0, 4);
        System.out.println(String.format("[%s-%s][%s] Action: %s | %s",
                activeMob.getType().getInternalName(), uuidShort, version, decision.decision.action_type, decision.reasoning));

        // 5. 行動実行
        if (!bukkitMob.isDead()) {
            actuator.execute(activeMob, decision, nearestCover);
        } else {
            brainStorage.remove(uuid);
        }
    }

    private void observeAndLearn(ActiveMob self, LiquidBrain myBrain) {
        Mob bukkitSelf = (Mob) self.getEntity().getBukkitEntity();

        bukkitSelf.getNearbyEntities(12, 12, 12).stream()
                .filter(e -> brainStorage.containsKey(e.getUniqueId()))
                .forEach(e -> {
                    LiquidBrain peerBrain = brainStorage.get(e.getUniqueId());

                    // 1. 成功体験の模倣 (Q-Table Transfer)
                    // 仲間が大きな報酬を得ている（＝直近の行動が成功している）場合
                    if (peerBrain.tacticalMemory.combatAdvantage > myBrain.tacticalMemory.combatAdvantage + 0.2) {

                        if (peerBrain.lastStateKey != null && peerBrain.lastActionType != null) {
                            // 仲間の「状態」と「行動」を、自分のQ-Tableにも「良いもの」として微調整
                            // 自分で経験していないが、見て学ぶ（オフポリス学習の簡易版）
                            myBrain.qTable.update(
                                    peerBrain.lastStateKey,
                                    peerBrain.lastActionType,
                                    0.5, // 自分でやるよりは低い報酬値で「参考」にする
                                    "IMITATED_STATE"
                            );

                            // 模倣したことを推論ログに残す
                            // myBrain.lastReasoning += " | MIMIC: " + peerBrain.lastActionType;
                        }
                    }

                    // 2. 集合知の「再確認」
                    // 仲間が特定プレイヤーの弱点を見つけているなら、それを自分の脳にも強く刻む
                    if (bukkitSelf.getTarget() != null) {
                        UUID targetId = bukkitSelf.getTarget().getUniqueId();
                        String peerWeakness = CollectiveKnowledge.getGlobalWeakness(targetId);
                        if (!peerWeakness.equals("NONE")) {
                            // 集合知を介して、より確信を持って「弱点」を突くようにマインドセットを調整
                            myBrain.frustration *= 0.8; // 確信を得ることで迷いを減らす
                        }
                    }

                    // 3. 感情・リキッドパラメータの同期（既存の強化）
                    // 仲間の Composure（冷静さ）が高いなら学び、低い（パニック）なら伝染する
                    double composureDiff = peerBrain.composure - myBrain.composure;
                    myBrain.composure += composureDiff * 0.1; // 10%の速度で同期

                    // Aggression / Fear の同期（Liquid値のmimicを使用）
                    myBrain.aggression.mimic(peerBrain.aggression, 0.1);
                    myBrain.fear.mimic(peerBrain.fear, 0.1);
                });
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