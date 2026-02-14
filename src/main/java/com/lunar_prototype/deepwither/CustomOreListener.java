package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@DependsOn({LevelManager.class, ProfessionManager.class, ItemFactory.class})
public class CustomOreListener implements Listener, IManager {
    private static final NamespacedKey KB_RESIST_BACKUP = NamespacedKey.fromString("mining_kb_backup", Deepwither.getInstance());

    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, BukkitTask> taskmap = new ConcurrentHashMap<>();

    public CustomOreListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Material type = event.getBlock().getType();
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
                attribute.setBaseValue(container.get(KB_RESIST_BACKUP, PersistentDataType.DOUBLE));
                container.remove(KB_RESIST_BACKUP);
            }
        }, 5 * 20L));

        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onLeavePlayer(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var container = player.getPersistentDataContainer();
        var attribute = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (container.has(KB_RESIST_BACKUP, PersistentDataType.DOUBLE)) {
            var task = taskmap.remove(event.getPlayer().getUniqueId());
            if (task != null) {
                task.cancel();
            }
            attribute.setBaseValue(container.get(KB_RESIST_BACKUP, PersistentDataType.DOUBLE));
            container.remove(KB_RESIST_BACKUP);
        }
    }

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

        boolean rareDropTriggered = handleCustomDrops(player, block, oreSection);

        int baseExp = oreSection.getInt("exp", 10);
        int finalExp = baseExp;

        if (rareDropTriggered) {
            double bonusMultiplier = 1.5 + (random.nextDouble() * 0.5);
            finalExp = (int) (baseExp * bonusMultiplier);
            player.sendMessage(Component.text("★ 希少鉱石発見！獲得経験値アップ: " + finalExp, NamedTextColor.AQUA));
        }

        Deepwither.getInstance().getLevelManager().addExp(player, finalExp);
        if (Deepwither.getInstance().getProfessionManager() != null) {
            Deepwither.getInstance().getProfessionManager().addExp(player, ProfessionType.MINING, finalExp);
        }

        long respawnTicks = oreSection.getLong("respawn_time", 300) * 20L;
        scheduleRespawn(block, originalType, respawnTicks);
    }

    private boolean handleCustomDrops(Player player, Block block, ConfigurationSection oreSection) {
        List<Map<?, ?>> dropList = oreSection.getMapList("drops");
        if (dropList.isEmpty()) return false;

        ProfessionManager profManager = Deepwither.getInstance().getProfessionManager();
        double doubleDropChance = (profManager != null) ? profManager.getDoubleDropChance(player, ProfessionType.MINING) : 0.0;

        boolean lucky = false;

        for (Map<?, ?> dropEntry : dropList) {
            String dropId = (String) dropEntry.get("item_id");
            double chance = 1.0;

            Object chanceObj = dropEntry.get("chance");
            if (chanceObj instanceof Number) {
                chance = ((Number) chanceObj).doubleValue();
            }

            if (random.nextDouble() <= chance) {
                dropItem(block, dropId);
                if (chance < 1.0) lucky = true;

                if (random.nextDouble() <= doubleDropChance) {
                    dropItem(block, dropId);
                    player.sendMessage(Component.text("★ ダブルドロップ！", NamedTextColor.GOLD));
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 2.0f);
                }
            }
        }
        return lucky;
    }

    private void dropItem(Block block, String dropId) {
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