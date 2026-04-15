package com.lunar_prototype.deepwither.party;

import com.lunar_prototype.deepwither.Deepwither;
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
        infoMeta.displayName(Component.text("募集設定を完了してください", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.text("・最低1つのタグを選択", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("・最大人数を設定 (2〜6人)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.empty());
        infoLore.add(Component.text("これらが完了しないと公開できません。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
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

        // 最大人数設定ボタン
        inv.setItem(40, createMaxMembersItem(party.getMaxMembers()));

        // 決定ボタン
        inv.setItem(48, createCancelButton());
        inv.setItem(50, createConfirmButton());
        inv.setItem(52, createMenuButton());

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

    private ItemStack createMaxMembersItem(int current) {
        ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("[ 最大人数設定 ]", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("設定値: ", NamedTextColor.GRAY)
                .append(Component.text(current + " 人", NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("▶ 左クリックで +1", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("▶ 右クリックで -1", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("(制限: 2 〜 6人)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        
        meta.lore(lore);
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

    private ItemStack createMenuButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("メインメニューへ", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Main Menu に戻ります。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
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

        // 最大人数設定ボタン (slot 40)
        if (slot == 40) {
            int current = party.getMaxMembers();
            if (e.isLeftClick()) {
                if (current < 6) {
                    party.setMaxMembers(current + 1);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                }
            } else if (e.isRightClick()) {
                if (current > 2) {
                    party.setMaxMembers(current - 1);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                }
            }
            open(player);
            return;
        }

        // キャンセルボタン
        if (slot == 48) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            partyGUI.open(player);
            return;
        }

        if (slot == 52) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            player.closeInventory();
            Deepwither.getInstance().getMenuGUI().open(player);
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
