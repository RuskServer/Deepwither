package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Marksman Zombie (精鋭ゾンビ射手)
 * メッセージを廃止し、パーティクルによる予兆とインジケーターを強化。
 */
public class MarksmanZombie extends AbstractMarksmanArcher {

    private int arrowRainCooldown = 0;

    @Override
    public void onSpawn() {
        setMaxHealth(100.0);
        entity.setCustomNameVisible(true);
        entity.customName(Component.text("ゾンビ", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true));

        var factory = Deepwither.getInstance().getItemFactoryAPI();
        entity.getEquipment().setItemInMainHand(factory.getItem("月光樹"));
        entity.getEquipment().setHelmet(factory.getItem("moonveil_hood"));
    }

    @Override
    public void onTick() {
        if (multiShotCooldown > 0) multiShotCooldown--;
        if (arrowRainCooldown > 0) arrowRainCooldown--;

        LivingEntity target = getNearestTarget(20.0);
        if (target == null) return;

        // マルチショット (18ブロック以内、5〜7秒CD)
        if (multiShotCooldown <= 0 && entity.getLocation().distance(target.getLocation()) < 18.0) {
            prepareMultiShot(target);
            multiShotCooldown = 130 + random.nextInt(40);
        }

        // アローレイン (15ブロック以内、10〜15秒CD)
        if (arrowRainCooldown <= 0 && entity.getLocation().distance(target.getLocation()) < 15.0) {
            executeArrowRain(target);
            arrowRainCooldown = 200 + random.nextInt(100);
        }
    }

    /**
     * マルチショットの予兆
     */
    private void prepareMultiShot(LivingEntity target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.8f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (entity == null || !entity.isValid() || target == null || !target.isValid()) {
                    this.cancel();
                    return;
                }

                if (ticks < 12) {
                    Vector dir = target.getLocation().toVector().subtract(entity.getEyeLocation().toVector()).normalize();
                    Location eyeLoc = entity.getEyeLocation().add(dir.multiply(1.0));
                    
                    // ゾンビらしい緑色の火花インジケーター
                    for (double i = -22.5; i <= 22.5; i += 7.5) {
                        Vector v = rotateVector(dir.clone(), Math.toRadians(i));
                        entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, eyeLoc, 1, v.getX()*0.1, v.getY()*0.1, v.getZ()*0.1, 0.02);
                    }
                } else {
                    performMultiShot(target, 5, 45.0, 2.0);
                    this.cancel();
                }
                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }

    @Override
    protected double getCustomArrowDamage() {
        return 80.0;
    }

    /**
     * アローレイン: 上空への光柱と足元の高密度魔法陣による予兆
     */
    private void executeArrowRain(LivingEntity target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.5f);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);

        Location targetLoc = target.getLocation();
        
        // 1. 上空への光柱（ゾンビから空へ）
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 20 || entity == null || !entity.isValid()) { this.cancel(); return; }
                for (double y = 0; y < 15; y += 1.0) {
                    Location beamLoc = entity.getLocation().add(0, y, 0);
                    entity.getWorld().spawnParticle(Particle.END_ROD, beamLoc, 1, 0.1, 0.1, 0.1, 0.02);
                }
                ticks += 2;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 2L);

        // 2. 魔法陣の予兆（高密度）
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 20) { this.cancel(); return; }
                drawCircle(targetLoc, 5.0, Color.fromRGB(255, 50, 0)); // より警告色に
                ticks += 2;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 2L);

        // 1秒後の発動
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < 12; i++) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            double offsetX = (random.nextDouble() - 0.5) * 8.0;
                            double offsetZ = (random.nextDouble() - 0.5) * 8.0;
                            Location spawnLoc = targetLoc.clone().add(offsetX, 12, offsetZ);
                            spawnFallingArrow(spawnLoc);
                        }
                    }.runTaskLater(Deepwither.getInstance(), i * 2L);
                }
            }
        }.runTaskLater(Deepwither.getInstance(), 20L);
    }

    private void spawnFallingArrow(Location loc) {
        new BukkitRunnable() {
            int life = 0;
            @Override
            public void run() {
                if (life > 20 || loc.getBlock().getType().isSolid()) {
                    loc.getWorld().spawnParticle(Particle.BLOCK, loc, 5, 0.2, 0.2, 0.2, 0.1, Material.BONE_BLOCK.createBlockData());
                    loc.getWorld().playSound(loc, Sound.ENTITY_ARROW_HIT, 0.5f, 1.2f);
                    applyRadiusDamage(loc, 1.5, 20.0);
                    this.cancel();
                    return;
                }
                
                loc.add(0, -0.8, 0);
                loc.getWorld().spawnParticle(Particle.CRIT, loc, 2, 0.05, 0.05, 0.05, 0.01);
                life++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }

    private void applyRadiusDamage(Location loc, double radius, double damage) {
        Collection<Entity> targets = loc.getWorld().getNearbyEntities(loc, radius, radius, radius);
        for (Entity e : targets) {
            if (e instanceof LivingEntity le && !e.equals(entity)) {
                DamageContext ctx = new DamageContext(entity, le, DeepwitherDamageEvent.DamageType.PHYSICAL, damage);
                Deepwither.getInstance().getDamageProcessor().process(ctx);
            }
        }
    }

    private void drawCircle(Location loc, double radius, Color color) {
        for (double t = 0; t < Math.PI * 2; t += Math.PI / 24) { // よりきめ細かく
            double x = radius * Math.cos(t);
            double z = radius * Math.sin(t);
            loc.add(x, 0.1, z);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.8f));
            loc.subtract(x, 0.1, z);
        }
    }

    @Override
    public void onDeath() {
        Location deathLoc = entity.getLocation();
        var factory = Deepwither.getInstance().getItemFactoryAPI();

        handleDrop("abyssal_eye", 0.15, deathLoc, factory);
        handleDrop("calcified_marrow", 0.20, deathLoc, factory);
        handleDrop("corrupted_flesh_scrap", 0.30, deathLoc, factory);
        handleDrop("small_health_potion", 0.05, deathLoc, factory);
        handleDrop("zombie_head", 0.01, deathLoc, factory);

        if (random.nextDouble() < 0.05) {
            Player killer = entity.getKiller();
            if (killer != null) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crates give " + killer.getName() + " lootcrate");
        }
    }

    private void handleDrop(String id, double chance, Location loc, com.lunar_prototype.deepwither.api.IItemFactory factory) {
        if (random.nextDouble() < chance) {
            org.bukkit.inventory.ItemStack item = factory.getItem(id);
            if (item != null) loc.getWorld().dropItemNaturally(loc, item);
        }
    }
}
