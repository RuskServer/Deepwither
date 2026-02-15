package com.lunar_prototype.deepwither.core.listener;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.playerdata.PlayerDataManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({
    PlayerDataManager.class
})
public class PlayerConnectionListener implements Listener, IManager {

    private final JavaPlugin plugin;
    private PlayerDataManager playerDataManager;

    public PlayerConnectionListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.playerDataManager = Deepwither.getInstance().getPlayerDataManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        playerDataManager.loadData(e.getPlayer().getUniqueId());
        
        // 追加: まだPlayerDataManagerに完全に統合されていないロード処理
        Deepwither dw = Deepwither.getInstance();
        dw.getDailyTaskManager().loadPlayer(e.getPlayer());
        dw.getCraftingManager().loadPlayer(e.getPlayer());
        dw.getProfessionManager().loadPlayer(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        playerDataManager.unloadData(e.getPlayer().getUniqueId());
    }
}
