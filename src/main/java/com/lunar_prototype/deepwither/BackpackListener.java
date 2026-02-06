package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent; // 追加
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({BackpackManager.class})
public class BackpackListener implements Listener, IManager {

    private final JavaPlugin plugin;
    private final BackpackManager backpackManager;

    public BackpackListener(JavaPlugin plugin, BackpackManager backpackManager) {
        this.plugin = plugin;
        this.backpackManager = backpackManager;
    }

    @Override
    public void init() throws Exception {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        handleReEquip(event.getPlayer());
    }

    // 【変更】死亡時に確実に既存のバックパックを削除する
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        // 死亡した瞬間に一度クリーンアップ（見た目の重複や死体上の残留を防ぐ）
        backpackManager.unequipBackpack(event.getEntity());
    }

    // 【変更】リスポーン時は「再装着」ではなく「新規チェック＆装備」を行う
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // リスポーン直後はインベントリが確定していない場合があるため少し遅らせる
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // 死んだことでバックパックを失っている可能性があるため、
            // handleReEquipではなく、onJoinと同様にアイテム所持チェックから行う
            ItemStack backpackItem = Deepwither.getInstance().getArtifactManager().getPlayerBackpack(player);

            if (backpackItem != null && backpackItem.getType() != Material.AIR) {
                if (backpackItem.hasItemMeta() && backpackItem.getItemMeta().hasCustomModelData()) {
                    int modelData = backpackItem.getItemMeta().getCustomModelData();
                    // 新規装備として処理
                    backpackManager.equipBackpack(player, modelData);
                }
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        handleReEquip(player);
        showExistingBackpacksTo(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        ItemStack backpackItem = Deepwither.getInstance().getArtifactManager().getPlayerBackpack(player);
        if (backpackItem != null && backpackItem.getType() != Material.AIR) {
            if (backpackItem.hasItemMeta() && backpackItem.getItemMeta().hasCustomModelData()) {
                int modelData = backpackItem.getItemMeta().getCustomModelData();
                backpackManager.equipBackpack(player, modelData);
            }
        }

        showExistingBackpacksTo(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        backpackManager.cleanup(event.getPlayer());
    }

    private void showExistingBackpacksTo(Player receiver) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!receiver.isOnline()) return;

            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(receiver)) continue;

                if (backpackManager.hasBackpack(other) && other.getWorld().equals(receiver.getWorld())) {
                    backpackManager.showBackpackToPlayer(other, receiver);
                }
            }
        }, 10L);
    }

    private void handleReEquip(Player player) {
        if (!backpackManager.hasBackpack(player)) return;

        int currentModelData = backpackManager.getModelData(player);
        backpackManager.unequipBackpack(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                // ここでも念のためアイテムチェックを入れるとより安全ですが、
                // テレポートやワールド移動ではアイテムロストしない前提ならこのままでも可
                backpackManager.equipBackpack(player, currentModelData);
            }
        }, 10L);
    }
}