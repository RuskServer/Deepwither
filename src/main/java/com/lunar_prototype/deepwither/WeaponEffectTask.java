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
            } else if ("ocean".equals(effect)) {
                spawnOceanHelix(player);
            } else if ("abyss".equals(effect)) {
                spawnAbyssHelix(player);
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

    private void spawnOceanHelix(Player player) {
        Location loc = player.getLocation();
        double radius = 1.0;
        double heightMax = 2.0;
        double step = 0.4;
        double speed = 0.25;

        for (double y = 0; y <= heightMax; y += step) {
            double angle = (ticks * speed) + (y * 2.0);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location particleLoc = loc.clone().add(x, y, z);
            
            // 泡と水しぶき
            player.getWorld().spawnParticle(Particle.BUBBLE, particleLoc, 1, 0.05, 0.05, 0.05, 0.02);
            if (ticks % 2 == 0) {
                player.getWorld().spawnParticle(Particle.SPLASH, particleLoc, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }
        
        // 足元に波紋のような演出
        if (ticks % 10 == 0) {
            player.getWorld().spawnParticle(Particle.DRIPPING_WATER, loc, 3, 0.5, 0.1, 0.5, 0.05);
        }
    }

    private void spawnAbyssHelix(Player player) {
        Location loc = player.getLocation();
        double radius = 0.8;
        double heightMax = 2.2;
        double step = 0.3;
        double speed = -0.2; // 逆回転

        for (double y = 0; y <= heightMax; y += step) {
            double angle = (ticks * speed) + (y * 3.0);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location particleLoc = loc.clone().add(x, y, z);
            
            // 黒い煙と龍の息（紫色）
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, particleLoc, 1, 0.02, 0.02, 0.02, 0.01);
            if (ticks % 3 == 0) {
                player.getWorld().spawnParticle(Particle.DRAGON_BREATH, particleLoc, 1, 0.01, 0.01, 0.01, 0.005);
            }
            
            // 時折白い火花（深淵の裂け目）
            if (ticks % 15 == 0 && Math.random() < 0.2) {
                player.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0.01);
            }
        }
    }
}
