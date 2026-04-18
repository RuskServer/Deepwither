package com.lunar_prototype.deepwither.core.listener;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.playerdata.PlayerDataManager;
import com.lunar_prototype.deepwither.modules.economy.advancement.AdvancementManager;
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
        


        // 1ブロック分の段差をオートジャンプなしで登れるようにする設定
        org.bukkit.attribute.AttributeInstance stepHeight = e.getPlayer().getAttribute(org.bukkit.attribute.Attribute.STEP_HEIGHT);
        if (stepHeight != null) {
            boolean autoStep = Deepwither.getInstance().getSettingsManager().isEnabled(e.getPlayer(), com.lunar_prototype.deepwither.PlayerSettingsManager.SettingType.AUTO_STEP);
            stepHeight.setBaseValue(autoStep ? 1.0 : 0.6);
        }
        
        // 実績タブをプレイヤーに表示
        AdvancementManager am =
                com.lunar_prototype.deepwither.api.DW.get(AdvancementManager.class);
        if (am != null) {
            am.onPlayerJoin(e.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        playerDataManager.unloadDataAsync(e.getPlayer().getUniqueId());
    }
}
