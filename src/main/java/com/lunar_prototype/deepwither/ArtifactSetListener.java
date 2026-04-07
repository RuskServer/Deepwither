package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({ArtifactManager.class})
public class ArtifactSetListener implements Listener, IManager {

    private final JavaPlugin plugin;

    public ArtifactSetListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeepwitherDamage(DeepwitherDamageEvent event) {
        Deepwither.getInstance().getArtifactManager().handleArtifactSetTrigger(event);
    }
}
