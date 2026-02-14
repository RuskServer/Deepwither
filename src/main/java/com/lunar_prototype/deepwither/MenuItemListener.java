package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;

@DependsOn({MenuGUI.class})
public class MenuItemListener implements Listener, IManager {

    private final Deepwither plugin;
    private MenuGUI menuGUI;
    public static final Component ITEM_NAME = Component.text("メニュー ", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text("(右クリック)", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false));

    public MenuItemListener(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.menuGUI = Deepwither.getInstance().getMenuGUI();
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        giveMenuItem(player);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ItemStack item = e.getItem();

        if (item == null || item.getType() == Material.AIR) return;
        if (!item.hasItemMeta()) return;
        if (!item.getItemMeta().displayName().equals(ITEM_NAME)) return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            menuGUI.open(player);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
        }
    }

    public void giveMenuItem(Player player) {
        ItemStack menuBtn = new ItemStack(Material.COMPASS);
        ItemMeta meta = menuBtn.getItemMeta();

        meta.displayName(ITEM_NAME);
        meta.lore(Collections.singletonList(Component.text("クリックしてステータスやスキルを確認します。", NamedTextColor.WHITE)));
        menuBtn.setItemMeta(meta);

        player.getInventory().setItem(8, menuBtn);
    }

    @EventHandler
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent e) {
        ItemStack item = e.getItemDrop().getItemStack();
        if (item.hasItemMeta() && ITEM_NAME.equals(item.getItemMeta().displayName())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        ItemStack item = e.getCurrentItem();
        if (item != null && item.hasItemMeta() && ITEM_NAME.equals(item.getItemMeta().displayName())) {
            if (e.getClickedInventory() == e.getWhoClicked().getInventory()) {
                e.setCancelled(true);
            }
        }
    }
}
