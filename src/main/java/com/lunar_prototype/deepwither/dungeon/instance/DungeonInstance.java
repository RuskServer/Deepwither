package com.lunar_prototype.deepwither.dungeon.instance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DungeonInstance {
    private final String instanceId;
    private final World world;
    private final Set<UUID> currentPlayers;
    private long lastEmptyTime;
    private final String type;
    private final String difficulty;
    private BossBar timerBar;
    private final int MAX_TIME_SECONDS = 15 * 60;
    private int remainingSeconds = MAX_TIME_SECONDS;
    private BukkitTask timerTask;

    public String getType() { return type; }
    public String getDifficulty() { return difficulty; }

    public DungeonInstance(String instanceId, World world, String type, String difficulty) {
        this.instanceId = instanceId;
        this.world = world;
        this.currentPlayers = new HashSet<>();
        this.lastEmptyTime = System.currentTimeMillis();
        this.type = type;
        this.difficulty = difficulty;

        this.timerBar = Bukkit.createBossBar(
                "",
                BarColor.GREEN,
                BarStyle.SOLID
        );
        this.timerBar.setTitle("ダンジョン残り時間"); // Placeholder, updated in lifecycle
    }

    public String getInstanceId() { return instanceId; }
    public World getWorld() { return world; }
    public Set<UUID> getPlayers() { return currentPlayers; }

    public void addPlayer(UUID uuid) {
        currentPlayers.add(uuid);
        timerBar.addPlayer(Bukkit.getPlayer(uuid));
        lastEmptyTime = -1;
    }

    public void removePlayer(UUID uuid) {
        currentPlayers.remove(uuid);
        timerBar.removePlayer(Bukkit.getPlayer(uuid));
        if (currentPlayers.isEmpty()) {
            lastEmptyTime = System.currentTimeMillis();
        }
    }

    public boolean isEmpty() { return currentPlayers.isEmpty(); }
    public long getLastEmptyTime() { return lastEmptyTime; }

    public void startLifeCycle() {
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                remainingSeconds--;
                if (remainingSeconds <= 0) {
                    handleTimeLimit();
                    this.cancel();
                    return;
                }

                double progress = (double) remainingSeconds / MAX_TIME_SECONDS;
                timerBar.setProgress(progress);

                if (progress < 0.2) {
                    timerBar.setColor(BarColor.RED);
                    timerBar.setTitle("§c§l警告: ダンジョン崩壊まで残り " + remainingSeconds + "秒");
                } else if (progress < 0.5) {
                    timerBar.setColor(BarColor.YELLOW);
                }

                int mins = remainingSeconds / 60;
                int secs = remainingSeconds % 60;
                timerBar.setTitle(String.format("§e§l制限時間: %02d:%02d", mins, secs));
            }
        }.runTaskTimer(com.lunar_prototype.deepwither.Deepwither.getInstance(), 0L, 20L);
    }

    private void handleTimeLimit() {
        if (currentPlayers.isEmpty()) return;
        for (UUID uuid : currentPlayers) {
            org.bukkit.entity.Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(Component.text("[Dungeon] ", NamedTextColor.RED, TextDecoration.BOLD)
                        .append(Component.text("制限時間が経過しました。ダンジョンは崩壊します...", NamedTextColor.DARK_RED)));
                p.setHealth(0);
            }
        }
        cleanup();
    }

    public void cleanup() {
        if (timerTask != null) timerTask.cancel();
        if (timerBar != null) timerBar.removeAll();
    }
}