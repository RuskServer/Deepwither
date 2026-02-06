package com.lunar_prototype.deepwither.companion;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

@DependsOn({CompanionManager.class})
public class CompanionListener implements Listener, IManager {

    private CompanionManager manager;
    private final JavaPlugin plugin;

    public CompanionListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.manager = Deepwither.getInstance().getCompanionManager();
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    // 右クリックで騎乗
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (manager.isCompanion(e.getRightClicked())) {
            CompanionManager.ActiveCompanion ac = manager.getActiveCompanion(e.getRightClicked().getUniqueId());
            if (ac != null && ac.data.isRideable() && ac.owner.equals(e.getPlayer())) {
                e.getRightClicked().addPassenger(e.getPlayer());
            }
        }
    }

    // フレンドリーファイア防止 (プレイヤー -> 自分のコンパニオン)
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && manager.isCompanion(e.getEntity())) {
            CompanionManager.ActiveCompanion ac = manager.getActiveCompanion(e.getEntity().getUniqueId());
            if (ac != null && ac.owner.equals(p)) {
                e.setCancelled(true);
            }
        }
        // コンパニオン -> 所有者 も無効化
        if (manager.isCompanion(e.getDamager()) && e.getEntity() instanceof Player p) {
            CompanionManager.ActiveCompanion ac = manager.getActiveCompanion(e.getDamager().getUniqueId());
            if (ac != null && ac.owner.equals(p)) {
                e.setCancelled(true);
            }
        }
    }

    // コンパニオン死亡時の処理
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (manager.isCompanion(e.getEntity())) {
            e.getDrops().clear(); // アイテムを落とさない
            e.setDroppedExp(0);
            // 必要なら所有者にメッセージを送るなど
        }
    }

    @EventHandler
    public void onPlayerInput(PlayerInputEvent e) {
        Player p = e.getPlayer();
        if (!(p.getVehicle() instanceof LivingEntity vehicle)) return;

        CompanionManager.ActiveCompanion ac = manager.getActiveCompanion(vehicle.getUniqueId());
        if (ac == null || !ac.owner.equals(p)) return;

        // 現在の入力を保存
        ac.currentInput = e.getInput();

        // スニーク（降りる）だけは瞬時性が高いのでここで処理してもOK
        if (e.getInput().isSneak()) {
            vehicle.removePassenger(p);
        }
    }

    // ログアウト時に消去
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.despawnCompanion(e.getPlayer());
    }
}