package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({})
public class BlacksmithListener implements Listener, IManager {

    private final JavaPlugin plugin;

    public BlacksmithListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Inventory clickedInventory = e.getClickedInventory();
        if (clickedInventory == null) return;

        // 鍛冶屋GUI以外でのクリックは無視
        if (!e.getView().title().equals(BlacksmithGUI.GUI_TITLE)) return;

        // GUI内のアイテム操作を禁止
        e.setCancelled(true);

        Player player = (Player) e.getWhoClicked();
        ItemStack clickedItem = e.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        Component displayName = clickedItem.getItemMeta().displayName();
        if (displayName == null) return;

        // --- 修理ボタンの処理 ---
        if (displayName.equals(Component.text("武器修理", NamedTextColor.GREEN))) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();

            if (mainHand.getType().isAir()) {
                player.sendMessage(Component.text("修理するアイテムをメインハンドに持ってください。", NamedTextColor.RED));
                player.closeInventory();
                return;
            }

            // RepairManagerの修理ロジックを実行
            new RepairManager(Deepwither.getInstance()).repairItem(player,mainHand);
            player.closeInventory(); // 修理後はGUIを閉じる

        }
        // --- 未実装ボタンのフィードバック ---
        else if (displayName.equals(Component.text("装備強化", NamedTextColor.AQUA))) {
            player.sendMessage(Component.text("この機能はまだ実装されていません。", NamedTextColor.YELLOW));
        }
        else if (displayName.equals(Component.text("アイテムクラフト", NamedTextColor.AQUA))) {
            // ★変更: クラフトGUIを開く
            Deepwither.getInstance().getCraftingGUI().openRecipeList(player);
        }
    }
}