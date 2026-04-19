package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.Deepwither;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Marksman Skeleton (精鋭射手スケルトン)
 * メッセージを廃止し、パーティクルによる予兆（インジケーター）を実装。
 */
public class MarksmanSkeleton extends AbstractMarksmanArcher {

    @Override
    public void onSpawn() {
        setMaxHealth(75.0);
        entity.setCustomNameVisible(true);
        entity.customName(Component.text("スケルトン", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true));

        var factory = Deepwither.getInstance().getItemFactoryAPI();
        entity.getEquipment().setItemInMainHand(factory.getItem("ld_fieldline_bow"));
        entity.getEquipment().setHelmet(factory.getItem("moonveil_hood"));
    }

    @Override
    public void onTick() {
        if (multiShotCooldown > 0) multiShotCooldown--;

        if (multiShotCooldown <= 0) {
            LivingEntity target = getNearestTarget(18.0);
            if (target != null) {
                prepareMultiShot(target);
                multiShotCooldown = 120 + random.nextInt(40); // 溜め時間を考慮してCDを少し調整
            }
        }
    }

    /**
     * マルチショットの予兆演出と実行
     */
    private void prepareMultiShot(LivingEntity target) {
        // 予兆音
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (entity == null || !entity.isValid() || target == null || !target.isValid()) {
                    this.cancel();
                    return;
                }

                // 10tick (0.5秒) の溜め
                if (ticks < 10) {
                    // 放つ方向に火花を表示
                    Vector dir = target.getLocation().toVector().subtract(entity.getEyeLocation().toVector()).normalize();
                    Location eyeLoc = entity.getEyeLocation().add(dir.multiply(1.0));
                    
                    // 簡易的な扇状インジケーター
                    for (double i = -20; i <= 20; i += 10) {
                        Vector v = rotateVector(dir.clone(), Math.toRadians(i));
                        entity.getWorld().spawnParticle(Particle.FIREWORK, eyeLoc, 1, v.getX()*0.1, v.getY()*0.1, v.getZ()*0.1, 0.02);
                    }
                } else {
                    // 実行
                    performMultiShot(target, 5, 40.0, 2.0);
                    this.cancel();
                }
                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }

    @Override
    protected double getCustomArrowDamage() {
        return 70.0;
    }

    @Override
    public void onDeath() {
        Location deathLoc = entity.getLocation();
        var factory = Deepwither.getInstance().getItemFactoryAPI();

        if (random.nextDouble() < 0.05) deathLoc.getWorld().dropItemNaturally(deathLoc, factory.getItem("ld_fieldline_bow"));
        if (random.nextDouble() < 0.05) deathLoc.getWorld().dropItemNaturally(deathLoc, factory.getItem("small_health_potion"));
        if (random.nextDouble() < 0.01) deathLoc.getWorld().dropItemNaturally(deathLoc, factory.getItem("zombie_head"));

        if (random.nextDouble() < 0.05) {
            Player killer = entity.getKiller();
            if (killer != null) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crates give " + killer.getName() + " lootcrate");
        }
    }
}
