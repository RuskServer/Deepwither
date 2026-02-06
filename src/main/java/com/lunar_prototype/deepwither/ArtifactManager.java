package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

@DependsOn({})
public class ArtifactManager implements IManager {
    private File dataFile;
    private Map<UUID, List<ItemStack>> playerArtifacts = new HashMap<>();
    // ★ 背中装備保存用のマップを追加
    private Map<UUID, ItemStack> playerBackpacks = new HashMap<>();
    private final JavaPlugin plugin;

    public ArtifactManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.dataFile = new File(plugin.getDataFolder(), "artifacts.dat");
        loadData();
    }

    @Override
    public void shutdown() {
        saveData();
    }

    public List<ItemStack> getPlayerArtifacts(Player player) {
        return playerArtifacts.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
    }

    // ★ 背中装備を取得するメソッド
    public ItemStack getPlayerBackpack(Player player) {
        return playerBackpacks.get(player.getUniqueId());
    }

    public void savePlayerArtifacts(Player player, List<ItemStack> artifacts, ItemStack backpack) {
        playerArtifacts.put(player.getUniqueId(), artifacts);
        // ★ 背中装備も同時にキャッシュに保存
        if (backpack != null) {
            playerBackpacks.put(player.getUniqueId(), backpack);
        } else {
            playerBackpacks.remove(player.getUniqueId());
        }
    }

    // --- 永続化のロジック (互換性維持) ---

    public void saveData() {
        try (BukkitObjectOutputStream oos = new BukkitObjectOutputStream(new FileOutputStream(dataFile))) {
            // アーティファクトとバックパックを一つのMapにまとめて保存
            Map<String, Object> allData = new HashMap<>();
            allData.put("artifacts", playerArtifacts);
            allData.put("backpacks", playerBackpacks);
            oos.writeObject(allData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadData() {
        if (!dataFile.exists()) return;

        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(new FileInputStream(dataFile))) {
            Object obj = ois.readObject();

            if (obj instanceof Map) {
                Map<?, ?> rawMap = (Map<?, ?>) obj;

                // 新しい形式 (Mapの中に "artifacts" キーがある場合)
                if (rawMap.containsKey("artifacts")) {
                    this.playerArtifacts = (Map<UUID, List<ItemStack>>) rawMap.get("artifacts");
                    this.playerBackpacks = (Map<UUID, ItemStack>) rawMap.get("backpacks");
                }
                // 旧形式 (Map自体が playerArtifacts だった場合)
                else {
                    this.playerArtifacts = (Map<UUID, List<ItemStack>>) rawMap;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}