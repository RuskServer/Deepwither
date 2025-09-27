package com.lunar_prototype.deepwither;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ArtifactGUI implements Listener {

    private Inventory artifactGUI;
    public static final int[] ARTIFACT_SLOTS = {3, 4, 5};

    public ArtifactGUI() {
        // GUIの作成 (9スロット)
        artifactGUI = Bukkit.createInventory(null, 9, "アーティファクト");

        // アーティファクトスロットのプレースホルダーを配置
        ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        meta.setDisplayName("§rアーティファクトスロット");
        placeholder.setItemMeta(meta);

        for (int i : ARTIFACT_SLOTS) {
            artifactGUI.setItem(i, placeholder);
        }
    }

    public void openArtifactGUI(Player player) {
        // GUIを開く前に、保存されたデータを読み込んでGUIを更新
        updateGUIFromPlayerData(player);
        player.openInventory(this.artifactGUI);
    }

    private void updateGUIFromPlayerData(Player player) {
        // まず、GUIを初期状態に戻す
        clearGUI();

        // 保存されたアーティファクトのリストを取得
        List<ItemStack> savedArtifacts = Deepwither.getInstance().getArtifactManager().getPlayerArtifacts(player);

        // 取得したアイテムをGUIのスロットに配置
        for (int i = 0; i < savedArtifacts.size(); i++) {
            if (i < ARTIFACT_SLOTS.length) {
                artifactGUI.setItem(ARTIFACT_SLOTS[i], savedArtifacts.get(i));
            }
        }
    }

    private void clearGUI() {
        // アーティファクトスロットをクリアし、プレースホルダーを再配置
        ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        meta.setDisplayName("§rアーティファクトスロット");
        placeholder.setItemMeta(meta);

        for (int i : ARTIFACT_SLOTS) {
            artifactGUI.setItem(i, placeholder);
        }
    }

    public Inventory getInventory() {
        return artifactGUI;
    }
}