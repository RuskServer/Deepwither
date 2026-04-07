package com.lunar_prototype.deepwither.listeners;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.booster.BoosterManager;
import com.lunar_prototype.deepwither.util.IManager;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Listener for NuVotifier vote events.
 * Grants a 2x experience booster for 1 hour to players who vote.
 */
public class VoteListener implements Listener, IManager {

    private final Deepwither plugin;

    public VoteListener(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws Exception {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onVote(VotifierEvent event) {
        Vote vote = event.getVote();
        String username = vote.getUsername();
        
        if (username == null || username.isEmpty()) {
            return;
        }

        Player player = Bukkit.getPlayerExact(username);
        if (player == null || !player.isOnline()) {
            // Player is offline. Depending on requirements, you might want to 
            // handle offline voting (e.g., save to DB and apply on next login).
            // For now, we only apply to online players.
            return;
        }

        BoosterManager boosterManager = DW.get(BoosterManager.class);
        if (boosterManager == null) {
            plugin.getLogger().warning("BoosterManager not found. Could not apply vote reward for " + username);
            return;
        }

        // Grant 2x booster for 60 minutes (1 hour)
        boosterManager.addBooster(player, 2.0, 60);

        player.sendMessage(Component.text("投票ありがとうございます！1時間限定の2倍経験値ブースターを付与しました！")
                .color(NamedTextColor.GREEN));
        
        plugin.getLogger().info("Applied 1-hour 2x booster to " + username + " for voting.");
    }
}
