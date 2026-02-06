package com.lunar_prototype.deepwither.listeners;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({})
public class PvPWorldListener implements Listener, IManager {

    private final String PVP_WORLD_NAME = "pvp";
    private final JavaPlugin plugin;

    public PvPWorldListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    // ブロック破壊
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (isPvPWorldProtectionActive(player)) {
            e.setCancelled(true);
            player.sendMessage("§cこのワールドではブロックの破壊は禁止されています。");
        }
    }

    // ブロック設置
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (isPvPWorldProtectionActive(player)) {
            e.setCancelled(true);
            player.sendMessage("§cこのワールドではブロックの設置は禁止されています。");
        }
    }

    // 保護を適用すべきか判定するヘルパー
    private boolean isPvPWorldProtectionActive(Player player) {
        // ワールド名が一致し、かつクリエイティブモードでない場合
        return player.getWorld().getName().equals(PVP_WORLD_NAME)
                && player.getGameMode() != GameMode.CREATIVE;
    }
}