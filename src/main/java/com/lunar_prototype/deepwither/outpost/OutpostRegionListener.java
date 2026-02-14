package com.lunar_prototype.deepwither.outpost;

import com.lunar_prototype.deepwither.outpost.OutpostEvent;
import com.lunar_prototype.deepwither.outpost.OutpostManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({OutpostManager.class})
public class OutpostRegionListener implements Listener, IManager {

    private OutpostManager manager;
    private final JavaPlugin plugin;

    public OutpostRegionListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.manager = OutpostManager.getInstance();
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        OutpostEvent activeEvent = manager.getActiveEvent();
        if (activeEvent == null) return;

        Player player = event.getPlayer();
        Location to = event.getTo();

        if (event.getFrom().getBlockX() == to.getBlockX() &&
                event.getFrom().getBlockY() == to.getBlockY() &&
                event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(to));

        String outpostRegionId = activeEvent.getOutpostRegionId();

        for (ProtectedRegion region : set) {
            if (region.getId().toLowerCase().contains(outpostRegionId)) {
                if (!activeEvent.getParticipants().contains(player.getUniqueId())) {
                    activeEvent.addParticipant(player.getUniqueId());
                    player.sendMessage(Component.text("[Outpost]", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .append(Component.text(" あなたは「", NamedTextColor.WHITE))
                            .append(Component.text(activeEvent.getOutpostRegionId(), NamedTextColor.YELLOW))
                            .append(Component.text("」の", NamedTextColor.WHITE))
                            .append(Component.text("参加者", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text("として記録されました！", NamedTextColor.WHITE)));
                }
            }
        }
    }
}
