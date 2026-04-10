package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMob;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Random;

/**
 * カスタムモブ: 刻印の執行者 (Engraved Executor)
 * ウィザースケルトンベース。圧倒的な近接攻撃力と重厚な魔法攻撃を持つ。
 */
public class EngravedExecutor extends CustomMob {

    private final Random random = new Random();
    private int dashCooldown = 0;
    private int swordRainCooldown = 0;

    @Override
    public void onSpawn() {
        setMaxHealth(420.0);
        entity.setCustomName("§4§lEngraved Executor");
        entity.setCustomNameVisible(true);

        // 属性設定 (1.21形式) - 難易度調整: 攻撃力を適正化(108->48)
        if (entity.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(48.0);
        }
        if (entity.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.2); // 通常移動をわずかに強化
        }

        // 装備
        entity.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        entity.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));

        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
        entity.getWorld().spawnParticle(Particle.ENCHANTED_HIT, entity.getLocation().add(0, 1, 0), 50, 0.5, 1.0, 0.5, 0.1);
    }

    @Override
    public void onTick() {
        if (!(entity instanceof Mob)) return;
        Mob mob = (Mob) entity;
        LivingEntity target = mob.getTarget();

        if (dashCooldown > 0) dashCooldown--;
        if (swordRainCooldown > 0) swordRainCooldown--;

        // 足元の魔法陣パーティクル
        if (getTicksLived() % 5 == 0) {
            drawCircle(entity.getLocation(), 1.0, Color.MAROON);
        }

        if (target != null) {
            double distance = entity.getLocation().distance(target.getLocation());

            // スキル1: 執行者の瞬歩 (調整: 接近速度を緩和)
            if (distance > 12.0 && dashCooldown <= 0) {
                executeExecutorDash(target);
                dashCooldown = 300; // 15秒
            }

            // スキル2: 刻印の剣雨 (近接時 & CD終了)
            if (distance < 5.0 && swordRainCooldown <= 0) {
                executeSwordRain(target);
                swordRainCooldown = 200; // 10秒
            }
        }
    }

    /**
     * 執行者の瞬歩 - ターゲットに高速で接近
     */
    private void executeExecutorDash(LivingEntity target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);

        Vector direction = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        entity.setVelocity(direction.multiply(1.3).setY(0.2)); // 速度を 2.0 -> 1.3 へ緩和

        // 軌跡パーティクル
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i > 5) { this.cancel(); return; }
                entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, entity.getLocation(), 10, 0.3, 0.5, 0.3, 0.05);
                i++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 2L);
    }

    /**
     * 刻印の剣雨 - 上空から剣を降らせる (調整: 予兆後のディレイと威力緩和)
     */
    private void executeSwordRain(LivingEntity target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 2.0f);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.0f, 0.5f);

        Location targetLoc = target.getLocation();
        
        // 魔法陣の展開 (予兆)
        drawCircle(targetLoc, 5.0, Color.RED);
        
        // 20tick(1秒)の猶予を追加
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < 15; i++) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            double offsetX = (random.nextDouble() - 0.5) * 10;
                            double offsetZ = (random.nextDouble() - 0.5) * 10;
                            Location spawnLoc = targetLoc.clone().add(offsetX, 10, offsetZ);
                            
                            spawnFallingSword(spawnLoc);
                        }
                    }.runTaskLater(Deepwither.getInstance(), i * 3L);
                }
            }
        }.runTaskLater(Deepwither.getInstance(), 20L);
    }

    private void spawnFallingSword(Location loc) {
        new BukkitRunnable() {
            int life = 0;
            @Override
            public void run() {
                if (life > 20 || loc.getBlock().getType().isSolid()) {
                    // 着弾ダメージ - 威力緩和(30->15)
                    loc.getWorld().spawnParticle(Particle.CRIT, loc, 10, 0.5, 0.5, 0.5, 0.1);
                    loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_BREAK, 0.5f, 1.5f);
                    
                    applyRadiusDamage(loc, 2.0, 15.0, DeepwitherDamageEvent.DamageType.MAGIC);
                    this.cancel();
                    return;
                }
                
                loc.add(0, -0.8, 0);
                loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0, 0, 0, 0);
                loc.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.GRAY, 1.0f));
                
                if (life % 5 == 0) {
                    loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 0.5f, 1.5f);
                }
                life++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }

    private void applyRadiusDamage(Location loc, double radius, double damage, DeepwitherDamageEvent.DamageType type) {
        Collection<Entity> targets = loc.getWorld().getNearbyEntities(loc, radius, radius, radius);
        for (Entity e : targets) {
            if (e instanceof Player p && !e.equals(entity)) {
                // 無敵時間チェック
                if (p.getNoDamageTicks() > 10) continue;

                DamageContext ctx = new DamageContext(entity, p, type, damage);
                Deepwither.getInstance().getDamageProcessor().process(ctx);

                // 無敵時間を付与
                p.setNoDamageTicks(10);
            }
        }
    }

    private void drawCircle(Location loc, double radius, Color color) {
        for (double t = 0; t < Math.PI * 2; t += Math.PI / 16) {
            double x = radius * Math.cos(t);
            double z = radius * Math.sin(t);
            loc.add(x, 0.1, z);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.0f));
            loc.subtract(x, 0.1, z);
        }
    }

    @Override
    public void onDamaged(LivingEntity attacker, DeepwitherDamageEvent event) {
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.8f, 0.5f);
    }

    @Override
    public void onDeath() {
        entity.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, entity.getLocation().add(0, 1, 0), 2);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
    }
}
