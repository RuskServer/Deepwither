package com.lunar_prototype.deepwither.seeker;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Optional;

public class CombatExperienceListener implements Listener {
    private final SeekerAIEngine aiEngine;

    public CombatExperienceListener(SeekerAIEngine aiEngine) {
        this.aiEngine = aiEngine;
    }

    @EventHandler
    public void onCombat(EntityDamageByEntityEvent event) {
        // 1. Mobがダメージを与えた場合 (成功体験)
        handleReward(event.getDamager(), 0.3); // 0.3は適応強度

        // 2. Mobがダメージを受けた場合 (失敗体験)
        handlePenalty(event.getEntity(), 0.5);
    }

    private void handleReward(Entity entity, double amount) {
        getBrain(entity).ifPresent(brain -> {
            brain.accumulatedReward += amount;
        });
    }

    private void handlePenalty(Entity entity, double amount) {
        getBrain(entity).ifPresent(brain -> {
            brain.accumulatedPenalty += amount;
        });
    }

    private Optional<LiquidBrain> getBrain(Entity entity) {
        return MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId())
                .map(am -> aiEngine.getBrain(am.getUniqueId()));
    }
}