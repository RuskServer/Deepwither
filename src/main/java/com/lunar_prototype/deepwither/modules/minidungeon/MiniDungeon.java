package com.lunar_prototype.deepwither.modules.minidungeon;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MiniDungeon {
    
    private final String id;
    private Location hologramLocation;
    private final List<Location> spawnLocations = new ArrayList<>();
    private Location chestLocation;
    private final List<String> mobsToSpawn = new ArrayList<>();
    private String lootTemplate;

    // Runtime state
    private int cooldownTimer; // 0 to 300 (seconds)
    private final List<UUID> activeMobs = new ArrayList<>();
    private boolean isActive = false;
    private double startedProgress = 0.0;
    private UUID textDisplayUuid = null;

    public MiniDungeon(String id) {
        this.id = id;
        this.cooldownTimer = 300; // max cooldown by default
    }

    public String getId() {
        return id;
    }

    public Location getHologramLocation() {
        return hologramLocation;
    }

    public void setHologramLocation(Location hologramLocation) {
        this.hologramLocation = hologramLocation;
    }

    public List<Location> getSpawnLocations() {
        return spawnLocations;
    }

    public void addSpawnLocation(Location location) {
        spawnLocations.add(location);
    }
    
    public void clearSpawnLocations() {
        spawnLocations.clear();
    }

    public Location getChestLocation() {
        return chestLocation;
    }

    public void setChestLocation(Location chestLocation) {
        this.chestLocation = chestLocation;
    }

    public List<String> getMobsToSpawn() {
        return mobsToSpawn;
    }

    public void addMobToSpawn(String mobId) {
        mobsToSpawn.add(mobId);
    }
    
    public void clearMobsToSpawn() {
        mobsToSpawn.clear();
    }

    public String getLootTemplate() {
        return lootTemplate;
    }

    public void setLootTemplate(String lootTemplate) {
        this.lootTemplate = lootTemplate;
    }

    public int getCooldownTimer() {
        return cooldownTimer;
    }

    public void setCooldownTimer(int cooldownTimer) {
        this.cooldownTimer = cooldownTimer;
    }

    public List<UUID> getActiveMobs() {
        return activeMobs;
    }

    public void addActiveMob(UUID uuid) {
        activeMobs.add(uuid);
    }

    public void removeActiveMob(UUID uuid) {
        activeMobs.remove(uuid);
    }
    
    public void clearActiveMobs() {
        activeMobs.clear();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public double getStartedProgress() {
        return startedProgress;
    }

    public void setStartedProgress(double startedProgress) {
        this.startedProgress = startedProgress;
    }

    public UUID getTextDisplayUuid() {
        return textDisplayUuid;
    }

    public void setTextDisplayUuid(UUID textDisplayUuid) {
        this.textDisplayUuid = textDisplayUuid;
    }
    
    // Config Serialization Helpers
    public boolean isValid() {
        return hologramLocation != null && chestLocation != null && !mobsToSpawn.isEmpty() && lootTemplate != null;
    }
}
