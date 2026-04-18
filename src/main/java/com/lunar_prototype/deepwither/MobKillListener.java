package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.booster.BoosterManager;
import com.lunar_prototype.deepwither.modules.economy.advancement.AdvancementManager;
import com.lunar_prototype.deepwither.modules.economy.advancement.PlayerAdvancementData;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMobManager;
import com.lunar_prototype.deepwither.modules.outpost.OutpostEvent;
import com.lunar_prototype.deepwither.modules.outpost.OutpostManager;
import com.lunar_prototype.deepwither.loot.RouteLootChestManager;
import com.lunar_prototype.deepwither.party.Party;
import com.lunar_prototype.deepwither.party.PartyManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

@DependsOn({LevelManager.class, OutpostManager.class, PartyManager.class, BoosterManager.class, CustomMobManager.class})
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
        this.levelManager = DW.get(LevelManager.class);
        this.outpostManager = DW.get(OutpostManager.class);
        this.partyManager = DW.get(PartyManager.class);
        this.boosterManager = DW.get(BoosterManager.class);
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

        RouteLootChestManager routeLootChestManager = DW.get(RouteLootChestManager.class);
        if (routeLootChestManager != null) {
            routeLootChestManager.recordMobKill(killer);
        }
        
        handleMobKillAdvancement(killer, mobType);

        OutpostEvent activeEvent = outpostManager.getActiveEvent();
        if (activeEvent != null) {
            String mobOutpostId = DW.get(MobSpawnManager.class).getMobOutpostId(e.getEntity());
            if (mobOutpostId != null && mobOutpostId.equals(activeEvent.getOutpostRegionId())) {
                activeEvent.mobDefeated(e.getEntity(), killer.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!(e.getEntity().getKiller() instanceof Player killer)) return;

        // CustomMobManager を取得
        CustomMobManager customMobManager = DW.get(CustomMobManager.class);
        if (customMobManager == null) return;

        String mobId = customMobManager.getCustomMobId(e.getEntity());
        if (mobId == null) return;

        // MythicMobDeathEvent で既に処理されている可能性があるため、
        // もし MythicMob なら二重付与を避ける（必要に応じて）
        // 現状の構成では CustomMob は MythicMob とは別系統なので、そのまま処理
        
        double baseExp = mobExpConfig.getDouble("mob-exp." + mobId, 0);
        if (baseExp > 0) {
            handleExpDistribution(killer, baseExp);
        }
        
        handleMobKillAdvancement(killer, mobId);
    }
    
    private void handleMobKillAdvancement(Player killer, String mobId) {
        com.lunar_prototype.deepwither.core.PlayerCache pc = DW.cache().getCache(killer.getUniqueId());
        if (pc == null) return;
        PlayerAdvancementData data = pc.getOrPut(PlayerAdvancementData.class, PlayerAdvancementData::new);
        int oldCount = data.getTotalMobKills();
        data.addMobKill(mobId);
        int newCount = data.getTotalMobKills();
        
        int[] milestones = {10, 50, 100, 500, 1000};
        AdvancementManager am = DW.get(AdvancementManager.class);
        if (am != null) {
            for (int m : milestones) {
                if (oldCount < m && newCount >= m) {
                    am.grantAdvancement(killer, "mob_kill_" + m);
                }
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
