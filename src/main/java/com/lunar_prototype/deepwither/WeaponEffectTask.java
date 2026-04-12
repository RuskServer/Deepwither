package com.lunar_prototype.deepwither;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 武器の特殊エフェクト（視覚効果）を描画するための定期タスク。
 */
public class WeaponEffectTask extends BukkitRunnable {
    private final Deepwither plugin;
    private int ticks = 0;

    public WeaponEffectTask(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        ticks++;
        PlayerSettingsManager settingsManager = plugin.get(PlayerSettingsManager.class);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (settingsManager != null && !settingsManager.isEnabled(player, PlayerSettingsManager.SettingType.WEAPON_EFFECT)) {
                continue;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            String effect = meta.getPersistentDataContainer().get(ItemLoader.WEAPON_EFFECT_KEY, PersistentDataType.STRING);

            if ("lava".equals(effect)) {
                spawnLavaHelix(player);
            }
        }
    }

    private void spawnLavaHelix(Player player) {
        Location loc = player.getLocation();
        double radius = 1.0;    // 螺旋の半径
        double heightMax = 2.0; // 螺旋の高さ
        double step = 0.5;      // パーティクルの垂直間隔 (0.25から0.5に増やして密度を減少)
        double speed = 0.3;     // 回転速度

        // プレイヤーの周囲に炎の螺旋を描画
        for (double y = 0; y <= heightMax; y += step) {
            // 時間(ticks)と高さ(y)を組み合わせて螺旋の角度を計算
            double angle = (ticks * speed) + (y * 2.5);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location particleLoc = loc.clone().add(x, y, z);
            
            // 軽く揺らぎを加える
            player.getWorld().spawnParticle(Particle.FLAME, particleLoc, 1, 0.02, 0.02, 0.02, 0.01);
            
            // 頂点付近には煙を少し混ぜる
            if (y > 1.5 && ticks % 4 == 0) {
                player.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 1, 0, 0, 0, 0.01);
            }
        }
    }
}
