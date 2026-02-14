package com.lunar_prototype.deepwither.loot;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;

        if (manager.isActiveLootChest(block.getLocation())) {
            event.setCancelled(true);
            Chest chestState = (Chest) block.getState();
            Inventory inventory = chestState.getInventory();

            for (ItemStack item : inventory.getContents()) {
                if (item != null) {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
                }
            }

            manager.despawnLootChest(block.getLocation());
            event.getPlayer().sendMessage(Component.text("ルートチェストの中身を回収しました。", NamedTextColor.YELLOW));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof Chest chestState)) return;
        Block block = chestState.getBlock();

        if (manager.isActiveLootChest(block.getLocation())) {
            if (inv.isEmpty()) {
                manager.despawnLootChest(block.getLocation());
                event.getPlayer().sendMessage(Component.text("ルートチェストを閉じたため、消滅しました。", NamedTextColor.GRAY));
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.CHEST) return;
        if (manager.isActiveLootChest(event.getClickedBlock().getLocation())) {
            // Future custom interaction logic
        }
    }
}
