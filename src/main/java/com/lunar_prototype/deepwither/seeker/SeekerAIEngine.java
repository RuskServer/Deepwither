package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SeekerAIEngine {

    private final JavaPlugin plugin;
    private final SensorProvider sensorProvider;
    private final LLMConnector llmConnector;
    private final Actuator actuator;

    // 推論中の個体が二重にリクエストを送らないためのロック用
    private final Map<UUID, Long> thinkingCache = new HashMap<>();
    private static final long THINKING_TIMEOUT_MS = 5000; // 5秒以上返答がなければタイムアウト

    public SeekerAIEngine(JavaPlugin plugin, String modelPath) throws Exception {
        this.plugin = plugin;
        this.sensorProvider = new SensorProvider();
        this.llmConnector = new LLMConnector(modelPath); // ONNXセッションの初期化
        this.actuator = new Actuator();
    }

    /**
     * バンディットの思考ルーチンを1ステップ実行
     * 通常、BukkitRunnableなどで定期的に（例：20~40ticksごと）呼び出す
     */
    public void tick(ActiveMob activeMob) {
        UUID uuid = activeMob.getUniqueId();

        // 1. 推論中チェック（二重リクエスト防止）
        if (thinkingCache.containsKey(uuid)) {
            if (System.currentTimeMillis() - thinkingCache.get(uuid) < THINKING_TIMEOUT_MS) {
                return; // まだ考え中
            }
        }

        // 2. 感知層（Sensors）による状況把握
        // 遮蔽物座標はLLMから返ってきた時に必要なので、ここで一旦計算して保持
        Location nearestCover = sensorProvider.findNearestCoverLocation(activeMob);
        BanditContext context = sensorProvider.scan(activeMob);

        // 3. 推論開始（非同期）
        thinkingCache.put(uuid, System.currentTimeMillis());

        llmConnector.fetchDecisionAsync(context, decision -> {
            try {
                // 4. 行動層（Actuators）による実行
                // 非同期コールバック内だが、LLMConnector側でBukkitメインスレッドへの書き戻しを保証している前提
                if (activeMob.getEntity() != null && !activeMob.getEntity().isDead()) {
                    actuator.execute(activeMob, decision, nearestCover);
                }
            } finally {
                // 思考完了、ロック解除
                thinkingCache.remove(uuid);
            }
        });
    }

    /**
     * プラグイン終了時にONNXセッションを解放
     */
    public void shutdown() {
        try {
            llmConnector.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}