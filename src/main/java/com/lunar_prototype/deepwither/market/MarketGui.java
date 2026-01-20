package com.lunar_prototype.deepwither.market;

import com.lunar_prototype.deepwither.Deepwither;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MarketGui implements Listener {

    private final GlobalMarketManager manager;
    private static final String TITLE_MAIN = "Global Market: Sellers";
    private static final String TITLE_SHOP = "Shop: ";
    private static final String TITLE_SEARCH = "Market Search Results";
    private final NamespacedKey LISTING_ID_KEY = new NamespacedKey(Deepwither.getInstance(), "listing_id");

    public MarketGui(GlobalMarketManager manager) {
        this.manager = manager;
    }

    // 1. メインメニュー: アクティブな出品者一覧
    public void openMainMenu(Player player) {
        List<OfflinePlayer> sellers = manager.getActiveSellers();
        int size = Math.min(54, ((sellers.size() / 9) + 1) * 9);
        Inventory inv = Bukkit.createInventory(null, size, Component.text(TITLE_MAIN));

        for (OfflinePlayer seller : sellers) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(seller);
            meta.displayName(Component.text("§e" + seller.getName() + "のショップ"));
            meta.lore(List.of(Component.text("§7クリックして商品を見る")));
            head.setItemMeta(meta);
            inv.addItem(head);
        }

        // 検索ボタンなどを配置
        ItemStack searchBtn = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchBtn.getItemMeta();
        searchMeta.displayName(Component.text("§b[アイテム検索]"));
        searchBtn.setItemMeta(searchMeta);
        inv.setItem(size - 1, searchBtn);

        player.openInventory(inv);
    }

    // 2. プレイヤーごとのショップ画面
    public void openPlayerShop(Player viewer, OfflinePlayer seller) {
        List<MarketListing> listings = manager.getListingsByPlayer(seller.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE_SHOP + seller.getName()));

        for (MarketListing listing : listings) {
            ItemStack displayItem = listing.getItem().clone();
            ItemMeta meta = displayItem.getItemMeta();
            List<Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();
            meta.getPersistentDataContainer().set(LISTING_ID_KEY, PersistentDataType.STRING, listing.getId().toString());

            lore.add(Component.text("§8----------------"));
            lore.add(Component.text("§e価格: " + listing.getPrice() + " G"));
            lore.add(Component.text("§aクリックで購入"));

            meta.lore(lore);
            displayItem.setItemMeta(meta);

            // NBT等でListingIDを埋め込むか、スロット位置とリストを対応させる必要がある
            // ここでは簡易的にアイテムを配置
            inv.addItem(displayItem);
        }

        viewer.openInventory(inv);
    }

    // 3. 検索結果画面
    public void openSearchResults(Player player, String query) {
        List<MarketListing> results = manager.search(query);
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE_SEARCH));

        for (MarketListing listing : results) {
            ItemStack displayItem = listing.getItem().clone();
            ItemMeta meta = displayItem.getItemMeta();
            List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();

            meta.getPersistentDataContainer().set(LISTING_ID_KEY, PersistentDataType.STRING, listing.getId().toString());

            lore.add(Component.text("§8----------------"));
            lore.add(Component.text("§7出品者: " + Bukkit.getOfflinePlayer(listing.getSellerId()).getName()));
            lore.add(Component.text("§e価格: " + listing.getPrice() + " G"));

            meta.lore(lore);
            displayItem.setItemMeta(meta);
            inv.addItem(displayItem);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // 1. タイトルチェック
        String title = e.getView().getTitle();
        boolean isMain = title.equals(TITLE_MAIN);
        boolean isShop = title.startsWith("Shop:");
        boolean isSearch = title.equals(TITLE_SEARCH);

        if (!isMain && !isShop && !isSearch) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        ItemStack current = e.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        // 2. メインメニュー（出品者一覧）の処理
        if (isMain) {
            if (current.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) current.getItemMeta();
                if (meta.getOwningPlayer() != null) {
                    openPlayerShop(p, meta.getOwningPlayer());
                }
            } else if (current.getType() == Material.COMPASS) {
                p.closeInventory();
                p.sendMessage("§b[Market] 検索したいキーワードをチャットに入力してください。");
                Deepwither.getInstance().getMarketSearchHandler().startSearch(p);
            }
        }

        // 3. ショップまたは検索結果画面での購入処理
        else {
            ItemMeta meta = current.getItemMeta();
            if (meta == null) return;

            // PDCからListing IDを取得
            String idStr = meta.getPersistentDataContainer().get(LISTING_ID_KEY, PersistentDataType.STRING);
            if (idStr == null) return;

            UUID listingId = UUID.fromString(idStr);

            // 全出品リストから対象のListingを検索
            Optional<MarketListing> listingOpt = manager.getAllListings().stream()
                    .filter(l -> l.getId().equals(listingId))
                    .findFirst();

            if (listingOpt.isEmpty()) {
                p.sendMessage("§c[Market] このアイテムは既に売り切れているか、取り下げられています。");
                p.closeInventory();
                return;
            }

            MarketListing listing = listingOpt.get();

            // 自分の出品は買えないようにする（任意）
            if (listing.getSellerId().equals(p.getUniqueId())) {
                p.sendMessage("§c[Market] 自分の出品を購入することはできません。");
                return;
            }

            // 購入実行 (Vault連携済みのbuyItemメソッドを呼び出し)
            if (manager.buyItem(p, listing)) {
                // 購入成功：画面を更新するか閉じる
                p.closeInventory();
                // 再度開いて最新の状態にする場合は openPlayerShop を呼ぶ
            }
        }
    }
}