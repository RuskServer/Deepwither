//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.lunar_prototype.deepwither.profiler;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.StatType;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.LevelManager;
import com.lunar_prototype.deepwither.companion.CompanionManager;
import com.lunar_prototype.deepwither.modules.mob.util.MobRegionService;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@DependsOn({CompanionManager.class, StatManager.class})
public class CombatAnalyzer implements Listener, IManager {
    private final Map<UUID, CombatProfile> activeProfiles = new HashMap();
    private CompanionManager companionManager;
    private final CombatLogger logger;
    private final Deepwither plugin;

    public CombatAnalyzer(Deepwither plugin) {
        this.plugin = plugin;
        this.logger = new CombatLogger(plugin);
    }

    @Override
    public void init() {
        this.companionManager = plugin.getCompanionManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onDeepwitherDamage(DeepwitherDamageEvent e) {
        LivingEntity victim = e.getVictim();
        LivingEntity attacker = e.getAttacker();

        if (victim instanceof Player player && attacker != null) {
            CombatProfile profile = this.activeProfiles.get(attacker.getUniqueId());
            if (profile != null) {
                profile.totalPlayerDamageTaken += e.getDamage();
                if (profile.playerEquipScore == 0) {
                    profile.playerEquipScore = this.calculateEquipScore(player);
                }
            }
        } else if (attacker instanceof Player player) {
            CombatProfile profile = this.activeProfiles.computeIfAbsent(victim.getUniqueId(), (k) -> {
                CombatProfile p = new CombatProfile(k, victim.getName(), victim.getMaxHealth());
                try {
                    LevelManager lm = DW.get(LevelManager.class);
                    if (lm != null && lm.get(player) != null) {
                        p.playerLevel = lm.get(player).getLevel();
                    }
                    MobRegionService rs = DW.get(MobRegionService.class);
                    if (rs != null) {
                        int tier = rs.getTierFromLocation(victim.getLocation());
                        p.regionLayer = "T" + tier;
                    }
                } catch (Exception ex) {}
                return p;
            });

            Input input = player.getCurrentInput();
            Vector inputVec = new Vector(input.isLeft() ? 1 : (input.isRight() ? -1 : 0), input.isJump() ? 1 : 0, input.isForward() ? 1 : (input.isBackward() ? -1 : 0));
            profile.lastAttackerUUID = player.getUniqueId();
            profile.damageHistory.add(new CombatProfile.DamageRecord(System.currentTimeMillis(), e.getDamage(), player.getInventory().getItemInMainHand().getType().toString(), victim.getLocation().distance(player.getLocation()), inputVec, e.getType()));
            
            if (e.getType() == DeepwitherDamageEvent.DamageType.MAGIC) {
                profile.totalMagicDamageDealt += e.getDamage();
            } else {
                profile.totalPhysicalDamageDealt += e.getDamage();
            }
        }
    }

    private int calculateEquipScore(Player player) {
        double defense = StatManager.getTotalStatsFromEquipment(player).getFinal(StatType.DEFENSE);
        return (int)defense;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        CombatProfile profile = (CombatProfile)this.activeProfiles.remove(e.getEntity().getUniqueId());
        if (profile != null) {
            Player killer = Bukkit.getPlayer(profile.lastAttackerUUID);
            if (killer != null) {
                profile.playerEquipScore = this.calculateEquipScore(killer);
                this.analyzeAndLog(profile, killer);
            }

        }
    }

    private void analyzeAndLog(CombatProfile profile, Player killer) {
        double hpLossPercent = profile.totalPlayerDamageTaken / Deepwither.getInstance().getStatManager().getActualMaxHealth(killer) * (double)100.0F;
        long duration = System.currentTimeMillis() - profile.startTime;
        double totalDmg = profile.damageHistory.stream().mapToDouble((d) -> d.damage()).sum();
        double avgDist = profile.damageHistory.stream().mapToDouble((d) -> d.distance()).average().orElse((double)0.0F);
        StringBuilder weaknessTags = new StringBuilder();
        System.out.println("---- Combat Analysis: " + profile.mobInternalName + " ----");
        System.out.println("Tier/Region: " + profile.regionLayer + " | PlayerLevel: " + profile.playerLevel);
        System.out.println("生存時間: " + (double)duration / (double)1000.0F + "秒");
        PrintStream var10000 = System.out;
        Object[] var10002 = new Object[]{avgDist};
        var10000.println("平均交戦距離: " + String.format("%.2f", var10002) + "m");
        if (avgDist > (double)10.0F && duration < 5000L) {
            System.out.println("[分析] 遠距離から即死しています。接近スキルかシールドが必要です。");
            weaknessTags.append("[KITED]");
        }

        long backwardHits = profile.damageHistory.stream().filter((d) -> d.playerInput().getZ() < (double)0.0F).count();
        if ((double)backwardHits > (double)profile.damageHistory.size() * 0.7) {
            System.out.println("[分析] プレイヤーが引き撃ち(Sキー)を多用しています。鈍化付与スキルの追加を推奨。");
            weaknessTags.append("[S_KEY_VULNERABLE]");
        }

        if (duration < 2000L && profile.initialHealth > (double)50.0F) {
            System.out.println("[分析] 短時間で高ダメージを受けています。ノックバック耐性(Attribute)の強化を推奨。");
        }

        if (profile.totalMagicDamageDealt > profile.totalPhysicalDamageDealt * 1.5 && avgDist < 4.0F) {
            System.out.println("[分析] 魔法職にもかかわらず敵に密着しすぎています。被弾リスク大。");
            weaknessTags.append("[BAD_POSITIONING]");
        }

        System.out.println("-------------------------------------------");
        if (weaknessTags.length() == 0) {
            weaknessTags.append("NONE");
        }

        this.logger.logProfile(profile, hpLossPercent, weaknessTags.toString());
        Logger var14 = this.plugin.getLogger();
        String var10001 = profile.mobInternalName;
        var14.info("Analysis saved for " + var10001 + ": " + String.valueOf(weaknessTags));
    }
}
