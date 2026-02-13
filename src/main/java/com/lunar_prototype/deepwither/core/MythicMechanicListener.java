package com.lunar_prototype.deepwither.core;

import com.lunar_prototype.deepwither.CustomDamageMechanics;
import com.lunar_prototype.deepwither.CustomHPDamageMechanic;
import com.lunar_prototype.deepwither.mythic.ManaShieldMechanic;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class MythicMechanicListener implements Listener, IManager {

    private final JavaPlugin plugin;

    public MythicMechanicListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
    }

    @EventHandler
    public void onMythicMechanicLoad(MythicMechanicLoadEvent event) {
        plugin.getLogger().info("MythicMechanicLoadEvent called for mechanic " + event.getMechanicName());

        if (event.getMechanicName().equalsIgnoreCase("CustomDamage")) {
            event.register(new CustomDamageMechanics(event.getConfig()));
            plugin.getLogger().info("-- Registered CustomDamage mechanic!");
        }

        if (event.getMechanicName().equalsIgnoreCase("CustomHPDamage")) {
            event.register(new CustomHPDamageMechanic(event.getConfig()));
            plugin.getLogger().info("-- Registered CustomHPDamage mechanic!");
        }

        if (event.getMechanicName().equalsIgnoreCase("manaShield")) {
            event.register(new ManaShieldMechanic(event.getConfig()));
            plugin.getLogger().info("-- Registered manaShield mechanic!");
        }
    }
}
