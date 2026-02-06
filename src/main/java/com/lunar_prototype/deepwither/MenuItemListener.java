package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
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
    private final String ITEM_NAME = "§6§lメニュー §7(右クリック)";

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

    // プレイヤーが参加した時にアイテムを配布
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        giveMenuItem(player);
    }

    // アイテムを右クリックした時にメニューを開く
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ItemStack item = e.getItem();

        if (item == null || item.getType() == Material.AIR) return;
        if (!item.hasItemMeta() || !item.getItemMeta().getDisplayName().equals(ITEM_NAME)) return;

        // 右クリックアクション（空気またはブロック）
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true); // 地面を耕したりするのを防ぐ

            // 既存のMenuGUIを開く
            menuGUI.open(player);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
        }
    }

    // メニューアイテムを特定のスロットにセットするメソッド
    public void giveMenuItem(Player player) {
        ItemStack menuBtn = new ItemStack(Material.COMPASS); // アイテムはお好みで(COMPASSなど)
        ItemMeta meta = menuBtn.getItemMeta();

        meta.setDisplayName(ITEM_NAME);
        meta.setLore(Collections.singletonList("§fクリックしてステータスやスキルを確認します。"));
        menuBtn.setItemMeta(meta);

        // ホットバーの8番スロット（一番右）に配置
        player.getInventory().setItem(8, menuBtn);
    }

    @EventHandler
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent e) {
        ItemStack item = e.getItemDrop().getItemStack();
        if (item.hasItemMeta() && item.getItemMeta().getDisplayName().equals(ITEM_NAME)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        ItemStack item = e.getCurrentItem();
        if (item != null && item.hasItemMeta() && item.getItemMeta().getDisplayName().equals(ITEM_NAME)) {
            // メニュー内ではなく、自分のインベントリ操作を制限
            if (e.getClickedInventory() == e.getWhoClicked().getInventory()) {
                e.setCancelled(true);
            }
        }
    }
}