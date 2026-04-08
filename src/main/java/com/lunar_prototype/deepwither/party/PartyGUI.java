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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PartyGUI implements Listener {

    private final PartyManager partyManager;
    private final JavaPlugin plugin;
    private final PartyTagGUI partyTagGUI;
    private final Map<UUID, Integer> playerPage = new HashMap<>();

    private static final int PARTIES_PER_PAGE = 7;
    private static final Component GUI_TITLE = Component.text("Party - Recruits", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);

    public PartyGUI(PartyManager partyManager, JavaPlugin plugin) {
        this.partyManager = partyManager;
        this.plugin = plugin;
        this.partyTagGUI = new PartyTagGUI(partyManager, this, plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        openPage(player, 0);
    }

    private void openPage(Player player, int page) {
        playerPage.put(player.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);
        fillBackground(inv);

        // パーティーを管理するセクション
        inv.setItem(10, createMyPartyIcon(player));
        inv.setItem(11, createPublicToggleIcon(player));
        inv.setItem(12, createPartyInfoIcon(player));

        // 公開パーティー一覧
        List<Party> publicParties = partyManager.getPublicParties();
        int totalPages = (int) Math.ceil((double) publicParties.size() / PARTIES_PER_PAGE);

        if (!publicParties.isEmpty()) {
            int startIdx = page * PARTIES_PER_PAGE;
            int endIdx = Math.min(startIdx + PARTIES_PER_PAGE, publicParties.size());

            int slot = 19;
            for (int i = startIdx; i < endIdx; i++) {
                Party party = publicParties.get(i);
                inv.setItem(slot, createPartyItemStack(party, i));
                slot++;
            }

            // ページネーション
            if (page > 0) {
                inv.setItem(47, createPageButton(Material.ARROW, "◀ 前へ"));
            }
            if (page < totalPages - 1) {
                inv.setItem(51, createPageButton(Material.ARROW, "次へ ▶"));
            }
        } else {
            inv.setItem(29, createEmptyMessage());
        }

        inv.setItem(49, createCloseButton());

        player.openInventory(inv);
    }

    private ItemStack createMyPartyIcon(Player player) {
        Party party = partyManager.getParty(player);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text("[ 自分のパーティー ]", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (party != null) {
            lore.add(Component.text("ステータス: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            boolean isLeader = party.getLeaderId().equals(player.getUniqueId());
            lore.add(Component.text(isLeader ? "リーダー" : "メンバー", isLeader ? NamedTextColor.GREEN : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("メンバー数: ", NamedTextColor.GRAY)
                    .append(Component.text(party.getMemberIds().size(), NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("▶ クリックして詳細を表示", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("パーティーに参加していません。", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPublicToggleIcon(Player player) {
        Party party = partyManager.getParty(player);
        ItemStack item = new ItemStack(party != null && party.isPublic() ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("[ 公開設定 ]", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (party != null) {
            if (party.getLeaderId().equals(player.getUniqueId())) {
                boolean isPublic = party.isPublic();
                lore.add(Component.text("現在: ", NamedTextColor.GRAY)
                        .append(Component.text(isPublic ? "公開中" : "非公開", isPublic ? NamedTextColor.GREEN : NamedTextColor.RED))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("▶ クリックして切り替え", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("リーダーのみ設定可能です。", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("パーティーを作成してください。", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPartyInfoIcon(Player player) {
        Party party = partyManager.getParty(player);
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("[ パーティー情報 ]", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (party != null) {
            Player leader = Bukkit.getPlayer(party.getLeaderId());
            lore.add(Component.text("リーダー: ", NamedTextColor.GRAY)
                    .append(Component.text(leader != null ? leader.getName() : "Unknown", NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("メンバー:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

            for (UUID memberId : party.getMemberIds()) {
                Player p = Bukkit.getPlayer(memberId);
                Component memberName = p != null && p.isOnline()
                        ? Component.text(p.getName(), NamedTextColor.GREEN)
                        : Component.text("(Offline)", NamedTextColor.GRAY);
                lore.add(Component.text(" - ").append(memberName).decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("パーティーに参加していません。", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPartyItemStack(Party party, int index) {
        Player leader = Bukkit.getPlayer(party.getLeaderId());
        if (leader == null) return new ItemStack(Material.AIR);

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(leader);
        meta.displayName(Component.text((index + 1) + ". " + leader.getName() + "'s Party", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("リーダー: ", NamedTextColor.GRAY)
                .append(Component.text(leader.getName(), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("メンバー: ", NamedTextColor.GRAY)
                .append(Component.text(party.getMemberIds().size(), NamedTextColor.GREEN))
                .append(Component.text("名", NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false));
                
        if (!party.getTags().isEmpty()) {
            lore.add(Component.empty());
            Component tagsComp = Component.text("タグ: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
            for (PartyTag tag : party.getTags()) {
                tagsComp = tagsComp.append(tag.getComponent()).append(Component.space());
            }
            lore.add(tagsComp);
        }
        
        lore.add(Component.empty());
        lore.add(Component.text("▶ クリックして参加リクエスト", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageButton(Material mat, String text) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(text, NamedTextColor.WHITE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptyMessage() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("募集中のパーティーはありません", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("閉じる", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBackground(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, glass);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().title().equals(GUI_TITLE)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = e.getSlot();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        switch (slot) {
            case 10:
                // 自分のパーティー詳細（InfoコマンドでもOK）
                player.performCommand("party info");
                break;
            case 11:
                // 公開設定切り替え
                Party party = partyManager.getParty(player);
                if (party != null && party.getLeaderId().equals(player.getUniqueId())) {
                    if (party.isPublic()) {
                        partyManager.setPartyPublic(player, false);
                        openPage(player, playerPage.getOrDefault(player.getUniqueId(), 0));
                    } else {
                        player.closeInventory();
                        partyTagGUI.open(player);
                    }
                }
                break;
            case 12:
                // パーティー情報は表示されるだけなので再度開く
                openPage(player, playerPage.getOrDefault(player.getUniqueId(), 0));
                break;
            case 47:
                // 前ページ
                int prevPage = playerPage.getOrDefault(player.getUniqueId(), 0) - 1;
                if (prevPage >= 0) {
                    openPage(player, prevPage);
                }
                break;
            case 51:
                // 次ページ
                int nextPage = playerPage.getOrDefault(player.getUniqueId(), 0) + 1;
                openPage(player, nextPage);
                break;
            case 49:
                // 閉じる
                player.closeInventory();
                break;
            default:
                // パーティーへの参加リクエスト (19-25のスロット)
                if (slot >= 19 && slot <= 25) {
                    handlePartyJoin(player, slot - 19);
                }
                break;
        }
    }

    private void handlePartyJoin(Player player, int itemIndex) {
        List<Party> publicParties = partyManager.getPublicParties();
        int page = playerPage.getOrDefault(player.getUniqueId(), 0);
        int actualIndex = page * PARTIES_PER_PAGE + itemIndex;

        if (actualIndex >= 0 && actualIndex < publicParties.size()) {
            Party targetParty = publicParties.get(actualIndex);
            partyManager.joinPublicParty(player, targetParty.getLeaderId());
            openPage(player, page);
        }
    }
}

