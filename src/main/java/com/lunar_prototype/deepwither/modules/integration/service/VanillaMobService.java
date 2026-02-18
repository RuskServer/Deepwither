package com.lunar_prototype.deepwither.modules.integration.service;

import org.bukkit.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;

public class VanillaMobService implements IMobService {
    @Override
    public String getMobId(LivingEntity entity) {
        return entity.getType().name();
    }

    @Override
    public List<String> getQuestCandidateMobIdsByTier(int tier) {
        List<String> candidates = new ArrayList<>();
        candidates.add("ZOMBIE");
        candidates.add("SKELETON");
        candidates.add("SPIDER");
        return candidates;
    }
}
