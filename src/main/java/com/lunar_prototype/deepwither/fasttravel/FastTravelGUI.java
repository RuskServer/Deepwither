package com.lunar_prototype.deepwither.fasttravel;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FastTravelGUI implements Listener {

    private final Deepwither plugin;
    private final FastTravelManager manager;
    private static final Component TITLE = Component.text("Fast Travel", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
    private static final int GRID_START = 10;

    public FastTravelGUI(Deepwither plugin, FastTravelManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fillBackground(inv);

        Collection<FastTravelPoint> playerPoints = manager.getPlayerPoints(player);
        int count = 0;
        for (FastTravelPoint point : playerPoints) {
            int row = count / 7;
            int col = count % 7;
            int actualSlot = GRID_START + (row * 9) + col;
            
            if (actualSlot >= 44) break;
            
            inv.setItem(actualSlot, createPointIcon(point));
            count++;
        }

        inv.setItem(49, createCloseButton());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 0.5f, 1f);
    }

    private ItemStack createPointIcon(FastTravelPoint point) {
        ItemStack item = new ItemStack(point.getIcon());
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(Component.text(point.getDisplayName(), NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("ステータス: ", NamedTextColor.GRAY).append(Component.text("解放済み", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("▶ クリックして移動", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
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
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();
        Collection<FastTravelPoint> playerPoints = manager.getPlayerPoints(player);
        int count = 0;
        for (FastTravelPoint point : playerPoints) {
            int row = count / 7;
            int col = count % 7;
            int actualSlot = GRID_START + (row * 9) + col;

            if (slot == actualSlot) {
                player.closeInventory();
                player.teleport(point.getLocation());
                player.sendMessage(Component.text(">> ", NamedTextColor.AQUA)
                        .append(Component.text(point.getDisplayName(), NamedTextColor.YELLOW))
                        .append(Component.text(" へ移動しました。", NamedTextColor.WHITE)));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                return;
            }
            count++;
        }
    }
}
