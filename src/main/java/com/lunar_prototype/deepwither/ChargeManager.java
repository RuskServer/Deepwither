package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerQuitEvent; // 追加: 退出時の処理用
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask; // 追加

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({})
public class ChargeManager implements Listener, IManager {
    private final Map<UUID, Long> chargeStartTimes = new HashMap<>();
    private final Map<UUID, String> fullyChargedType = new HashMap<>();
    // 追加: 実行中のタスクを管理するMap
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private final JavaPlugin plugin;

    public ChargeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
        for (BukkitTask task : activeTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        activeTasks.clear();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId(); // 何度も使うので変数化

        // アイテムチェック等はスニーク開始時のみ厳密に行えばOKですが、
        // 解除時はタスクキャンセルだけしたいので処理順を整理します

        if (e.isSneaking()) {
            // === スニーク開始時の処理 ===

            // 既にタスクが動いている場合は重複を防ぐため何もしない（またはリセット）
            if (activeTasks.containsKey(uuid)) {
                return;
            }

            ItemStack item = p.getInventory().getItemInMainHand();
            if (item == null || !item.hasItemMeta()) return;
            String type = item.getItemMeta().getPersistentDataContainer().get(ItemLoader.CHARGE_ATTACK_KEY, PersistentDataType.STRING);
            if (type == null) return;

            chargeStartTimes.put(uuid, System.currentTimeMillis());

            // タスクを生成し、Mapに保存
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    // プレイヤーが無効、またはスニークをやめている場合はキャンセル
                    if (!p.isOnline() || !p.isSneaking()) {
                        stopCharge(uuid); // キャンセル処理へ
                        return;
                    }

                    // 開始時間が記録されていない場合（何らかの理由で消えた場合）
                    if (!chargeStartTimes.containsKey(uuid)) {
                        stopCharge(uuid);
                        return;
                    }

                    long duration = System.currentTimeMillis() - chargeStartTimes.get(uuid);

                    // --- 音演出 ---
                    if (duration < 2500 && (duration % 400) < 100) { // 2ticks(100ms)の幅を持たせて判定
                        float pitch = 0.5f + ((float) duration / 2500f) * 0.7f;
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.6f, pitch);
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, pitch);
                    }

                    if (duration >= 2500) { // 完了
                        if (!fullyChargedType.containsKey(uuid)) {
                            fullyChargedType.put(uuid, type);
                            p.sendMessage("§6§l★ 溜め完了！ ★");
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.5f);
                        }
                        // 完了中の継続エフェクト
                        if (duration % 200 < 100) {
                            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 2.0f);
                        }
                        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.02);
                    } else {
                        // 溜め中エフェクト
                        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 1, 0.3, 0.3, 0.3, 0.01);
                    }
                }
            }.runTaskTimer(plugin, 0L, 2L);

            activeTasks.put(uuid, task);

        } else {
            // === スニーク解除時の処理 ===
            // シフトを離したら即座にタスク停止＆リセット
            stopCharge(uuid);
        }
    }

    // 安全のため、プレイヤーが退出した際もリセットする
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopCharge(e.getPlayer().getUniqueId());
    }

    // 溜め処理を中断・リセットするメソッド
    private void stopCharge(UUID uuid) {
        // タスクが生きていればキャンセル
        if (activeTasks.containsKey(uuid)) {
            activeTasks.get(uuid).cancel();
            activeTasks.remove(uuid);
        }
        // ステータスリセット
        chargeStartTimes.remove(uuid);
        fullyChargedType.remove(uuid); // 攻撃せずに離した場合はチャージ完了状態も消すならこれが必要
    }

    // 溜め状態を消費し、溜まっていたタイプを返す
    public String consumeCharge(UUID uuid) {
        // 攻撃時にもタスクを止める（攻撃後の余韻を残さない場合）
        // もし攻撃後もタスクを残したい場合は stopCharge(uuid) を呼ばないでください
        stopCharge(uuid);
        return fullyChargedType.remove(uuid);
    }
}