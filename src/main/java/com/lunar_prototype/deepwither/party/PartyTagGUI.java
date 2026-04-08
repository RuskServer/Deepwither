package com.lunar_prototype.deepwither.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class PartyTagGUI implements Listener {
    private final PartyManager partyManager;
    private final PartyGUI partyGUI;
    private static final Component TITLE = Component.text("公開設定 - タグ選択", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);

    public PartyTagGUI(PartyManager partyManager, PartyGUI partyGUI, JavaPlugin plugin) {
        this.partyManager = partyManager;
        this.partyGUI = partyGUI;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Party party = partyManager.getParty(player);
        if (party == null || !party.getLeaderId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("リーダーのみパーティータグを設定できます。", NamedTextColor.RED));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // 背景
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }

        // ガイド用アイテム
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(Component.text("タグを選択してください (複数可)", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.text("最低でも1つのタグを選択しないと", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("公開（募集開始）できません。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // タグの配置
        int[] slots = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33}; // 余裕をもたせたスロット配置
        PartyTag[] tags = PartyTag.values();
        for (int i = 0; i < tags.length; i++) {
            if (i < slots.length) {
                inv.setItem(slots[i], createTagItem(tags[i], party.getTags().contains(tags[i])));
            }
        }

        // 決定ボタン
        inv.setItem(48, createCancelButton());
        inv.setItem(50, createConfirmButton());

        player.openInventory(inv);
    }

    private ItemStack createTagItem(PartyTag tag, boolean isSelected) {
        Material mat = tag.getIcon();
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        Component name = tag.getComponent().decoration(TextDecoration.ITALIC, false);
        meta.displayName(name);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (isSelected) {
            lore.add(Component.text("▶ 選択中", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true); // エンチャント光で強調
        } else {
            lore.add(Component.text("▷ 未選択", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("クリックで切り替え", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createConfirmButton() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("公開する (確定)", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("キャンセル / 戻る", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().title().equals(TITLE)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Party party = partyManager.getParty(player);
        if (party == null || !party.getLeaderId().equals(player.getUniqueId())) return;

        int slot = e.getSlot();

        // 決定ボタン
        if (slot == 50) {
            if (party.getTags().isEmpty()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                player.sendMessage(Component.text("エラー: 少なくとも1つのタグを選択してください！", NamedTextColor.RED));
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
                partyManager.setPartyPublic(player, true);
                player.closeInventory();
            }
            return;
        }

        // キャンセルボタン
        if (slot == 48) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            partyGUI.open(player);
            return;
        }

        // タグ切り替え
        PartyTag[] tags = PartyTag.values();
        int[] slots = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

        for (int i = 0; i < tags.length; i++) {
            if (i < slots.length && slot == slots[i]) {
                PartyTag tag = tags[i];
                if (party.getTags().contains(tag)) {
                    party.getTags().remove(tag);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
                } else {
                    party.getTags().add(tag);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                }
                // 再描画
                open(player);
                break;
            }
        }
    }
}
