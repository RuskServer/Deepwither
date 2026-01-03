package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SeekerAIEngine {

    private final SensorProvider sensorProvider;
    private final LiquidCombatEngine liquidEngine;
    private final Actuator actuator;

    // 個体ごとの脳の状態を保持するメモリ (永続性を持たせるため)
    private final Map<UUID, LiquidBrain> brainStorage = new HashMap<>();

    public SeekerAIEngine() {
        this.sensorProvider = new SensorProvider();
        this.liquidEngine = new LiquidCombatEngine();
        this.actuator = new Actuator();
    }

    /**
     * バンディットの思考ルーチンを実行
     */
    public void tick(ActiveMob activeMob) {
        UUID uuid = activeMob.getUniqueId();

        // --- 0. BukkitのMobエンティティを取得・チェック ---
        // これを最初に行うことで、キャストエラーを防ぎつつ、後続の処理に渡せるようにします
        if (activeMob.getEntity() == null || !(activeMob.getEntity().getBukkitEntity() instanceof Mob)) {
            return;
        }
        Mob bukkitMob = (Mob) activeMob.getEntity().getBukkitEntity();

        // 1. 感知
        Location nearestCover = sensorProvider.findNearestCoverLocation(activeMob);
        BanditContext context = sensorProvider.scan(activeMob);

        // 2. 脳の取得 (初対面のMobなら脳を新規作成)
        LiquidBrain brain = brainStorage.computeIfAbsent(uuid, k -> new LiquidBrain(bukkitMob.getUniqueId()));

        brain.digestExperience();

        // 3. リキッド演算 (適応的思考)
        // 修正ポイント: 第3引数にさきほど取得した bukkitMob を渡します
        BanditDecision decision = liquidEngine.think(context, brain, bukkitMob);

        // --- ログ出力セクション ---
        String mobName = activeMob.getType().getInternalName();
        String uuidShort = uuid.toString().substring(0, 4);

        System.out.println(String.format("[%s-%s] Action: %s | %s",
                mobName, uuidShort, decision.decision.action_type, decision.reasoning));

        // 4. 行動実行
        // すでに上で生存・型チェック済みなので、ここでは単純に実行します
        if (!bukkitMob.isDead()) {
            actuator.execute(activeMob, decision, nearestCover);
        } else {
            // 死んだら脳をメモリから消去
            brainStorage.remove(uuid);
        }
    }

    // SeekerAIEngine.java に追加
    public LiquidBrain getBrain(UUID uuid) {
        // 脳がまだない場合は作成して返す（これによってリスナー経由でも脳が初期化される）
        return brainStorage.computeIfAbsent(uuid, k -> new LiquidBrain(uuid));
    }

    public void shutdown() {
        brainStorage.clear();
    }

    // Mobがデスポーンした時などに呼ぶクリーナーメソッドがあると良い
    public void clearBrain(UUID uuid) {
        brainStorage.remove(uuid);
    }
}