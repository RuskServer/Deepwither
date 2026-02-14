package com.lunar_prototype.deepwither.profession;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@DependsOn({ProfessionDatabase.class})
public class ProfessionManager implements IManager {

    private final Deepwither plugin;
    private final ProfessionDatabase database;
    private final Map<UUID, PlayerProfessionData> cache = new ConcurrentHashMap<>();

    private static final int BASE_EXP = 50;

    public ProfessionManager(Deepwither plugin,ProfessionDatabase db) {
        this.plugin = plugin;
        this.database = db;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        for (UUID uuid : cache.keySet()) {
            savePlayerSync(uuid);
        }
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfessionData data = database.loadPlayer(uuid);
            cache.put(uuid, data);
        });
    }

    public void saveAndUnloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerProfessionData data = cache.remove(uuid);
        if (data != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                database.savePlayer(data);
            });
        }
    }

    private void savePlayerSync(UUID uuid) {
        PlayerProfessionData data = cache.get(uuid);
        if (data != null) {
            database.savePlayer(data);
        }
    }

    public PlayerProfessionData getData(Player player) {
        return cache.getOrDefault(player.getUniqueId(), new PlayerProfessionData(player.getUniqueId()));
    }

    public void addExp(Player player, ProfessionType type, int amount) {
        PlayerProfessionData data = getData(player);
        int oldLevel = getLevel(data.getExp(type));

        data.addExp(type, amount);

        int newLevel = getLevel(data.getExp(type));
        if (newLevel > oldLevel) {
            player.sendMessage(Component.text("==========================", NamedTextColor.GOLD));
            player.sendMessage(Component.text(" レベルアップ！ (" + type.name() + ")", NamedTextColor.YELLOW));
            player.sendMessage(Component.text(" Lv." + oldLevel + " -> Lv." + newLevel, NamedTextColor.AQUA));
            player.sendMessage(Component.text("==========================", NamedTextColor.GOLD));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
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
}
