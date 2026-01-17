package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

public class SocialLearningListener implements Listener {

    private final SeekerAIEngine aiEngine;

    public SocialLearningListener(SeekerAIEngine aiEngine) {
        this.aiEngine = aiEngine;
    }

    @EventHandler
    public void onMobDeath(MythicMobDeathEvent event) {
        // 1. プレイヤーによって倒されたか確認
        if (!(event.getKiller() instanceof Player killer)) {
            return;
        }

        Entity victim = event.getEntity();
        Location victimLoc = victim.getLocation();

        // 2. 集合知に「プレイヤーの危険度」を報告
        CollectiveKnowledge.reportAllyDeath(killer, 0);

        // 3. 周囲の仲間に「熱力学的ショック」を与える (半径15m)
        List<Entity> nearby = victim.getNearbyEntities(15, 15, 15);

        for (int i = 0; i < nearby.size(); i++) {
            Entity entity = nearby.get(i);
            LiquidBrain nearbyBrain = aiEngine.getBrain(entity.getUniqueId());
            if (nearbyBrain == null) continue;

            // 距離を量子化(float)して計算
            float dist = (float) entity.getLocation().distance(victimLoc);

            // ショック強度 (近いほど激しい)
            float shock = Math.max(0.1f, 1.0f - (dist / 15.0f));

            // --- [TQH] 熱力学的介入 ---

            // 仲間が死んだという強烈な「予測誤差」をシステム温度に変換
            // 仲間の死は「負の報酬（TD誤差）」の極致として、系を急激に加熱（液体化〜気体化）させる
            nearbyBrain.systemTemperature += shock * 0.8f;

            // 恐怖(Fear)を注入: update(input, urgency, systemTemperature)
            // 温度が高い状態で更新されるため、恐怖への反応が「サラサラ（即時的）」になる
            nearbyBrain.fear.update(1.0f, 0.8f, nearbyBrain.systemTemperature);

            // アドレナリンを上昇
            nearbyBrain.adrenaline = Math.min(1.0f, nearbyBrain.adrenaline + (shock * 0.5f));

            // 戦術(Tactical)を刺激
            nearbyBrain.tactical.update(0.7f, 0.4f, nearbyBrain.systemTemperature);

            // 冷静さを失う (Composureを減少)
            nearbyBrain.composure = Math.max(0.0f, nearbyBrain.composure - (shock * 0.3f));

            // 不満度(Frustration)も上昇 (逃げ場がない、あるいは勝てないというストレス)
            nearbyBrain.frustration = Math.min(1.0f, nearbyBrain.frustration + (shock * 0.4f));

            // --- [2026-01-12] 構造的再編の即時実行 ---
            // 温度が上がったため、即座に脳の接続（トポロジー）を書き換える
            // これにより、次のTickからマゼンタ（GAS）のパーティクルが出て動きが変わる
            nearbyBrain.reshapeTopology();
        }
    }
}