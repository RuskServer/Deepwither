package com.lunar_prototype.deepwither.market;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
    private final Set<UUID> searchingPlayers = new HashSet<>();

    public MarketSearchHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.gui = Deepwither.getInstance().getMarketGui();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    public void startSearch(Player player) {
        searchingPlayers.add(player.getUniqueId());
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("[Market Search]", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(Component.text("検索したいアイテムの名前をチャットに入力してください。", NamedTextColor.WHITE));
        player.sendMessage(Component.text("(キャンセルする場合は 'cancel' と入力してください)", NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!searchingPlayers.contains(p.getUniqueId())) return;

        e.setCancelled(true);
        searchingPlayers.remove(p.getUniqueId());

        String message = e.getMessage();

        if (message.equalsIgnoreCase("cancel")) {
            p.sendMessage(Component.text("検索をキャンセルしました。", NamedTextColor.RED));
            Bukkit.getScheduler().runTask(plugin, () -> gui.openMainMenu(p));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            p.sendMessage(Component.text("キーワード「", NamedTextColor.GREEN)
                    .append(Component.text(message, NamedTextColor.WHITE))
                    .append(Component.text("」で検索中...", NamedTextColor.GREEN)));
            gui.openSearchResults(p, message);
        });
    }
}
