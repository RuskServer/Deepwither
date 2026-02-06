package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@DependsOn({})
public class ArtifactGUI implements Listener, IManager {

    private Inventory artifactGUI;
    public static final int[] ARTIFACT_SLOTS = {3, 4, 5};
    // ★ 装飾スロットを定義
    public static final int[] BORDER_SLOTS = {0, 1, 2, 6, 7, 8};

    public static final int BACKPACK_SLOT = 13;

    public ArtifactGUI() {}

    @Override
    public void init() {
        // GUIの作成 (9スロット)
        artifactGUI = Bukkit.createInventory(null, 18, "§8[GUI] §6アーティファクト"); // タイトルを見やすく修正

        // 1. アーティファクトスロットのプレースホルダーを準備
        ItemStack artifactPlaceholder = new ItemStack(Material.CYAN_STAINED_GLASS_PANE); // 色を変えて視認性向上
        ItemMeta artifactMeta = artifactPlaceholder.getItemMeta();
        artifactMeta.setDisplayName(ChatColor.AQUA + "【アーティファクトスロット】");
        artifactPlaceholder.setItemMeta(artifactMeta);

        // 2. 装飾用のプレースホルダーを準備
        ItemStack borderPlaceholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = borderPlaceholder.getItemMeta();
        borderMeta.setDisplayName("§7- - -"); // 名前を短く
        borderPlaceholder.setItemMeta(borderMeta);

        // 3. ★ 装飾スロットを埋める
        for (int i : BORDER_SLOTS) {
            artifactGUI.setItem(i, borderPlaceholder);
        }

        // 4. アーティファクトスロットのプレースホルダーを配置
        for (int i : ARTIFACT_SLOTS) {
            artifactGUI.setItem(i, artifactPlaceholder);
        }

        ItemStack bpPlaceholder = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta bpMeta = bpPlaceholder.getItemMeta();
        bpMeta.setDisplayName("§d【背中装備スロット】");
        bpPlaceholder.setItemMeta(bpMeta);
        artifactGUI.setItem(BACKPACK_SLOT, bpPlaceholder);

        // リスナー登録
        Bukkit.getPluginManager().registerEvents(this, Deepwither.getInstance());
    }

    @Override
    public void shutdown() {}

    public void openArtifactGUI(Player player) {
        // GUIを開く前に、保存されたデータを読み込んでGUIを更新
        updateGUIFromPlayerData(player);
        player.openInventory(this.artifactGUI);
    }

    private void updateGUIFromPlayerData(Player player) {
        // まず、GUIを初期状態に戻す (clearGUIを修正し、ボーダーはそのまま残すようにする)
        // clearGUI(); // clearGUIを廃止し、クリアと初期配置を統合

        // 1. アーティファクトスロットのプレースホルダーを再配置
        ItemStack artifactPlaceholder = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta artifactMeta = artifactPlaceholder.getItemMeta();
        artifactMeta.setDisplayName(ChatColor.AQUA + "【アーティファクトスロット】");
        artifactPlaceholder.setItemMeta(artifactMeta);

        // スロットをプレースホルダーでリセット
        for (int i : ARTIFACT_SLOTS) {
            artifactGUI.setItem(i, artifactPlaceholder);
        }

        // 2. 保存されたアーティファクトの配置
        List<ItemStack> savedArtifacts = Deepwither.getInstance().getArtifactManager().getPlayerArtifacts(player);
        for (int i = 0; i < savedArtifacts.size() && i < ARTIFACT_SLOTS.length; i++) {
            artifactGUI.setItem(ARTIFACT_SLOTS[i], savedArtifacts.get(i));
        }

        // 3. 保存された背中装備の配置
        ItemStack savedBackpack = Deepwither.getInstance().getArtifactManager().getPlayerBackpack(player);
        if (savedBackpack != null) {
            artifactGUI.setItem(BACKPACK_SLOT, savedBackpack);
        }
    }

    public Inventory getInventory() {
        return artifactGUI;
    }
}
