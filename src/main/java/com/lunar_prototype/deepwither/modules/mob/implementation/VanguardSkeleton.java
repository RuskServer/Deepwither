package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Vanguard Skeleton (近衛スケルトン)
 * 元の melee_skeleton をリメイク。盾と大剣を使いこなし、多彩な近接技を放つ。
 */
public class VanguardSkeleton extends CustomMob {

    private final Random random = new Random();
    private int attackCount = 0;

    @Override
    public void onSpawn() {
        setMaxHealth(120.0);
        if (entity.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(20.0);
        }
        entity.setCustomNameVisible(true);
        entity.customName(Component.text("近衛スケルトン", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));

        // 装備の適用 (ARTIFACT_DROPロジックを独自呼び出し)
        var factory = Deepwither.getInstance().getItemFactoryAPI();
        entity.getEquipment().setItemInMainHand(factory.getItem("arklight_buster"));
        entity.getEquipment().setChestplate(factory.getItem("laps_jacket"));
        entity.getEquipment().setBoots(factory.getItem("laps_tread"));
        entity.getEquipment().setItemInOffHand(new ItemStack(Material.SHIELD));
    }

    @Override
    public void onTick() {
        // AI行動: 1.25秒おきに技を発動
        if (getTicksLived() % 25 == 0) {
            LivingEntity target = getTarget();
            if (target != null) {
                double dist = entity.getLocation().distance(target.getLocation());
                if (dist < 3.0) {
                    // 近距離: 50%でシールドバッシュ、50%でなぎ払い
                    if (random.nextBoolean()) {
                        performShieldBash(target);
                    } else {
                        performCircularSwing();
                    }
                } else if (dist > 6.0 && dist < 12.0) {
                    performTacticalDash(target);
                }
            }
        }
    }

    @Override
    public void onAttack(LivingEntity victim, DeepwitherDamageEvent event) {
        attackCount++;
        
        // 3撃目ごとの強力な一撃
        if (attackCount % 3 == 0) {
            event.setDamage(event.getDamage() * 1.5);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 15, 0.2, 0.2, 0.2, 0.5);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.8f);
            
            if (victim instanceof Player p) {
                p.sendMessage(Component.text(">>> 近衛スケルトンの強撃！", NamedTextColor.RED));
            }
        }
    }

    @Override
    public void onDamaged(LivingEntity attacker, DeepwitherDamageEvent event) {
        // 45%の確率でガード (30% -> 45%)
        if (random.nextDouble() < 0.45) {
            event.setDamage(event.getDamage() * 0.5);
            entity.getWorld().playSound(entity.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);
            entity.getWorld().spawnParticle(Particle.BLOCK, entity.getLocation().add(0, 1, 0), 10, Bukkit.createBlockData(Material.IRON_BLOCK));

            // リポスト (反撃)
            if (attacker != null && entity.getLocation().distance(attacker.getLocation()) < 4.0) {
                performRiposte(attacker);
            }
        }
    }

    @Override
    public void onDeath() {
        // ドロップロジックの再現
        Location deathLoc = entity.getLocation();
        var factory = Deepwither.getInstance().getItemFactoryAPI();

        // arklight_buster (5%)
        if (random.nextDouble() < 0.05) {
            deathLoc.getWorld().dropItemNaturally(deathLoc, factory.getItem("arklight_buster"));
        }
        
        // zombie_head (1%)
        if (random.nextDouble() < 0.01) {
            deathLoc.getWorld().dropItemNaturally(deathLoc, factory.getItem("zombie_head"));
        }

        // lootcrate command (5%)
        if (random.nextDouble() < 0.05) {
            Entity killer = entity.getKiller();
            if (killer instanceof Player p) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crates give " + p.getName() + " lootcrate");
            }
        }
    }

    private void performShieldBash(LivingEntity target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
        Vector dir = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        target.setVelocity(dir.multiply(1.2).setY(0.3));
        
        target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 1, 0), 1);
    }

    private void performCircularSwing() {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
        entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK, entity.getLocation().add(0, 1, 0), 5, 1.5, 0.5, 1.5, 0.1);

        double dmg = 15.0;
        entity.getNearbyEntities(3.5, 2.0, 3.5).stream()
                .filter(e -> e instanceof LivingEntity && !e.equals(entity))
                .map(e -> (LivingEntity) e)
                .forEach(victim -> {
                    com.lunar_prototype.deepwither.core.damage.DamageContext ctx = 
                        new com.lunar_prototype.deepwither.core.damage.DamageContext(entity, victim, DeepwitherDamageEvent.DamageType.PHYSICAL, dmg);
                    Deepwither.getInstance().getDamageProcessor().process(ctx);
                    victim.setVelocity(victim.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(0.5).setY(0.1));
                });
    }

    private void performRiposte(LivingEntity attacker) {
        // 即座に振り向き、反撃
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 1.5f);
        entity.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.2);

        com.lunar_prototype.deepwither.core.damage.DamageContext ctx = 
            new com.lunar_prototype.deepwither.core.damage.DamageContext(entity, attacker, DeepwitherDamageEvent.DamageType.PHYSICAL, 25.0);
        Deepwither.getInstance().getDamageProcessor().process(ctx);
    }

    private void performTacticalDash(LivingEntity target) {
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 1.5f);
        Vector dir = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        entity.setVelocity(dir.multiply(1.5).setY(0.1));
        
        entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation(), 20, 0.2, 0.2, 0.2, 0.1);
    }

    private LivingEntity getTarget() {
        // 簡易的なターゲット取得（最も近いプレイヤー）
        return entity.getWorld().getNearbyEntities(entity.getLocation(), 15, 15, 15).stream()
                .filter(e -> e instanceof Player p && p.getGameMode() != org.bukkit.GameMode.SPECTATOR)
                .map(e -> (LivingEntity) e)
                .findFirst().orElse(null);
    }
}
