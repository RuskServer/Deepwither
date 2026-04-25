//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.lunar_prototype.deepwither.profiler;

import com.lunar_prototype.deepwither.Deepwither;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.bukkit.Bukkit;

public class CombatLogger {
    private final Deepwither plugin;
    private final File logFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public CombatLogger(Deepwither plugin) {
        this.plugin = plugin;
        File folder = new File(plugin.getDataFolder(), "analysis");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        this.logFile = new File(folder, "combat_log.csv");
        if (!this.logFile.exists()) {
            this.writeHeader();
        }

    }

    private void writeHeader() {
        try (PrintWriter out = new PrintWriter(new FileWriter(this.logFile, true))) {
            out.println("Timestamp,PlayerLevel,Tier,CombatClass,MobName,Duration,AvgDist,TotalDmgDealt,TotalDmgTaken,BackRate,JumpRate,HPLoss%,EquipScore,WeaknessTag");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void logProfile(CombatProfile profile, double hpLossPercent, String weaknessTag) {
        long durationMs = System.currentTimeMillis() - profile.startTime;
        double totalDmgDealt = profile.totalPhysicalDamageDealt + profile.totalMagicDamageDealt;
        double avgDist = profile.damageHistory.stream().mapToDouble((d) -> d.distance()).average().orElse((double)0.0F);
        long totalHits = (long)profile.damageHistory.size();
        double backwardRate = totalHits == 0L ? (double)0.0F : (double)profile.damageHistory.stream().filter((d) -> d.playerInput().getZ() < (double)0.0F).count() / (double)totalHits;
        double jumpRate = totalHits == 0L ? (double)0.0F : (double)profile.damageHistory.stream().filter((d) -> d.playerInput().getY() > (double)0.0F).count() / (double)totalHits;
        
        String combatClass = "HYBRID";
        if (profile.totalMagicDamageDealt > profile.totalPhysicalDamageDealt * 1.5) {
            combatClass = "MAGIC";
        } else if (profile.totalPhysicalDamageDealt > profile.totalMagicDamageDealt * 1.5) {
            combatClass = "PHYSICAL";
        }

        final String finalCombatClass = combatClass;

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try (PrintWriter out = new PrintWriter(new FileWriter(this.logFile, true))) {
                out.printf("%s,%d,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.1f%%,%d,%s%n", 
                    this.dateFormat.format(new Date()), 
                    profile.playerLevel,
                    profile.regionLayer,
                    finalCombatClass,
                    profile.mobInternalName, 
                    (double)durationMs / 1000.0F, 
                    avgDist, 
                    totalDmgDealt, 
                    profile.totalPlayerDamageTaken,
                    backwardRate, 
                    jumpRate, 
                    hpLossPercent, 
                    profile.playerEquipScore, 
                    weaknessTag);
            } catch (IOException e) {
                e.printStackTrace();
            }

        });
    }
}
