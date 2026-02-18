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

    /**
     * Creates a new repository that manages persisted quest locations for the given plugin.
     *
     * @param plugin the main Deepwither plugin instance used for accessing the plugin data folder and logging
     */
    public QuestLocationRepository(Deepwither plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads dynamic quest locations from the plugin's dynamic_quest_locations.yml into memory.
     *
     * Ensures the YAML file exists, reads configuration entries under "locations.{QUEST_TYPE}",
     * converts valid entries into QuestLocation objects, populates the repository's in-memory map,
     * and logs the total number of loaded locations.
     */
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

    /**
     * Persists the in-memory quest location lists to the plugin's dynamic_quest_locations.yml configuration file.
     *
     * If the repository has not been initialized (configuration or file is null), the method logs a warning and does nothing.
     */
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

    /**
     * Adds or replaces a quest location for the specified quest type and persists the change.
     *
     * If a location with the same name (case-insensitive) already exists for the type, it is removed
     * before the provided location is added. The repository is saved after the update.
     *
     * @param type the quest type to which the location belongs
     * @param loc  the quest location to add or replace
     */
    public void addLocation(QuestType type, QuestLocation loc) {
        List<QuestLocation> list = questLocations.computeIfAbsent(type, k -> new ArrayList<>());
        list.removeIf(l -> l.getName().equalsIgnoreCase(loc.getName()));
        list.add(loc);
        save();
    }

    /**
     * Retrieve the configured quest locations for the specified quest type.
     *
     * @param type the quest type whose locations to retrieve
     * @return an unmodifiable list of {@link QuestLocation} objects for the given type, or an empty list if none exist
     */
    public List<QuestLocation> getLocations(QuestType type) {
        List<QuestLocation> list = questLocations.get(type);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    /**
     * Finds a quest location with the given name for the specified quest type.
     *
     * @param type the quest type to search within
     * @param name the name of the quest location to find (case-insensitive)
     * @return the matching {@code QuestLocation} if present, or {@code null} if no match is found
     */
    public QuestLocation getLocation(QuestType type, String name) {
        List<QuestLocation> list = questLocations.get(type);
        if (list == null) return null;
        return list.stream().filter(l -> l.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}