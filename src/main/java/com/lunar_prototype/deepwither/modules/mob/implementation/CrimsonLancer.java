package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Random;

/**
 * Crimson Lancer (深紅の槍騎兵)
 * 元の melee_zombie2 をリメイク。
 * 強力な突き攻撃とノックバック耐性を持ち、逃げる獲物を許さない。
 */
public class CrimsonLancer extends CustomMob {

    private final Random random = new Random();

    @Override
    public void onSpawn() {
        setMaxHealth(180.0);
        if (entity.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(80.0);
        }
        if (entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null) {
            entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        }
        entity.setCustomNameVisible(true);
        entity.customName(Component.text("深紅の槍騎兵", NamedTextColor.RED).decoration(TextDecoration.BOLD, false));

        // 装備の適用
        var factory = Deepwither.getInstance().getItemFactoryAPI();
        entity.getEquipment().setItemInMainHand(factory.getItem("rift_valtar_lance"));
        entity.getEquipment().setChestplate(factory.getItem("laps_jacket"));
        entity.getEquipment().setBoots(factory.getItem("laps_tread"));
    }

    @Override
    public void onTick() {
        // 主力スキル: クリムゾン・スラスト (約12秒周期に短縮)
        if (getTicksLived() % 240 == 0) {
            performCrimsonThrust();
        }

        // AI行動: 1秒おきに技を判定
        if (getTicksLived() % 20 == 0) {
            LivingEntity target = getTarget();
            if (target != null) {
                double dist = entity.getLocation().distance(target.getLocation());
                if (dist < 3.0) {
                    // 至近距離: 槍の短い突き
                    performShortThrust(target);
                } else if (dist > 5.0 && dist < 15.0 && random.nextDouble() < 0.6) {
                    performLeapAttack(target);
                }
            }
        }
    }

    @Override
    public void onAttack(LivingEntity victim, DeepwitherDamageEvent event) {
        // 槍の攻撃は15%の確率で出血を付与 (10% -> 15%)
        if (random.nextDouble() < 0.15) {
            victim.setMetadata("bleeding", new org.bukkit.metadata.FixedMetadataValue(Deepwither.getInstance(), true));
            victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1, 0), 10, Bukkit.createBlockData(Material.REDSTONE_BLOCK));
            if (victim instanceof Player p) {
                p.sendMessage(Component.text(">>> 出血を負った！", NamedTextColor.DARK_RED));
            }
        }
    }

    @Override
    public void onDamaged(LivingEntity attacker, DeepwitherDamageEvent event) {
        // 50%の確率でバックステップ (至近距離のみ)
        if (attacker != null && entity.getLocation().distance(attacker.getLocation()) < 3.0) {
            if (random.nextDouble() < 0.5) {
                performBackstep(attacker);
            }
        }
    }

    @Override
    public void onDeath() {
        Location deathLoc = entity.getLocation();
        var factory = Deepwither.getInstance().getItemFactoryAPI();

        // ドロップ再現
        handleDrop("rift_valtar_lance", 0.05, deathLoc, factory);
        handleDrop("zombie_head", 0.01, deathLoc, factory);
        handleDrop("abyssal_eye", 0.15, deathLoc, factory);
        handleDrop("calcified_marrow", 0.20, deathLoc, factory);
        handleDrop("corrupted_flesh_scrap", 0.30, deathLoc, factory);

        if (random.nextDouble() < 0.05) {
            Entity killer = entity.getKiller();
            if (killer instanceof Player p) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crates give " + p.getName() + " lootcrate");
            }
        }
    }

    private void performShortThrust(LivingEntity target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.2f);
        entity.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.1);

        DamageContext ctx = new DamageContext(entity, target, DeepwitherDamageEvent.DamageType.PHYSICAL, 40.0);
        Deepwither.getInstance().getDamageProcessor().process(ctx);
    }

    private void performBackstep(LivingEntity attacker) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 0.5f, 1.5f);
        Vector dir = entity.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize();
        dir.setY(0.2).multiply(0.8); // 後ろに軽く飛ぶ
        entity.setVelocity(dir);
        
        entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation(), 10, 0.1, 0.1, 0.1, 0.05);
    }

    private void performCrimsonThrust() {
        LivingEntity target = getTarget();
        if (target == null) return;

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.5f, 0.5f);
        entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK, entity.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.1);

        Vector dir = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        
        // 貫通突き攻撃 (直線状の敵全員にダメージ)
        for (double d = 1.0; d <= 6.0; d += 1.0) {
            Location checkLoc = entity.getEyeLocation().add(dir.clone().multiply(d));
            checkLoc.getWorld().spawnParticle(Particle.CRIT, checkLoc, 5, 0.1, 0.1, 0.1, 0.05);
            
            Collection<Entity> targets = checkLoc.getWorld().getNearbyEntities(checkLoc, 1.5, 1.5, 1.5);
            for (Entity e : targets) {
                if (e instanceof LivingEntity living && !e.equals(entity)) {
                    DamageContext ctx = new DamageContext(entity, living, DeepwitherDamageEvent.DamageType.PHYSICAL, 120.0);
                    Deepwither.getInstance().getDamageProcessor().process(ctx);
                }
            }
        }
    }

    private void performLeapAttack(LivingEntity target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.5f);
        Vector dir = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        
        // 前方へ大ジャンプ
        entity.setVelocity(dir.multiply(1.2).setY(0.6));
        
        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            if (entity.isValid() && !entity.isDead()) {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                entity.getWorld().spawnParticle(Particle.EXPLOSION, entity.getLocation(), 1);
                
                // 着地地点の範囲ダメージ
                Collection<Entity> targets = entity.getWorld().getNearbyEntities(entity.getLocation(), 3.0, 2.0, 3.0);
                for (Entity e : targets) {
                    if (e instanceof LivingEntity living && !e.equals(entity)) {
                        living.setVelocity(new Vector(0, 0.4, 0)); // 打ち上げ
                        DamageContext ctx = new DamageContext(entity, living, DeepwitherDamageEvent.DamageType.PHYSICAL, 60.0);
                        Deepwither.getInstance().getDamageProcessor().process(ctx);
                    }
                }
            }
        }, 15L);
    }

    private void handleDrop(String id, double chance, Location loc, com.lunar_prototype.deepwither.api.IItemFactory factory) {
        if (random.nextDouble() < chance) {
            ItemStack item = factory.getItem(id);
            if (item != null) loc.getWorld().dropItemNaturally(loc, item);
        }
    }

    private LivingEntity getTarget() {
        return entity.getWorld().getNearbyEntities(entity.getLocation(), 15, 15, 15).stream()
                .filter(e -> e instanceof Player p && p.getGameMode() != GameMode.SPECTATOR)
                .map(e -> (LivingEntity) e)
                .findFirst().orElse(null);
    }
}
