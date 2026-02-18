package com.lunar_prototype.deepwither.modules.integration.service;

import org.bukkit.entity.LivingEntity;
import java.util.List;

public interface IMobService {
    /**
     * エンティティの識別ID（VanillaならType名、MythicMobsならMob名）を取得します。
     */
    String getMobId(LivingEntity entity);

    /**
     * 指定されたティアに対応するクエスト用MobIDの候補リストを取得します。
     */
    List<String> getQuestCandidateMobIdsByTier(int tier);
}
