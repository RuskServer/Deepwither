package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.booster.BoosterManager;
import com.lunar_prototype.deepwither.outpost.OutpostEvent;
import com.lunar_prototype.deepwither.outpost.OutpostManager;
import com.lunar_prototype.deepwither.party.Party;
import com.lunar_prototype.deepwither.party.PartyManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

@DependsOn({LevelManager.class, OutpostManager.class, PartyManager.class, BoosterManager.class})
public class MobKillListener implements Listener, IManager {
    private LevelManager levelManager;
    private final FileConfiguration mobExpConfig;
    private OutpostManager outpostManager;
    private PartyManager partyManager;
    private BoosterManager boosterManager;
    private final JavaPlugin plugin;

    public MobKillListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mobExpConfig = plugin.getConfig();
    }

    @Override
    public void init() {
        this.levelManager = Deepwither.getInstance().getLevelManager();
        this.outpostManager = OutpostManager.getInstance();
        this.partyManager = Deepwither.getInstance().getPartyManager();
        this.boosterManager = Deepwither.getInstance().getBoosterManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent e) {
        if (!(e.getKiller() instanceof Player killer)) return;

        String mobType = e.getMobType().getInternalName();
        double baseExp = mobExpConfig.getDouble("mob-exp." + mobType, 0);

        if (baseExp > 0) {
            handleExpDistribution(killer, baseExp);
        }

        OutpostEvent activeEvent = outpostManager.getActiveEvent();
        if (activeEvent != null) {
            String mobOutpostId = Deepwither.getInstance().getMobSpawnManager().getMobOutpostId(e.getEntity());
            if (mobOutpostId != null && mobOutpostId.equals(activeEvent.getOutpostRegionId())) {
                activeEvent.mobDefeated(e.getEntity(), killer.getUniqueId());
            }
        }
    }

    private void handleExpDistribution(Player killer, double baseExp) {
        Party party = partyManager.getParty(killer);

        if (party == null) {
            double multiplier = boosterManager.getMultiplier(killer);
            double finalExp = baseExp * multiplier;

            levelManager.addExp(killer, finalExp);
            levelManager.updatePlayerDisplay(killer);

            if (multiplier > 1.0) {
                killer.sendMessage(Component.text("[Booster] ", NamedTextColor.GOLD)
                        .append(Component.text("+" + String.format("%.1f", finalExp) + " Exp (x" + multiplier + ")", NamedTextColor.YELLOW)));
            }
            return;
        }

        double shareRadius = 30.0;
        List<Player> nearbyMembers = party.getOnlineMembers().stream()
                .filter(p -> p.getWorld().equals(killer.getWorld()))
                .filter(p -> p.getLocation().distanceSquared(killer.getLocation()) <= shareRadius * shareRadius)
                .collect(Collectors.toList());

        if (nearbyMembers.isEmpty()) {
            double multiplier = boosterManager.getMultiplier(killer);
            levelManager.addExp(killer, baseExp * multiplier);
            levelManager.updatePlayerDisplay(killer);
            return;
        }

        double partyBonusMultiplier = 1.0 + ((nearbyMembers.size() - 1) * 0.1);
        double totalExpWithBonus = baseExp * partyBonusMultiplier;
        double expPerMemberBase = totalExpWithBonus / nearbyMembers.size();

        for (Player member : nearbyMembers) {
            double personalMultiplier = boosterManager.getMultiplier(member);
            double finalMemberExp = expPerMemberBase * personalMultiplier;

            levelManager.addExp(member, finalMemberExp);
            levelManager.updatePlayerDisplay(member);

            Component msg = Component.text("[Party] ", NamedTextColor.YELLOW)
                    .append(Component.text("+" + String.format("%.1f", finalMemberExp) + " Exp", NamedTextColor.YELLOW));
            if (personalMultiplier > 1.0) {
                msg = msg.append(Component.text(" (x" + personalMultiplier + " Booster)", NamedTextColor.GOLD));
            }
            member.sendMessage(msg);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        event.setAmount(0);
        levelManager.updatePlayerDisplay(player);
    }
}
