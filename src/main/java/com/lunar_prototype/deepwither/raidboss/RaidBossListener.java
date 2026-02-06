package com.lunar_prototype.deepwither.raidboss;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({RaidBossManager.class})
public class RaidBossListener implements Listener, IManager {

    private RaidBossManager raidBossManager;
    private final JavaPlugin plugin;
    private NamespacedKey bossIdKey;

    public RaidBossListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.raidBossManager = Deepwither.getInstance().getRaidBossManager();
        this.bossIdKey = new NamespacedKey(plugin, "raid_boss_id");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onUseSummonItem(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(bossIdKey, PersistentDataType.STRING)) return;

        e.setCancelled(true); // 設置などをキャンセル
        Player player = e.getPlayer();

        // クールダウンなどが欲しければここに追加

        String bossId = pdc.get(bossIdKey, PersistentDataType.STRING);

        // 召喚試行
        if (raidBossManager.spawnBoss(player, bossId)) {
            // 成功したらアイテムを消費
            item.setAmount(item.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1f, 0.5f);
        } else {
            // 失敗時は音を鳴らして消費しない
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        }
    }
}