package com.lunar_prototype.deepwither;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class ArtifactManager {
    private final File dataFile;
    private Map<UUID, List<ItemStack>> playerArtifacts = new HashMap<>();

    public ArtifactManager(Deepwither plugin) {
        // プラグインのデータフォルダにファイルを指定
        this.dataFile = new File(plugin.getDataFolder(), "artifacts.dat"); // .datファイルを使用
        loadData();
    }

    public List<ItemStack> getPlayerArtifacts(Player player) {
        return playerArtifacts.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
    }

    public void savePlayerArtifacts(Player player, List<ItemStack> artifacts) {
        playerArtifacts.put(player.getUniqueId(), artifacts);
    }

    // --- 永続化のロジック ---

    // データをファイルに保存
    public void saveData() {
        try (BukkitObjectOutputStream oos = new BukkitObjectOutputStream(new FileOutputStream(dataFile))) {
            oos.writeObject(playerArtifacts);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ファイルからデータを読み込み
    public void loadData() {
        if (!dataFile.exists()) {
            return;
        }

        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(new FileInputStream(dataFile))) {
            Object obj = ois.readObject();
            if (obj instanceof Map) {
                // 型安全でないため警告が出ますが、この方法は一般的です
                this.playerArtifacts = (Map<UUID, List<ItemStack>>) obj;
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}