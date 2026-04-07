package com.lunar_prototype.deepwither.profession;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.core.CacheManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@DependsOn({ProfessionDatabase.class, CacheManager.class})
public class ProfessionManager implements IManager {

    private final Deepwither plugin;
    private final ProfessionDatabase database;
    private final Map<UUID, BossBarData> bossBarMap = new ConcurrentHashMap<>();

    private static final int BASE_EXP = 50;

    public ProfessionManager(Deepwither plugin,ProfessionDatabase db) {
        this.plugin = plugin;
        this.database = db;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        bossBarMap.forEach((uuid, data) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && data.bossBar != null) {
                player.hideBossBar(data.bossBar);
            }
            if (data.hideTask != null) {
                data.hideTask.cancel();
            }
        });
        bossBarMap.clear();
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfessionData data = database.loadPlayer(uuid);
            DW.cache().getCache(uuid).set(PlayerProfessionData.class, data);
        });
    }

    public void saveAndUnloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerProfessionData data = DW.cache().getCache(uuid).get(PlayerProfessionData.class);
        if (data != null) {
            database.savePlayer(data);
            DW.cache().getCache(uuid).remove(PlayerProfessionData.class);
        }
    }

    private void savePlayerSync(UUID uuid) {
        PlayerProfessionData data = DW.cache().getCache(uuid).get(PlayerProfessionData.class);
        if (data != null) {
            database.savePlayer(data);
        }
    }

    public PlayerProfessionData getData(Player player) {
        PlayerProfessionData data = DW.cache().getCache(player.getUniqueId()).get(PlayerProfessionData.class);
        return data != null ? data : new PlayerProfessionData(player.getUniqueId());
    }

    public void addExp(Player player, ProfessionType type, int amount) {
        PlayerProfessionData data = getData(player);
        long totalExpBefore = data.getExp(type);
        int oldLevel = getLevel(totalExpBefore);

        data.addExp(type, amount);

        long totalExpAfter = data.getExp(type);
        int newLevel = getLevel(totalExpAfter);
        
        showExpBossBar(player, type, totalExpAfter, newLevel);

        if (newLevel > oldLevel) {
            sendLevelUpFeedback(player, type, oldLevel, newLevel);
        }
    }

    private void sendLevelUpFeedback(Player player, ProfessionType type, int oldLevel, int newLevel) {
        Title title = Title.title(
                Component.text("LEVEL UP!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(getDisplayName(type), NamedTextColor.YELLOW)
                        .append(Component.text(" Lv.", NamedTextColor.GRAY))
                        .append(Component.text(oldLevel, NamedTextColor.WHITE))
                        .append(Component.text(" → ", NamedTextColor.WHITE))
                        .append(Component.text(newLevel, NamedTextColor.GREEN, TextDecoration.BOLD))
        );
        player.showTitle(title);

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        Component separator = Component.text("--------------------------------------", NamedTextColor.AQUA)
                .decoration(TextDecoration.STRIKETHROUGH, true);
        player.sendMessage(separator);
        player.sendMessage(Component.text("    »» ", NamedTextColor.WHITE, TextDecoration.BOLD)
                .append(Component.text("レベルアップ！", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" (" + getDisplayName(type) + ")", NamedTextColor.YELLOW, TextDecoration.BOLD)));
        player.sendMessage(Component.text("   レベル: ", NamedTextColor.YELLOW)
                .append(Component.text(oldLevel, NamedTextColor.WHITE))
                .append(Component.text(" → ", NamedTextColor.WHITE))
                .append(Component.text(newLevel, NamedTextColor.GREEN, TextDecoration.BOLD)));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("- 職業ボーナス -", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  » ", NamedTextColor.AQUA)
                .append(Component.text("職業熟練度が上昇した。", NamedTextColor.WHITE)));
        player.sendMessage(separator);
    }

    private void showExpBossBar(Player player, ProfessionType type, long totalExp, int level) {
        UUID uuid = player.getUniqueId();
        BossBarData data = bossBarMap.computeIfAbsent(uuid, k -> new BossBarData());

        if (data.hideTask != null) {
            data.hideTask.cancel();
        }

        long expForCurrentLevel = totalExp - getTotalExpForLevel(level);
        long expRequiredForNext = getExpRequiredForNextLevel(level);
        float progress = (float) expForCurrentLevel / expRequiredForNext;
        progress = Math.min(1.0f, Math.max(0.0f, progress));

        Component title = Component.text(getDisplayName(type) + " ", NamedTextColor.WHITE)
                .append(Component.text("Lv." + level, NamedTextColor.YELLOW))
                .append(Component.text(" [", NamedTextColor.GRAY))
                .append(Component.text(expForCurrentLevel + "/" + expRequiredForNext, NamedTextColor.WHITE))
                .append(Component.text("]", NamedTextColor.GRAY));

        if (data.bossBar == null) {
            data.bossBar = BossBar.bossBar(title, progress, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
            player.showBossBar(data.bossBar);
        } else {
            data.bossBar.name(title);
            data.bossBar.progress(progress);
            player.showBossBar(data.bossBar); // Ensure it's shown if it was hidden
        }

        data.hideTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.hideBossBar(data.bossBar);
            data.hideTask = null;
        }, 100L); // 5 seconds
    }

    private String getDisplayName(ProfessionType type) {
        return switch (type) {
            case MINING -> "採掘";
            case FISHING -> "釣り";
        };
    }

    private long getTotalExpForLevel(int level) {
        long total = 0;
        for (int i = 1; i < level; i++) {
            total += getExpRequiredForNextLevel(i);
        }
        return total;
    }

    public int getLevel(long totalExp) {
        int level = 1;
        while (level < 100) {
            long req = getExpRequiredForNextLevel(level);
            if (totalExp < req) break;
            totalExp -= req;
            level++;
        }
        return level;
    }

    private long getExpRequiredForNextLevel(int currentLevel) {
        return (long) (BASE_EXP * Math.pow(currentLevel, 1.1));
    }

    public double getDoubleDropChance(Player player, ProfessionType type) {
        PlayerProfessionData data = getData(player);
        int level = getLevel(data.getExp(type));

        double maxChance = 0.5;
        return (level / 100.0) * maxChance;
    }

    private static class BossBarData {
        BossBar bossBar;
        BukkitTask hideTask;
    }
}
