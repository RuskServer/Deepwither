package com.lunar_prototype.deepwither;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public boolean isOnCooldown(UUID uuid, String skillId, double baseCooldown) {
        double actualCooldown = applyCooldownReduction(uuid, baseCooldown);
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(uuid, new HashMap<>()).getOrDefault(skillId, 0L);
        return (now - last) < (actualCooldown * 1000);
    }

    public double getRemaining(UUID uuid, String skillId, double baseCooldown) {
        double actualCooldown = applyCooldownReduction(uuid, baseCooldown);
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(uuid, new HashMap<>()).getOrDefault(skillId, 0L);
        double remaining = (actualCooldown * 1000 - (now - last)) / 1000.0;
        return Math.max(0, remaining);
    }

    private double applyCooldownReduction(UUID uuid, double baseCooldown) {
        double reduction = StatManager.getTotalStatsFromEquipment(Bukkit.getPlayer(uuid)).getFlat(StatType.COOLDOWN_REDUCTION);

        reduction = Math.min(reduction, 0.9); // 安全のため上限90%
        return baseCooldown * (1.0 - reduction);
    }

    public void setCooldown(UUID uuid, String skillId) {
        cooldowns.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(skillId, System.currentTimeMillis());
    }
}
