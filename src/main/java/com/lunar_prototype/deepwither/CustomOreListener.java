package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CustomOreListener implements Listener {

    private final Deepwither plugin;
    private final Random random = new Random(); // 確率判定に使用

    public CustomOreListener(Deepwither plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        Material originalType = block.getType();

        ConfigurationSection oreSection = plugin.getConfig().getConfigurationSection("ore_setting." + originalType.name());

        if (oreSection == null) {
            return;
        }

        event.setCancelled(true);

        block.setType(Material.BEDROCK);

        // ★修正後のカスタムドロップを処理
        handleCustomDrops(block, oreSection);

        long respawnTicks = oreSection.getLong("respawn_time", 300) * 20L;
        scheduleRespawn(block, originalType, respawnTicks);
    }

    /**
     * 確率に基づいてカスタムドロップアイテムをドロップさせる
     */
    private void handleCustomDrops(Block block, ConfigurationSection oreSection) {
        // ConfigurationSection#getMapList() は List<Map<String, Object>> を返すため、キャストして使用
        List<Map<?, ?>> dropList = oreSection.getMapList("drops");

        if (dropList.isEmpty()) return;

        for (Map<?, ?> dropEntry : dropList) {
            // ConfigurationSectionから読み込んだデータはMap<Object, Object>になることがあるため、適切なキャストを行う
            String dropId = (String) dropEntry.get("item_id");

            // 確率は double で取得し、デフォルトは 1.0f (100%)
            double chance = 1.0;
            Object chanceObj = dropEntry.get("chance");
            if (chanceObj instanceof Number) {
                chance = ((Number) chanceObj).doubleValue();
            } else {
                plugin.getLogger().warning("Invalid chance value for drop ID: " + dropId);
            }

            // 確率判定
            if (random.nextDouble() <= chance) {
                // ドロップ成功
                File itemFolder = new File(plugin.getDataFolder(), "items");
                ItemStack customDrop = Deepwither.getInstance().getItemFactory().getCustomItemStack(dropId);

                if (customDrop != null) {
                    // ブロックの中央からドロップ
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), customDrop);
                } else {
                    plugin.getLogger().warning("Custom Item ID not found: " + dropId);
                }
            }
        }
    }

    /**
     * ブロックのリスポーンをスケジュールする
     */
    private void scheduleRespawn(Block block, Material originalType, long respawnTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (block.getType() == Material.BEDROCK) {
                    block.setType(originalType);
                }
            }
        }.runTaskLater(plugin, respawnTicks);
    }
}