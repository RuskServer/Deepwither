package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.UUID;

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

        // 2. 集合知に「プレイヤーの危険度」を報告 (CollectiveKnowledge側もfloat/int対応を想定)
        CollectiveKnowledge.reportAllyDeath(killer, 0);

        // 3. 周囲の仲間に「直接的な心理的ショック」を与える (半径15m)
        List<Entity> nearby = victim.getNearbyEntities(15, 15, 15);

        for (int i = 0; i < nearby.size(); i++) {
            Entity entity = nearby.get(i);
            LiquidBrain nearbyBrain = aiEngine.getBrain(entity.getUniqueId());

            // --- コンパイルエラー対策: floatへの明示的キャスト ---

            // 距離をfloatで計算
            float dist = (float) entity.getLocation().distance(victimLoc);
            // ショック値を計算 (1.0fなどのリテラルを使用してfloat演算を強制)
            float shock = Math.max(0.1f, 1.0f - (dist / 15.0f));

            // 恐怖(Fear)を注入: update(input: float, urgency: float)
            // 仲間が死んだショックにより、恐怖ニューロンを急上昇させる
            nearbyBrain.fear.update(1.0f, shock * 0.5f);

            // アドレナリン(Adrenaline)を上昇
            nearbyBrain.adrenaline = Math.min(1.0f, nearbyBrain.adrenaline + (shock * 0.3f));

            // 戦術(Tactical)ニューロンも刺激
            nearbyBrain.tactical.update(1.0f, 0.4f);

            // 冷静さを失う (Composureを微減)
            nearbyBrain.composure = Math.max(0.0f, nearbyBrain.composure - (shock * 0.2f));
        }
    }
}