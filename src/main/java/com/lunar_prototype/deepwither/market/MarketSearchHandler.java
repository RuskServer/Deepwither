package com.lunar_prototype.deepwither.market;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@DependsOn({MarketGui.class})
public class MarketSearchHandler implements Listener, IManager {

    private final JavaPlugin plugin;
    private MarketGui gui;
    // 検索入力待ちのプレイヤーリスト
    private final Set<UUID> searchingPlayers = new HashSet<>();

    public MarketSearchHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.gui = (MarketGui) Deepwither.getInstance().getServiceManager().get(MarketGui.class);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    /**
     * プレイヤーを検索待機状態にする
     */
    public void startSearch(Player player) {
        searchingPlayers.add(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage("§b§l[Market Search]");
        player.sendMessage("§f検索したいアイテムの名前をチャットに入力してください。");
        player.sendMessage("§7(キャンセルする場合は 'cancel' と入力してください)");
        player.sendMessage("");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!searchingPlayers.contains(p.getUniqueId())) return;

        // チャットが全体に流れないようにキャンセル
        e.setCancelled(true);
        searchingPlayers.remove(p.getUniqueId());

        String message = e.getMessage();

        if (message.equalsIgnoreCase("cancel")) {
            p.sendMessage("§c検索をキャンセルしました。");
            // メインメニューに戻してあげる親切設計
            Bukkit.getScheduler().runTask(plugin, () -> gui.openMainMenu(p));
            return;
        }

        // GUI操作はメインスレッドで行う必要があるため、同期タスクに切り替える
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.sendMessage("§aキーワード「§f" + message + "§a」で検索中...");
            gui.openSearchResults(p, message);
        });
    }
}