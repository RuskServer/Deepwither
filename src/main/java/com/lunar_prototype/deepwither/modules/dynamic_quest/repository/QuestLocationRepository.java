package com.lunar_prototype.deepwither.modules.dynamic_quest.repository;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.modules.dynamic_quest.enums.QuestType;
import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.QuestLocation;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class QuestLocationRepository {

    private final Deepwither plugin;
    private final Map<QuestType, List<QuestLocation>> questLocations = new HashMap<>();
    private File locationsFile;
    private FileConfiguration locationsConfig;

    public QuestLocationRepository(Deepwither plugin) {
        this.plugin = plugin;
    }

    public void load() {
        questLocations.clear();
        locationsFile = new File(plugin.getDataFolder(), "dynamic_quest_locations.yml");
        if (!locationsFile.exists()) {
            try {
                boolean created = locationsFile.createNewFile();
                if (!created) {
                    plugin.getLogger().warning("[DynamicQuest] Failed to create dynamic_quest_locations.yml (file not created).");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        locationsConfig = YamlConfiguration.loadConfiguration(locationsFile);

        for (QuestType type : QuestType.values()) {
            List<?> list = locationsConfig.getList("locations." + type.name());
            if (list != null) {
                List<QuestLocation> qLocs = new ArrayList<>();
                for (Object obj : list) {
                    if (obj instanceof QuestLocation) {
                        qLocs.add((QuestLocation) obj);
                    }
                }
                questLocations.put(type, qLocs);
            }
        }
        plugin.getLogger().info("[DynamicQuest] Loaded " + questLocations.values().stream().mapToInt(List::size).sum() + " custom locations.");
    }

    public void save() {
        if (locationsConfig == null || locationsFile == null) {
            plugin.getLogger().warning("[DynamicQuest] save() called before load(); skipping.");
            return;
        }
        for (Map.Entry<QuestType, List<QuestLocation>> entry : questLocations.entrySet()) {
            locationsConfig.set("locations." + entry.getKey().name(), entry.getValue());
        }
        try {
            locationsConfig.save(locationsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addLocation(QuestType type, QuestLocation loc) {
        List<QuestLocation> list = questLocations.computeIfAbsent(type, k -> new ArrayList<>());
        list.removeIf(l -> l.getName().equalsIgnoreCase(loc.getName()));
        list.add(loc);
        save();
    }

    public List<QuestLocation> getLocations(QuestType type) {
        List<QuestLocation> list = questLocations.get(type);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    public QuestLocation getLocation(QuestType type, String name) {
        List<QuestLocation> list = questLocations.get(type);
        if (list == null) return null;
        return list.stream().filter(l -> l.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
