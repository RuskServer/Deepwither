package com.lunar_prototype.deepwither;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class BlacksmithListener implements Listener {

    private static final String GUI_TITLE = ChatColor.DARK_GRAY + "鍛冶屋メニュー";

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Inventory clickedInventory = e.getClickedInventory();
        if (clickedInventory == null) return;

        // 鍛冶屋GUI以外でのクリックは無視
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;

        // GUI内のアイテム操作を禁止
        e.setCancelled(true);

        Player player = (Player) e.getWhoClicked();
        ItemStack clickedItem = e.getCurrentItem();

        if (clickedItem == null || clickedItem.getItemMeta() == null) return;

        String displayName = clickedItem.getItemMeta().getDisplayName();

        // --- 修理ボタンの処理 ---
        if (displayName.equals(ChatColor.GREEN + "武器修理")) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();

            if (mainHand.getType().isAir()) {
                player.sendMessage(ChatColor.RED + "修理するアイテムをメインハンドに持ってください。");
                player.closeInventory();
                return;
            }

            // RepairManagerの修理ロジックを実行
            new RepairManager(Deepwither.getInstance()).repairItem(player,mainHand);
            player.closeInventory(); // 修理後はGUIを閉じる

        }
        // --- 未実装ボタンのフィードバック ---
        else if (displayName.equals(ChatColor.AQUA + "装備強化") || displayName.equals(ChatColor.AQUA + "アイテムクラフト")) {
            player.sendMessage(ChatColor.YELLOW + "この機能はまだ実装されていません。");
        }
    }
}