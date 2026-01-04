package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.profession.ProfessionManager; // 追加
import com.lunar_prototype.deepwither.profession.ProfessionType;    // 追加
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CustomOreListener implements Listener {

    private final Deepwither plugin;
    private final Random random = new Random();

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

        // ★修正: カスタムドロップ処理
        handleCustomDrops(player, block, oreSection); // player引数を追加

        // ★追加: 採掘経験値の付与
        // configに exp 設定があればそれを使い、なければ固定値(例:10)
        int xpAmount = oreSection.getInt("exp", 10);

        Deepwither.getInstance().getLevelManager().addExp(player,xpAmount);

        // DeepwitherクラスにgetProfessionManager()を追加している前提
        if (Deepwither.getInstance().getProfessionManager() != null) {
            Deepwither.getInstance().getProfessionManager().addExp(player, ProfessionType.MINING, xpAmount);
        }

        long respawnTicks = oreSection.getLong("respawn_time", 300) * 20L;
        scheduleRespawn(block, originalType, respawnTicks);
    }

    /**
     * 確率に基づいてカスタムドロップアイテムをドロップさせる
     */
    private void handleCustomDrops(Player player, Block block, ConfigurationSection oreSection) {
        List<Map<?, ?>> dropList = oreSection.getMapList("drops");
        if (dropList.isEmpty()) return;

        // ★追加: 職業マネージャーの取得
        ProfessionManager profManager = Deepwither.getInstance().getProfessionManager();
        double doubleDropChance = 0.0;

        if (profManager != null) {
            // 現在のダブルドロップ確率を取得
            doubleDropChance = profManager.getDoubleDropChance(player, ProfessionType.MINING);
        }

        for (Map<?, ?> dropEntry : dropList) {
            String dropId = (String) dropEntry.get("item_id");

            double chance = 1.0;
            Object chanceObj = dropEntry.get("chance");
            if (chanceObj instanceof Number) {
                chance = ((Number) chanceObj).doubleValue();
            } else {
                plugin.getLogger().warning("Invalid chance value for drop ID: " + dropId);
            }

            // 通常のドロップ判定
            if (random.nextDouble() <= chance) {
                dropItem(block, dropId); // 通常ドロップ

                // ★追加: ダブルドロップ判定
                // 採掘レベルに応じた確率で、もう一度同じアイテムをドロップ
                if (random.nextDouble() <= doubleDropChance) {
                    dropItem(block, dropId); // 2個目をドロップ

                    // 視覚的フィードバック (任意)
                    player.sendMessage(ChatColor.GOLD + "★ ダブルドロップ！");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 2.0f);
                }
            }
        }
    }

    // ドロップ処理を共通化
    private void dropItem(Block block, String dropId) {
        File itemFolder = new File(plugin.getDataFolder(), "items");
        // Deepwither.getInstance() へのアクセスはstatic import等で行っている前提、または修正してください
        ItemStack customDrop = Deepwither.getInstance().getItemFactory().getCustomItemStack(dropId);

        if (customDrop != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), customDrop);
        } else {
            plugin.getLogger().warning("Custom Item ID not found: " + dropId);
        }
    }

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