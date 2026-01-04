package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
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

        // 2. 集合知に「プレイヤーの危険度」と「集団の恐怖」を報告
        // これにより、まだ戦っていない個体もこのプレイヤーを警戒し始める
        CollectiveKnowledge.reportAllyDeath(killer, 0);

        // 3. 周囲の仲間に「直接的な心理的ショック」を与える
        // 半径15m以内のエンティティを走査
        List<Entity> nearby = victim.getNearbyEntities(15, 15, 15);
        for (Entity entity : nearby) {
            // 現在AIが管理している(脳を持っている)個体かどうかを確認
            if (aiEngine.hasBrain(entity.getUniqueId())) {
                LiquidBrain nearbyBrain = aiEngine.getBrain(entity.getUniqueId());

                // 【視覚的・社会的学習】
                // 仲間が死ぬのを目撃したことで、恐怖値を直接跳ね上げる
                // 距離が近いほどショックが大きい
                double dist = entity.getLocation().distance(victim.getLocation());
                double shock = Math.max(0.1, 1.0 - (dist / 15.0));

                // 恐怖(Fear)を注入し、アドレナリン(Adrenaline)を上昇させる
                nearbyBrain.fear.update(1.0, shock * 0.5);
                nearbyBrain.adrenaline = Math.min(1.0, nearbyBrain.adrenaline + (shock * 0.3));

                // 戦術(Tactical)も刺激され、より慎重な位置取りを考えるようになる
                nearbyBrain.tactical.update(1.0, 0.4);
            }
        }
    }
}