package com.lunar_prototype.deepwither.profession;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProfessionManager {

    private final Deepwither plugin;
    private final ProfessionDatabase database; // DBクラス
    private final Map<UUID, PlayerProfessionData> cache = new ConcurrentHashMap<>();

    // レベル計算定数
    private static final int BASE_EXP = 100;

    public ProfessionManager(Deepwither plugin,ProfessionDatabase db) {
        this.plugin = plugin;
        // データベース初期化
        this.database = db;
    }

    // --- サーバー停止時の処理 ---
    public void shutdown() {
        // 全員のデータを保存
        for (UUID uuid : cache.keySet()) {
            savePlayerSync(uuid); // 同期保存
        }
    }

    // --- データ管理 ---

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // 非同期でDBからロード
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfessionData data = database.loadPlayer(uuid);
            cache.put(uuid, data);
        });
    }

    public void saveAndUnloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerProfessionData data = cache.remove(uuid);
        if (data != null) {
            // 非同期でDBへ保存
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                database.savePlayer(data);
            });
        }
    }

    // サーバーシャットダウン時などの緊急保存用（同期）
    private void savePlayerSync(UUID uuid) {
        PlayerProfessionData data = cache.get(uuid);
        if (data != null) {
            database.savePlayer(data);
        }
    }

    public PlayerProfessionData getData(Player player) {
        return cache.getOrDefault(player.getUniqueId(), new PlayerProfessionData(player.getUniqueId()));
    }

    // --- ロジック (変更なし) ---

    public void addExp(Player player, ProfessionType type, int amount) {
        PlayerProfessionData data = getData(player);
        int oldLevel = getLevel(data.getExp(type));

        data.addExp(type, amount);

        int newLevel = getLevel(data.getExp(type));
        if (newLevel > oldLevel) {
            player.sendMessage(ChatColor.GOLD + "==========================");
            player.sendMessage(ChatColor.YELLOW + " レベルアップ！ (" + type.name() + ")");
            player.sendMessage(ChatColor.AQUA + " Lv." + oldLevel + " -> Lv." + newLevel);
            player.sendMessage(ChatColor.GOLD + "==========================");
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
        return (long) (BASE_EXP * Math.pow(currentLevel, 1.2));
    }

    public double getDoubleDropChance(Player player, ProfessionType type) {
        PlayerProfessionData data = getData(player);
        int level = getLevel(data.getExp(type));

        double maxChance = 0.5;
        return (level / 100.0) * maxChance;
    }
}