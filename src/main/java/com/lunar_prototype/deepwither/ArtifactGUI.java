package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
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
    public static final int[] BORDER_SLOTS = {0, 1, 2, 6, 7, 8};
    public static final int BACKPACK_SLOT = 13;

    public ArtifactGUI() {}

    @Override
    public void init() {
        Component title = Component.text("[GUI] ", NamedTextColor.DARK_GRAY).append(Component.text("アーティファクト", NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false);
        artifactGUI = Bukkit.createInventory(null, 18, title);

        ItemStack artifactPlaceholder = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta artifactMeta = artifactPlaceholder.getItemMeta();
        artifactMeta.displayName(Component.text("【アーティファクトスロット】", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        artifactPlaceholder.setItemMeta(artifactMeta);

        ItemStack borderPlaceholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = borderPlaceholder.getItemMeta();
        borderMeta.displayName(Component.text("- - -", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        borderPlaceholder.setItemMeta(borderMeta);

        for (int i : BORDER_SLOTS) {
            artifactGUI.setItem(i, borderPlaceholder);
        }

        for (int i : ARTIFACT_SLOTS) {
            artifactGUI.setItem(i, artifactPlaceholder);
        }

        ItemStack bpPlaceholder = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta bpMeta = bpPlaceholder.getItemMeta();
        bpMeta.displayName(Component.text("【背中装備スロット】", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        bpPlaceholder.setItemMeta(bpMeta);
        artifactGUI.setItem(BACKPACK_SLOT, bpPlaceholder);

        Bukkit.getPluginManager().registerEvents(this, Deepwither.getInstance());
    }

    @Override
    public void shutdown() {}

    public void openArtifactGUI(Player player) {
        updateGUIFromPlayerData(player);
        player.openInventory(this.artifactGUI);
    }

    private void updateGUIFromPlayerData(Player player) {
        ItemStack artifactPlaceholder = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta artifactMeta = artifactPlaceholder.getItemMeta();
        artifactMeta.displayName(Component.text("【アーティファクトスロット】", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        artifactPlaceholder.setItemMeta(artifactMeta);

        for (int i : ARTIFACT_SLOTS) {
            artifactGUI.setItem(i, artifactPlaceholder);
        }

        List<ItemStack> savedArtifacts = Deepwither.getInstance().getArtifactManager().getPlayerArtifacts(player);
        for (int i = 0; i < savedArtifacts.size() && i < ARTIFACT_SLOTS.length; i++) {
            artifactGUI.setItem(ARTIFACT_SLOTS[i], savedArtifacts.get(i));
        }

        ItemStack savedBackpack = Deepwither.getInstance().getArtifactManager().getPlayerBackpack(player);
        if (savedBackpack != null) {
            artifactGUI.setItem(BACKPACK_SLOT, savedBackpack);
        }
    }

    public Inventory getInventory() {
        return artifactGUI;
    }
}