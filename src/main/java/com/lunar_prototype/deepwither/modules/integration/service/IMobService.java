package com.lunar_prototype.deepwither.modules.integration.service;

import org.bukkit.entity.LivingEntity;
import java.util.List;

public interface IMobService {
    /**
 * Obtain the identifier for the given entity.
 *
 * For vanilla entities the identifier is the entity's type name; for MythicMobs entities it is the MythicMobs mob name.
 *
 * @param entity the entity to identify
 * @return the mob identifier string (vanilla: type name; MythicMobs: mob name)
 */
    String getMobId(LivingEntity entity);

    /**
 * Provides candidate mob IDs suitable for quests at the specified tier.
 *
 * @param tier the quest tier for which candidate mob IDs are requested
 * @return a list of mob identifier strings considered suitable for the given tier
 */
    List<String> getQuestCandidateMobIdsByTier(int tier);
}