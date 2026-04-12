package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@DependsOn({})
public class ArtifactGUI implements IManager {

    public static final int[] ARTIFACT_SLOTS = {3, 4, 5};
    public static final int[] BORDER_SLOTS = {0, 1, 2, 6, 7, 8};
    public static final int BACKPACK_SLOT = 13;

    private static final Component TITLE = Component.text("Artifacts & Equipment", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);

    public ArtifactGUI() {}

    @Override
    public void init() {
        // 共有インベントリは持たない設計のため、ここでは何もしない
    }

    @Override
    public void shutdown() {}

    /**
     * プレイヤー専用の新しいインベントリを生成して開く。
     * 毎回新しいインスタンスを生成することで複数人同時利用でのデータ混入を防ぐ。
     */
    public void openArtifactGUI(Player player) {
        Inventory inv = buildInventory(player);
        player.openInventory(inv);
    }

    private Inventory buildInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, 18, TITLE);

        // ボーダースロット
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text("- - -", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        border.setItemMeta(borderMeta);
        for (int i : BORDER_SLOTS) {
            inv.setItem(i, border);
        }

        // アーティファクトスロットのプレースホルダー
        ItemStack artifactPlaceholder = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta artifactMeta = artifactPlaceholder.getItemMeta();
        artifactMeta.displayName(Component.text("【アーティファクトスロット】", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        artifactPlaceholder.setItemMeta(artifactMeta);
        for (int i : ARTIFACT_SLOTS) {
            inv.setItem(i, artifactPlaceholder);
        }

        // 背中装備スロットのプレースホルダー
        ItemStack bpPlaceholder = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta bpMeta = bpPlaceholder.getItemMeta();
        bpMeta.displayName(Component.text("【背中装備スロット】", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        bpPlaceholder.setItemMeta(bpMeta);
        inv.setItem(BACKPACK_SLOT, bpPlaceholder);

        // 保存済みアーティファクトを読み込んで配置
        List<ItemStack> savedArtifacts = Deepwither.getInstance().getArtifactManager().getPlayerArtifacts(player);
        for (int i = 0; i < savedArtifacts.size() && i < ARTIFACT_SLOTS.length; i++) {
            inv.setItem(ARTIFACT_SLOTS[i], savedArtifacts.get(i));
        }

        // 保存済み背中装備を配置
        ItemStack savedBackpack = Deepwither.getInstance().getArtifactManager().getPlayerBackpack(player);
        if (savedBackpack != null) {
            inv.setItem(BACKPACK_SLOT, savedBackpack);
        }

        return inv;
    }

    /**
     * @deprecated 共有インベントリは廃止。openArtifactGUI(player)を使用してください。
     */
    @Deprecated
    public Inventory getInventory() {
        return null;
    }
}
