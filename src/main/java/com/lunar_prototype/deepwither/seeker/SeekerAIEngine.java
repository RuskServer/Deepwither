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
        String version = (activeMob.getLevel() >= 10) ? "v2" : "v1";
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
        self.getEntity().getBukkitEntity().getNearbyEntities(10, 10, 10).stream()
                .filter(e -> brainStorage.containsKey(e.getUniqueId()))
                .forEach(e -> {
                    LiquidBrain peerBrain = brainStorage.get(e.getUniqueId());
                    if (peerBrain.aggression.get() > myBrain.aggression.get() + 0.2) {
                        myBrain.aggression.mimic(peerBrain.aggression, 0.05);
                        myBrain.fear.mimic(peerBrain.fear, 0.05);
                        if (peerBrain.composure > myBrain.composure) {
                            myBrain.composure += (peerBrain.composure - myBrain.composure) * 0.05;
                        }
                    }
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