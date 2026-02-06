package com.lunar_prototype.deepwither.loot;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
// PDC関連のインポート (NamespacedKey, PersistentDataType) は不要になりました。

@DependsOn({LootChestManager.class})
public class LootChestListener implements Listener, IManager {

    private final JavaPlugin plugin;
    private LootChestManager manager;

    public LootChestListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.manager = Deepwither.getInstance().getLootChestManager();
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    // -----------------------------------------------------------------
    // 1. チェストが破壊されたときの処理
    // -----------------------------------------------------------------
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (block.getType() != Material.CHEST) return;

        // ★修正点: Managerの追跡マップで照合
        if (manager.isActiveLootChest(block.getLocation())) {
            event.setCancelled(true); // 破壊をキャンセル

            Chest chestState = (Chest) block.getState();
            Inventory inventory = chestState.getInventory();

            // 中身をすべて地面にドロップさせる
            for (ItemStack item : inventory.getContents()) {
                if (item != null) {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
                }
            }

            // 追跡を終了させ、ブロックを消去
            manager.despawnLootChest(block.getLocation());
            event.getPlayer().sendMessage("§eルートチェストの中身を回収しました。");
        }
    }

    // -----------------------------------------------------------------
    // 2. インベントリが閉じられたときの処理 (空になったら消滅)
    // -----------------------------------------------------------------
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();

        if (!(inv.getHolder() instanceof Chest)) return;

        Chest chestState = (Chest) inv.getHolder();
        Block block = chestState.getBlock();

        // ★修正点: Managerの追跡マップで照合
        if (manager.isActiveLootChest(block.getLocation())) {
            // 中身が空になっているか確認
            if (inv.isEmpty()) {
                // 空なら即座に消滅させる
                manager.despawnLootChest(block.getLocation());
                event.getPlayer().sendMessage("§7ルートチェストを閉じたため、消滅しました。");
            }
        }
    }

    // -----------------------------------------------------------------
    // 3. インタラクト時の処理
    // -----------------------------------------------------------------
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.CHEST) return;

        Block block = event.getClickedBlock();

        // ★修正点: Managerの追跡マップで照合
        if (manager.isActiveLootChest(block.getLocation())) {
            // ルートチェストに対するカスタム操作（例: タイトル変更など）をここに追加
        }
    }
}