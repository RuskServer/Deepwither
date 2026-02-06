package com.lunar_prototype.deepwither.seeker;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@DependsOn({})
public class SeekerAIEngine implements IManager {

    private final SensorProvider sensorProvider;
    private final LiquidCombatEngine liquidEngine;
    private final Actuator actuator;
    private final Map<UUID, LiquidBrain> brainStorage = new HashMap<>();

    public SeekerAIEngine() {
        this.sensorProvider = new SensorProvider();
        this.liquidEngine = new LiquidCombatEngine();
        this.actuator = new Actuator();
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        for (LiquidBrain brain : brainStorage.values()) {
            brain.dispose();
        }
        brainStorage.clear();
    }