package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.booster.BoosterManager;
import com.lunar_prototype.deepwither.outpost.OutpostEvent;
import com.lunar_prototype.deepwither.outpost.OutpostManager;
import com.lunar_prototype.deepwither.party.Party;          // ★追加
import com.lunar_prototype.deepwither.party.PartyManager;   // ★追加
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;

import java.util.List;
import java.util.stream.Collectors;

public class MobKillListener implements Listener {
    private final LevelManager levelManager;
    private final FileConfiguration mobExpConfig;
    private final OutpostManager outpostManager;
    private final PartyManager partyManager; // ★追加
    private final BoosterManager boosterManager; // ★追加

    // ★ コンストラクタにBoosterManagerを追加
    public MobKillListener(LevelManager levelManager, FileConfiguration config, OutpostManager outpostManager, PartyManager partyManager, BoosterManager boosterManager) {
        this.levelManager = levelManager;
        this.mobExpConfig = config;
        this.outpostManager = outpostManager;
        this.partyManager = partyManager;
        this.boosterManager = boosterManager; // ★初期化
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent e) {
        if (!(e.getKiller() instanceof Player killer)) return;

        // 1. 経験値処理
        String mobType = e.getMobType().getInternalName();
        double baseExp = mobExpConfig.getDouble("mob-exp." + mobType, 0);

        if (baseExp > 0) {
            handleExpDistribution(killer, baseExp); // ★ 分配メソッド呼び出しに変更
        }
        // updatePlayerDisplayは分配メソッド内で行うか、ここでkillerだけ更新するかですが、
        // 分配対象全員を更新する必要があるためメソッド内で処理します。

        // 2. Outpost Mobの撃破カウント (既存ロジック・変更なし)
        OutpostEvent activeEvent = outpostManager.getActiveEvent();
        if (activeEvent != null) {
            String mobOutpostId = Deepwither.getInstance().getMobSpawnManager().getMobOutpostId(e.getEntity());
            if (mobOutpostId != null && mobOutpostId.equals(activeEvent.getOutpostRegionId())) {
                activeEvent.mobDefeated(e.getEntity(), killer.getUniqueId());
            }
        }
    }

    /**
     * ★ 経験値分配ロジック
     */
    private void handleExpDistribution(Player killer, double baseExp) {
        Party party = partyManager.getParty(killer);

        // --- 1. パーティー未所属の場合 ---
        if (party == null) {
            // ★ ブースト適用
            double multiplier = boosterManager.getMultiplier(killer);
            double finalExp = baseExp * multiplier;

            levelManager.addExp(killer, finalExp);
            levelManager.updatePlayerDisplay(killer);

            if (multiplier > 1.0) {
                killer.sendMessage("§6[Booster] §e+" + String.format("%.1f", finalExp) + " Exp (x" + multiplier + ")");
            }
            return;
        }

        // --- 2. パーティー分配処理 ---
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
            // ★ 各メンバーの個人ブーストを適用
            // これにより、ブーストを買った人だけが多く貰える仕組み、
            // もしくは全員ブーストなら全員凄まじく増える仕組みになります。
            double personalMultiplier = boosterManager.getMultiplier(member);
            double finalMemberExp = expPerMemberBase * personalMultiplier;

            levelManager.addExp(member, finalMemberExp);
            levelManager.updatePlayerDisplay(member);

            String msg = "§e[Party] +" + String.format("%.1f", finalMemberExp) + " Exp";
            if (personalMultiplier > 1.0) {
                msg += " §6(x" + personalMultiplier + " Booster)";
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