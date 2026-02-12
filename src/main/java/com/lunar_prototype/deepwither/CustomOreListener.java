package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.profession.ProfessionManager; // 追加
import com.lunar_prototype.deepwither.profession.ProfessionType;    // 追加
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@DependsOn({LevelManager.class, ProfessionManager.class, ItemFactory.class})
public class CustomOreListener implements Listener, IManager {
    private static final NamespacedKey KB_RESIST_BACKUP = NamespacedKey.fromString("mining_kb_backup", Deepwither.getInstance()); // PersistentDataType.DOUBLE

    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, BukkitTask> taskmap = new ConcurrentHashMap<>(); // 同期かどうか確認するやる気はないのだ...

    public CustomOreListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    /**
     * ブロックを叩き始めた時にノックバック耐性を付与
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Material type = event.getBlock().getType();
        // カスタム鉱石の設定があるか確認
        if (!plugin.getConfig().contains("ore_setting." + type.name())) {
            return;
        }

        var source = event.getPlayer();
        var container = source.getPersistentDataContainer();
        var attribute = source.getAttribute(Attribute.KNOCKBACK_RESISTANCE);

        if (attribute == null) {
            return;
        }

        if (!taskmap.containsKey(source.getUniqueId())) {
            container.set(KB_RESIST_BACKUP, PersistentDataType.DOUBLE, attribute.getBaseValue());
        }

        attribute.setBaseValue(1.0);

        var task = taskmap.put(source.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (source.isOnline()) {
                // 抑制: 順序的にsetされていることが保証されているはず
                //noinspection DataFlowIssue
                attribute.setBaseValue(container.get(KB_RESIST_BACKUP, PersistentDataType.DOUBLE));
            }
        }, 5 * 20L));

        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onLeavePlayer(PlayerQuitEvent event) {
        var task = taskmap.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * プレイヤーのノックバック耐性を設定するヘルパーメソッド
     */
    private void setKnockbackResistance(Player player, double value) {
        AttributeInstance attr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (attr != null) {
            attr.setBaseValue(value);
        }
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

        // ★修正: レアドロップが発生したかを受け取る
        boolean rareDropTriggered = handleCustomDrops(player, block, oreSection);

        // 基本経験値の取得
        int baseExp = oreSection.getInt("exp", 10);
        int finalExp = baseExp;

        // ★追加: レアドロップボーナス (50% ~ 100% アップ)
        if (rareDropTriggered) {
            // 1.5倍から2.0倍のランダム倍率
            double bonusMultiplier = 1.5 + (random.nextDouble() * 0.5);
            finalExp = (int) (baseExp * bonusMultiplier);

            player.sendMessage(ChatColor.AQUA + "★ 希少鉱石発見！獲得経験値アップ: " + finalExp);
        }

        // 経験値付与 (finalExpを使用)
        Deepwither.getInstance().getLevelManager().addExp(player, finalExp);
        if (Deepwither.getInstance().getProfessionManager() != null) {
            Deepwither.getInstance().getProfessionManager().addExp(player, ProfessionType.MINING, finalExp);
        }

        long respawnTicks = oreSection.getLong("respawn_time", 300) * 20L;
        scheduleRespawn(block, originalType, respawnTicks);
    }

    /**
     * 確率に基づいてカスタムドロップアイテムをドロップさせる
     * @return レアドロップ（chance < 1.0）が発生した場合は true
     */
    private boolean handleCustomDrops(Player player, Block block, ConfigurationSection oreSection) {
        List<Map<?, ?>> dropList = oreSection.getMapList("drops");
        if (dropList.isEmpty()) return false;

        ProfessionManager profManager = Deepwither.getInstance().getProfessionManager();
        double doubleDropChance = (profManager != null) ? profManager.getDoubleDropChance(player, ProfessionType.MINING) : 0.0;

        boolean lucky = false; // レアドロップフラグ

        for (Map<?, ?> dropEntry : dropList) {
            String dropId = (String) dropEntry.get("item_id");
            double chance = 1.0;

            Object chanceObj = dropEntry.get("chance");
            if (chanceObj instanceof Number) {
                chance = ((Number) chanceObj).doubleValue();
            }

            // ドロップ判定
            if (random.nextDouble() <= chance) {
                dropItem(block, dropId);

                // ★追加: レアドロップ(chance < 1.0) かつ成功ならフラグを立てる
                if (chance < 1.0) {
                    lucky = true;
                }

                // ダブルドロップ判定
                if (random.nextDouble() <= doubleDropChance) {
                    dropItem(block, dropId);
                    player.sendMessage(ChatColor.GOLD + "★ ダブルドロップ！");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 2.0f);
                }
            }
        }
        return lucky;
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
