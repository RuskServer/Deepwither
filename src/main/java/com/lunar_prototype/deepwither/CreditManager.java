package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({})
public class CreditManager implements IManager {

    private final JavaPlugin plugin;
    private File creditFile;
    private YamlConfiguration creditConfig;

    // [PlayerUUID] -> [TraderID] -> [CreditValue]
    private final Map<UUID, Map<String, Integer>> playerCredits = new HashMap<>();

    public CreditManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.creditFile = new File(plugin.getDataFolder(), "player_credits.yml");
        loadCredits();
    }

    @Override
    public void shutdown() {
        saveCredits();
    }

    private void loadCredits() {
        if (!creditFile.exists()) {
            plugin.saveResource("player_credits.yml", false);
        }
        creditConfig = YamlConfiguration.loadConfiguration(creditFile);

        for (String uuidStr : creditConfig.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(uuidStr);
                Map<String, Integer> traderMap = new HashMap<>();

                if (creditConfig.isConfigurationSection(uuidStr)) {
                    for (String traderId : creditConfig.getConfigurationSection(uuidStr).getKeys(false)) {
                        int credit = creditConfig.getInt(uuidStr + "." + traderId);
                        traderMap.put(traderId, credit);
                    }
                }
                playerCredits.put(playerUUID, traderMap);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なUUID: " + uuidStr);
            }
        }
    }

    public void saveCredits() {
        // メモリ上のデータをYAMLに書き戻す
        for (Map.Entry<UUID, Map<String, Integer>> entry : playerCredits.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<String, Integer> traderEntry : entry.getValue().entrySet()) {
                creditConfig.set(uuidStr + "." + traderEntry.getKey(), traderEntry.getValue());
            }
        }
        try {
            creditConfig.save(creditFile);
        } catch (IOException e) {
            plugin.getLogger().severe("信用度データの保存に失敗しました: " + e.getMessage());
        }
    }

    /**
     * プレイヤーの特定のトレーダーに対する信用度を取得する。
     * @param playerUUID プレイヤーのUUID
     * @param traderId トレーダーID
     * @return 信用度。存在しない場合は0。
     */
    public int getCredit(UUID playerUUID, String traderId) {
        return playerCredits.getOrDefault(playerUUID, new HashMap<>()).getOrDefault(traderId, 0);
    }

    /**
     * プレイヤーの信用度を増減させる。
     * @param playerUUID プレイヤーのUUID
     * @param traderId トレーダーID
     * @param amount 増減量 (負の値を渡すと減少)
     */
    public void addCredit(UUID playerUUID, String traderId, int amount) {
        playerCredits.computeIfAbsent(playerUUID, k -> new HashMap<>());
        Map<String, Integer> traderMap = playerCredits.get(playerUUID);

        int newCredit = Math.max(0, traderMap.getOrDefault(traderId, 0) + amount);
        traderMap.put(traderId, newCredit);

        // 変更をすぐに保存（頻繁に呼ばれる場合は非同期で遅延保存を検討）
        saveCredits();
    }
}