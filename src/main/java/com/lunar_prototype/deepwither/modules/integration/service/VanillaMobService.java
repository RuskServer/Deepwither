package com.lunar_prototype.deepwither.modules.integration.service;

import org.bukkit.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;

public class VanillaMobService implements IMobService {
    /**
     * Gets the mob identifier for the given living entity.
     *
     * @param entity the living entity whose mob identifier will be returned
     * @return the mob identifier as the entity type's name
     */
    @Override
    public String getMobId(LivingEntity entity) {
        return entity.getType().name();
    }

    /**
     * Candidate mob identifiers suitable for quests at the specified tier.
     *
     * @param tier the quest tier for which candidates are requested
     * @return a list of mob ID strings (for example: "ZOMBIE", "SKELETON", "SPIDER")
     */
    @Override
    public List<String> getQuestCandidateMobIdsByTier(int tier) {
        List<String> candidates = new ArrayList<>();
        candidates.add("ZOMBIE");
        candidates.add("SKELETON");
        candidates.add("SPIDER");
        return candidates;
    }
}