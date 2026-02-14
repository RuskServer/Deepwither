package com.lunar_prototype.deepwither.core.listener;

import com.lunar_prototype.deepwither.AttributeManager;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.LevelManager;
import com.lunar_prototype.deepwither.SkilltreeManager;
import com.lunar_prototype.deepwither.DailyTaskManager;
import com.lunar_prototype.deepwither.crafting.CraftingManager;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({
    LevelManager.class, 
    AttributeManager.class, 
    SkilltreeManager.class, 
    DailyTaskManager.class, 
    CraftingManager.class, 
    ProfessionManager.class
})
public class PlayerConnectionListener implements Listener, IManager {

    private final JavaPlugin plugin;
    private LevelManager levelManager;
    private AttributeManager attributeManager;
    private SkilltreeManager skilltreeManager;
    private DailyTaskManager dailyTaskManager;
    private CraftingManager craftingManager;
    private ProfessionManager professionManager;

    public PlayerConnectionListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Deepwither dw = Deepwither.getInstance();
        this.levelManager = dw.getLevelManager();
        this.attributeManager = dw.getAttributeManager();
        this.skilltreeManager = dw.getSkilltreeManager();
        this.dailyTaskManager = dw.getDailyTaskManager();
        this.craftingManager = dw.getCraftingManager();
        this.professionManager = dw.getProfessionManager();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
        // 必要に応じてリスナー解除などの処理
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        levelManager.load(e.getPlayer().getUniqueId());
        attributeManager.load(e.getPlayer().getUniqueId());
        skilltreeManager.load(e.getPlayer().getUniqueId());
        dailyTaskManager.loadPlayer(e.getPlayer());
        craftingManager.loadPlayer(e.getPlayer());
        professionManager.loadPlayer(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        levelManager.unload(e.getPlayer().getUniqueId());
        attributeManager.unload(e.getPlayer().getUniqueId());
        dailyTaskManager.saveAndUnloadPlayer(e.getPlayer().getUniqueId());
        craftingManager.saveAndUnloadPlayer(e.getPlayer().getUniqueId());
        professionManager.saveAndUnloadPlayer(e.getPlayer());
    }
}
