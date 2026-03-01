package com.lunar_prototype.deepwither.modules.mob.service;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobConfigService implements IManager {

    private final Deepwither plugin;
    private final Map<Integer, MobTierConfig> mobTierConfigs = new HashMap<>();

    public MobConfigService(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        loadMobTierConfigs();
    }

    @Override
    public void shutdown() {
        mobTierConfigs.clear();
    }

    private void loadMobTierConfigs() {
        mobTierConfigs.clear();
        ConfigurationSection mobSpawnsSection = plugin.getConfig().getConfigurationSection("mob_spawns");
        if (mobSpawnsSection == null) return;

        for (String tierKey : mobSpawnsSection.getKeys(false)) {
            try {
                int tierNumber = Integer.parseInt(tierKey);
                ConfigurationSection tierSection = mobSpawnsSection.getConfigurationSection(tierKey);
                if (tierSection == null) continue;

                int areaLevel = tierSection.getInt("area_level", 999);
                List<String> regularMobs = tierSection.getStringList("regular_mobs");
                List<String> banditMobs = tierSection.getStringList("bandit_mobs");

                Map<String, Double> miniBosses = new HashMap<>();
                ConfigurationSection bossSection = tierSection.getConfigurationSection("mini_bosses");
                if (bossSection != null) {
                    for (String mobId : bossSection.getKeys(false)) {
                        miniBosses.put(mobId, bossSection.getDouble(mobId));
                    }
                }
                mobTierConfigs.put(tierNumber, new MobTierConfig(areaLevel, regularMobs, banditMobs, miniBosses));
            } catch (NumberFormatException ignored) {}
        }
    }

    public MobTierConfig getTierConfig(int tier) {
        return mobTierConfigs.get(tier);
    }

    public static class MobTierConfig {
        private final int areaLevel;
        private final List<String> regularMobs;
        private final List<String> banditMobs;
        private final Map<String, Double> miniBosses;

        public MobTierConfig(int areaLevel, List<String> regularMobs, List<String> banditMobs, Map<String, Double> miniBosses) {
            this.areaLevel = areaLevel;
            this.regularMobs = regularMobs;
            this.banditMobs = banditMobs;
            this.miniBosses = miniBosses;
        }

        public int getAreaLevel() { return areaLevel; }
        public List<String> getRegularMobs() { return regularMobs; }
        public List<String> getBanditMobs() { return banditMobs; }
        public Map<String, Double> getMiniBosses() { return miniBosses; }
    }
}
