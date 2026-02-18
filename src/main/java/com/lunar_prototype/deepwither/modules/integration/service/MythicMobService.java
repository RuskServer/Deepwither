package com.lunar_prototype.deepwither.modules.integration.service;

import com.lunar_prototype.deepwither.MobSpawnManager;
import com.lunar_prototype.deepwither.api.DW;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * MythicMobs APIへの依存をこのクラス内にカプセル化します。
 * MythicMobsが存在しない環境では、クラス検証時に失敗するため、
 * インスタンス化はガードされた条件下でのみ行う必要があります。
 */
public class MythicMobService implements IMobService {

    @Override
    public String getMobId(LivingEntity entity) {
        return MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId())
                .map(am -> am.getType().getInternalName())
                .orElse(entity.getType().name());
    }

    @Override
    public List<String> getQuestCandidateMobIdsByTier(int tier) {
        MobSpawnManager mobManager = DW.get(MobSpawnManager.class);
        if (mobManager != null) {
            return mobManager.getQuestCandidateMobIdsByTier(tier);
        }
        return new ArrayList<>();
    }
}
