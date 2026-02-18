package com.lunar_prototype.deepwither.modules.dynamic_quest.obj;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class QuestLocation implements ConfigurationSerializable {
    private final String name;
    private final Location pos;
    private final Location pos2; // Used as 'end' for Raids/Convoys
    private final int layerId;

    public QuestLocation(String name, Location pos, int layerId) {
        this(name, pos, null, layerId);
    }

    public QuestLocation(String name, Location pos, Location pos2, int layerId) {
        this.name = name;
        this.pos = pos;
        this.pos2 = pos2;
        this.layerId = layerId;
    }

    public String getName() {
        return name;
    }

    public Location getPos() {
        return pos;
    }

    public Location getPos2() {
        return pos2;
    }

    public int getLayerId() {
        return layerId;
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("pos", pos);
        if (pos2 != null) {
            map.put("pos2", pos2);
        }
        map.put("layerId", layerId);
        return map;
    }

    public static QuestLocation deserialize(Map<String, Object> map) {
        return new QuestLocation(
                (String) map.get("name"),
                (Location) map.get("pos"),
                (Location) map.getOrDefault("pos2", null),
                (int) map.getOrDefault("layerId", 0)
        );
    }
}
