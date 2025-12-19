package com.lunar_prototype.deepwither.booster;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BoosterManager {
    // プレイヤーUUID -> ブーストデータ(倍率, 終了時刻)
    private final Map<UUID, BoosterData> activeBoosters = new ConcurrentHashMap<>();

    public static class BoosterData {
        public double multiplier;
        public long endTime;

        public BoosterData(double multiplier, long durationMillis) {
            this.multiplier = multiplier;
            this.endTime = System.currentTimeMillis() + durationMillis;
        }
    }

    /**
     * ブーストを付与する
     */
    public void addBooster(Player player, double multiplier, int minutes) {
        activeBoosters.put(player.getUniqueId(), new BoosterData(multiplier, (long) minutes * 60 * 1000));
    }

    /**
     * 現在有効な倍率を取得（期限切れなら1.0を返す）
     */
    public double getMultiplier(Player player) {
        BoosterData data = activeBoosters.get(player.getUniqueId());
        if (data == null) return 1.0;

        if (System.currentTimeMillis() > data.endTime) {
            activeBoosters.remove(player.getUniqueId());
            return 1.0;
        }
        return data.multiplier;
    }

    /**
     * 残り時間を秒で取得（表示用）
     */
    public long getRemainingSeconds(Player player) {
        BoosterData data = activeBoosters.get(player.getUniqueId());
        if (data == null) return 0;
        return Math.max(0, (data.endTime - System.currentTimeMillis()) / 1000);
    }
}