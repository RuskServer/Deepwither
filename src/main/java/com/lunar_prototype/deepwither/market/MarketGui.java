package com.lunar_prototype.deepwither.market;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@DependsOn({GlobalMarketManager.class})
public class MarketGui implements Listener, IManager {

    private GlobalMarketManager manager;
    private static final String TITLE_MAIN = "Global Market: Sellers";
    private static final String TITLE_SHOP_PREFIX = "Shop: ";
    private static final String TITLE_SEARCH = "Market Search Results";
    private final NamespacedKey LISTING_ID_KEY = new NamespacedKey(Deepwither.getInstance(), "listing_id");
    private final JavaPlugin plugin;

    public MarketGui(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.manager = Deepwither.getInstance().getGlobalMarketManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    public void openMainMenu(Player player) {
        List<OfflinePlayer> sellers = manager.getActiveSellers();
        int size = Math.min(54, ((sellers.size() / 9) + 1) * 9);
        Inventory inv = Bukkit.createInventory(null, size, Component.text(TITLE_MAIN, NamedTextColor.DARK_AQUA));

        for (OfflinePlayer seller : sellers) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(seller);
            meta.displayName(Component.text(seller.getName() + "のショップ", NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text("クリックして商品を見る", NamedTextColor.GRAY)));
            head.setItemMeta(meta);
            inv.addItem(head);
        }

        ItemStack searchBtn = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchBtn.getItemMeta();
        searchMeta.displayName(Component.text("[アイテム検索]", NamedTextColor.AQUA));
        searchBtn.setItemMeta(searchMeta);
        inv.setItem(size - 1, searchBtn);

        player.openInventory(inv);
    }

    public void openPlayerShop(Player viewer, OfflinePlayer seller) {
        List<MarketListing> listings = manager.getListingsByPlayer(seller.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE_SHOP_PREFIX + seller.getName(), NamedTextColor.DARK_AQUA));

        for (MarketListing listing : listings) {
            ItemStack displayItem = listing.getItem().clone();
            ItemMeta meta = displayItem.getItemMeta();
            List<Component> lore = meta.lore() != null ? meta.lore() : new ArrayList<>();
            meta.getPersistentDataContainer().set(LISTING_ID_KEY, PersistentDataType.STRING, listing.getId().toString());

            lore.add(Component.text("----------------", NamedTextColor.DARK_GRAY).decoration(TextDecoration.STRIKETHROUGH, true));
            lore.add(Component.text("価格: ", NamedTextColor.YELLOW).append(Component.text(listing.getPrice() + " G", NamedTextColor.GOLD)));
            lore.add(Component.text("クリックで購入", NamedTextColor.GREEN));

            meta.lore(lore);
            displayItem.setItemMeta(meta);
            inv.addItem(displayItem);
        }

        viewer.openInventory(inv);
    }

    public void openSearchResults(Player player, String query) {
        List<MarketListing> results = manager.search(query);
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE_SEARCH, NamedTextColor.DARK_AQUA));

        for (MarketListing listing : results) {
            ItemStack displayItem = listing.getItem().clone();
            ItemMeta meta = displayItem.getItemMeta();
            List<Component> lore = meta.lore() != null ? meta.lore() : new ArrayList<>();

            meta.getPersistentDataContainer().set(LISTING_ID_KEY, PersistentDataType.STRING, listing.getId().toString());

            lore.add(Component.text("----------------", NamedTextColor.DARK_GRAY).decoration(TextDecoration.STRIKETHROUGH, true));
            lore.add(Component.text("出品者: ", NamedTextColor.GRAY).append(Component.text(Bukkit.getOfflinePlayer(listing.getSellerId()).getName(), NamedTextColor.WHITE)));
            lore.add(Component.text("価格: ", NamedTextColor.YELLOW).append(Component.text(listing.getPrice() + " G", NamedTextColor.GOLD)));

            meta.lore(lore);
            displayItem.setItemMeta(meta);
            inv.addItem(displayItem);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Component titleComp = e.getView().title();
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack current = e.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        if (titleComp.equals(Component.text(TITLE_MAIN, NamedTextColor.DARK_AQUA))) {
            e.setCancelled(true);
            if (current.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) current.getItemMeta();
                if (meta.getOwningPlayer() != null) {
                    openPlayerShop(p, meta.getOwningPlayer());
                }
            } else if (current.getType() == Material.COMPASS) {
                p.closeInventory();
                p.sendMessage(Component.text("[Market] ", NamedTextColor.AQUA).append(Component.text("検索したいキーワードをチャットに入力してください。", NamedTextColor.WHITE)));
                Deepwither.getInstance().getMarketSearchHandler().startSearch(p);
            }
        } else if (e.getView().title().toString().contains(TITLE_SHOP_PREFIX) || titleComp.equals(Component.text(TITLE_SEARCH, NamedTextColor.DARK_AQUA))) {
            e.setCancelled(true);
            ItemMeta meta = current.getItemMeta();
            if (meta == null) return;

            String idStr = meta.getPersistentDataContainer().get(LISTING_ID_KEY, PersistentDataType.STRING);
            if (idStr == null) return;

            UUID listingId = UUID.fromString(idStr);
            Optional<MarketListing> listingOpt = manager.getAllListings().stream()
                    .filter(l -> l.getId().equals(listingId))
                    .findFirst();

            if (listingOpt.isEmpty()) {
                p.sendMessage(Component.text("[Market] ", NamedTextColor.RED).append(Component.text("このアイテムは既に売り切れているか、取り下げられています。", NamedTextColor.WHITE)));
                p.closeInventory();
                return;
            }

            MarketListing listing = listingOpt.get();
            if (listing.getSellerId().equals(p.getUniqueId())) {
                p.sendMessage(Component.text("[Market] ", NamedTextColor.RED).append(Component.text("自分の出品を購入することはできません。", NamedTextColor.WHITE)));
                return;
            }

            if (manager.buyItem(p, listing)) {
                p.closeInventory();
            }
        }
    }
}
