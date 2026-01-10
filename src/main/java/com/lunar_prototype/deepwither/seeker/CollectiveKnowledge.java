package com.lunar_prototype.deepwither.seeker;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CollectiveKnowledge {
    // スレッドセーフなマップに変更
    public static Map<UUID, PlayerTacticalProfile> playerProfiles = new ConcurrentHashMap<>();

    public static double globalAggressionBias = 0.0;
    public static double globalFearBias = 0.0;

    /**
     * プレイヤーごとの戦術プロファイル
     */
    public static class PlayerTacticalProfile {
        public double dangerLevel = 0.0;
        public String preferredWeapon = "UNKNOWN";
        public String identifiedWeakness = "NONE"; // 例: "CLOSE_QUARTERS", "LONG_RANGE"
        public long lastInteractionTick;

        public void decay() {
            dangerLevel *= 0.95; // 時間経過で脅威を忘れる
        }
    }

    /**
     * 特定のプレイヤーに対する「共通の対策」を取得
     */
    public static String getGlobalWeakness(UUID uuid) {
        PlayerTacticalProfile profile = playerProfiles.get(uuid);
        return (profile != null) ? profile.identifiedWeakness : "NONE";
    }

    /**
     * 学習の共有：一人が見つけた弱点を群れ全体に登録
     */
    public static void updateGlobalTactics(Player player, String weakness) {
        PlayerTacticalProfile profile = playerProfiles.computeIfAbsent(player.getUniqueId(), k -> new PlayerTacticalProfile());
        profile.identifiedWeakness = weakness;
        profile.lastInteractionTick = player.getWorld().getFullTime();

        // 弱点を見つけた＝攻勢を強める
        globalAggressionBias = Math.min(1.0, globalAggressionBias + 0.05);
    }

    /**
     * 仲間が殺された際の報告を強化
     */
    public static void reportAllyDeath(Player killer, double dist) {
        globalFearBias = Math.min(1.0, globalFearBias + (dist < 10 ? 0.2 : 0.05));

        PlayerTacticalProfile profile = playerProfiles.computeIfAbsent(killer.getUniqueId(), k -> new PlayerTacticalProfile());
        profile.dangerLevel += 0.5;

        // 殺された時の状況（武器）をプロファイリング
        profile.preferredWeapon = killer.getInventory().getItemInMainHand().getType().name();

        // 仲間が殺されたことで、周囲の個体の「フラストレーション」を底上げするシグナル
        globalAggressionBias = Math.min(1.0, globalAggressionBias + 0.1);
    }

    public static double getDangerLevel(UUID uuid) {
        PlayerTacticalProfile profile = playerProfiles.get(uuid);
        return (profile != null) ? profile.dangerLevel : 0.0;
    }

    /**
     * 定期的な記憶の減退（エンジンのTick等で呼ぶ）
     */
    public static void tick() {
        playerProfiles.values().forEach(PlayerTacticalProfile::decay);
        globalFearBias *= 0.99;
        globalAggressionBias *= 0.99;
    }
}