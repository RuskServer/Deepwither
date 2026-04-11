package com.lunar_prototype.deepwither.fasttravel;

import org.bukkit.Location;
import org.bukkit.Material;

public class FastTravelPoint {
    private final String id;
    private final String displayName;
    private final Location location;
    private final Material icon;

    public FastTravelPoint(String id, String displayName, Location location, Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.location = location;
        this.icon = icon;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Location getLocation() { return location; }
    public Material getIcon() { return icon; }
}
