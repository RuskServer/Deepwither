package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
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
    private int globalCooldown = 0; // スキル共通クールダウン

    @Override
    public void onSpawn() {
        setMaxHealth(420.0);
        entity.setCustomName("§4§lEngraved Executor");
        entity.setCustomNameVisible(true);

        // 属性設定 (1.21形式) - 難易度調整: 攻撃力をさらに半分に(48->24)
        if (entity.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(24.0);
        }
        if (entity.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.2);
        }

        // 装備
        ItemStack mainHand = DW.items().getItem("深淵の海月");
        entity.getEquipment().setItemInMainHand(mainHand != null ? mainHand : new ItemStack(Material.NETHERITE_SWORD));
        entity.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));

        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 0.7f);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 0.7f, 1.2f);
        spawnAbyssAura(entity.getLocation().add(0, 1, 0), 0.8);
    }

    @Override
    public void onTick() {
        if (!(entity instanceof Mob)) return;
        Mob mob = (Mob) entity;
        LivingEntity target = mob.getTarget();

        // クールダウン減少処理
        if (dashCooldown > 0) dashCooldown--;
        if (swordRainCooldown > 0) swordRainCooldown--;
        if (globalCooldown > 0) globalCooldown--;

        // 足元の魔法陣パーティクル
        if (getTicksLived() % 5 == 0) {
            drawCircle(entity.getLocation(), 1.0, Color.fromRGB(10, 18, 48));
            spawnAbyssAura(entity.getLocation().add(0, 1, 0), 0.5);
        }

        if (target != null && globalCooldown <= 0) {
            double distance = entity.getLocation().distance(target.getLocation());

            // スキル1: 執行者の瞬歩 (個別CD延長: 15s -> 25s)
            if (distance > 12.0 && dashCooldown <= 0) {
                executeExecutorDash(target);
                dashCooldown = 500; 
                globalCooldown = 100; // 共通CD 5秒
            }

            // スキル2: 刻印の剣雨 (個別CD延長: 10s -> 20s)
            else if (distance < 5.0 && swordRainCooldown <= 0) {
                executeSwordRain(target);
                swordRainCooldown = 400; 
                globalCooldown = 100; // 共通CD 5秒
            }
        }
    }

    /**
     * 執行者の瞬歩 - ターゲットに高速で接近
     */
    private void executeExecutorDash(LivingEntity target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 0.6f);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_WET_GRASS_STEP, 0.8f, 0.7f);

        Vector direction = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        entity.setVelocity(direction.multiply(1.3).setY(0.2));

        // 軌跡パーティクル
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i > 5) { this.cancel(); return; }
                Location loc = entity.getLocation().add(0, 1.0, 0);
                entity.getWorld().spawnParticle(Particle.BUBBLE, loc, 8, 0.35, 0.4, 0.35, 0.04);
                entity.getWorld().spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, loc, 4, 0.25, 0.3, 0.25, 0.02);
                entity.getWorld().spawnParticle(Particle.DUST, loc, 8, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(Color.fromRGB(18, 95, 112), 1.1f));
                i++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 2L);
    }

    /**
     * 刻印の剣雨 - 上空から剣を降らせる
     */
    private void executeSwordRain(LivingEntity target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 1.0f, 0.8f);
        entity.getWorld().playSound(entity.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.9f, 0.8f);

        Location targetLoc = target.getLocation();
        
        // 魔法陣の展開 (予兆)
        drawCircle(targetLoc, 5.0, Color.fromRGB(77, 229, 255));
        spawnAbyssAura(targetLoc.clone().add(0, 1.0, 0), 1.5);
        
        // 20tick(1秒)の猶予
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
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
                    // 着弾ダメージ
                    loc.getWorld().spawnParticle(Particle.BUBBLE, loc, 14, 0.45, 0.45, 0.45, 0.08);
                    loc.getWorld().spawnParticle(Particle.SQUID_INK, loc, 6, 0.35, 0.35, 0.35, 0.02);
                    loc.getWorld().spawnParticle(Particle.DUST, loc, 10, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(Color.fromRGB(10, 18, 48), 1.2f));
                    loc.getWorld().playSound(loc, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.7f, 0.8f);
                    
                    // 着弾ダメージ - 威力調整: 15.0 -> 7.5
                    applyRadiusDamage(loc, 2.0, 7.5, DeepwitherDamageEvent.DamageType.MAGIC);
                    this.cancel();
                    return;
                }
                
                loc.add(0, -0.8, 0);
                loc.getWorld().spawnParticle(Particle.BUBBLE, loc, 2, 0.08, 0.08, 0.08, 0.02);
                loc.getWorld().spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, loc, 2, 0.08, 0.08, 0.08, 0.01);
                loc.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(18, 95, 112), 1.0f));
                
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
            loc.getWorld().spawnParticle(Particle.BUBBLE, loc, 1, 0.02, 0.02, 0.02, 0.0);
            loc.subtract(x, 0.1, z);
        }
    }

    private void spawnAbyssAura(Location loc, double intensity) {
        loc.getWorld().spawnParticle(Particle.BUBBLE, loc, 10, 0.35 * intensity, 0.4 * intensity, 0.35 * intensity, 0.02);
        loc.getWorld().spawnParticle(Particle.SQUID_INK, loc, 6, 0.25 * intensity, 0.3 * intensity, 0.25 * intensity, 0.02);
        loc.getWorld().spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, loc, 4, 0.2 * intensity, 0.25 * intensity, 0.2 * intensity, 0.01);
        loc.getWorld().spawnParticle(Particle.DUST, loc, 12, 0.4 * intensity, 0.4 * intensity, 0.4 * intensity, 0,
                new Particle.DustOptions(Color.fromRGB(77, 229, 255), 1.0f + (float) intensity * 0.3f));
    }

    @Override
    public void onDamaged(LivingEntity attacker, DeepwitherDamageEvent event) {
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.8f, 0.5f);
    }

    @Override
    public void onDeath() {
        Location deathLoc = entity.getLocation();
        var factory = Deepwither.getInstance().getItemFactoryAPI();

        entity.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, entity.getLocation().add(0, 1, 0), 2);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
        handleDrop("深淵の海月", 0.05, deathLoc, factory);
    }

    private void handleDrop(String id, double chance, Location loc, com.lunar_prototype.deepwither.api.IItemFactory factory) {
        if (random.nextDouble() < chance) {
            ItemStack item = factory.getItem(id);
            if (item != null) loc.getWorld().dropItemNaturally(loc, item);
        }
    }
}
