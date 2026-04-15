package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.Random;

/**
 * エルドグール
 * アークグールより軽量だが、近距離のヘルフレアで強引に押し返す雑魚上位モブ。
 */
public class EldGhoul extends CustomMob {

    private static final double MAX_HP = 40.0;
    private static final double ATTACK_DAMAGE = 10.0;
    private static final double MOVE_SPEED = 0.31;
    private static final double ARMOR = 20.0;

    private static final int CHARGE_COOLDOWN = 85;
    private static final int POUNCE_COOLDOWN = 120;
    private static final int HELLFLARE_COOLDOWN = 160;

    private final Random random = new Random();
    private int chargeCooldown;
    private int pounceCooldown;
    private int hellflareCooldown;
    private int pressureTicks;

    @Override
    public void onSpawn() {
        setMaxHealth(MAX_HP);

        if (entity.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            entity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(ATTACK_DAMAGE);
        }
        if (entity.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(MOVE_SPEED);
        }
        if (entity.getAttribute(Attribute.ARMOR) != null) {
            entity.getAttribute(Attribute.ARMOR).setBaseValue(ARMOR);
        }
        if (entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null) {
            entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.25);
        }

        entity.setCustomNameVisible(true);
        entity.customName(Component.text("エルドグール", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);

        equipMainHand();
        equipIfPresent("黯智のヘルメット", item -> entity.getEquipment().setHelmet(item));
        equipIfPresent("虚星のチェストプレート", item -> entity.getEquipment().setChestplate(item));
        equipIfPresent("屍食のレギンス", item -> entity.getEquipment().setLeggings(item));
        equipIfPresent("深淵のブーツ", item -> entity.getEquipment().setBoots(item));

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.9f);
        entity.getWorld().spawnParticle(Particle.FLAME, entity.getLocation().add(0, 1.0, 0), 10, 0.35, 0.5, 0.35, 0.01);
        entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation().add(0, 1.0, 0), 8, 0.35, 0.5, 0.35, 0.01);
    }

    @Override
    public void onTick() {
        if (chargeCooldown > 0) chargeCooldown--;
        if (pounceCooldown > 0) pounceCooldown--;
        if (hellflareCooldown > 0) hellflareCooldown--;
        if (pressureTicks > 0) pressureTicks--;

        LivingEntity target = resolveTarget();
        if (target == null) {
            spawnAmbientParticles();
            return;
        }

        double distance = entity.getLocation().distance(target.getLocation());
        boolean retreating = isRetreating(target);
        boolean lowHp = getHealth() <= getMaxHealth() * 0.55;

        if (distance <= 5.0 && hellflareCooldown <= 0 && shouldHellflare(target, retreating, lowHp)) {
            performHellflare(target);
            return;
        }

        if (shouldPounce(distance, retreating, lowHp)) {
            performPounce(target);
            return;
        }

        if (shouldCharge(distance, retreating, lowHp)) {
            performCharge(target);
            return;
        }

        if (target instanceof Player player && player.getGameMode() == GameMode.SURVIVAL) {
            entity.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1.0, 0), 1, 0.1, 0.1, 0.1, 0.0);
        }

        spawnAmbientParticles();
    }

    @Override
    public void onAttack(LivingEntity victim, DeepwitherDamageEvent event) {
        pressureTicks = Math.max(pressureTicks, 50);
        if (victim != null) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, true, true));
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_BURN, 0.8f, 1.15f);
        }
    }

    @Override
    public void onDamaged(LivingEntity attacker, DeepwitherDamageEvent event) {
        pressureTicks = Math.max(pressureTicks, 70);
        if (attacker != null && entity.getLocation().distance(attacker.getLocation()) <= 7.0) {
            entity.getWorld().playSound(entity.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.9f);
        }
    }

    @Override
    public void onDeath() {
        Location deathLoc = entity.getLocation();
        deathLoc.getWorld().spawnParticle(Particle.FLAME, deathLoc, 40, 0.8, 1.0, 0.8, 0.06);
        deathLoc.getWorld().spawnParticle(Particle.SMOKE, deathLoc, 30, 0.8, 1.0, 0.8, 0.04);
        deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_ZOMBIE_DEATH, 1.1f, 0.75f);

        dropIfPresent("calcified_marrow", 0.15, deathLoc);
        dropIfPresent("zombie_head", 0.05, deathLoc);
        dropIfPresent("ghoul_echo_bone", 0.08, deathLoc);
        dropIfPresent("ghoul_fester_gland", 0.05, deathLoc);
        dropIfPresent("ghoul_death_ashd", 0.20, deathLoc);

        Entity killer = entity.getKiller();
        if (killer instanceof Player p && random.nextDouble() < 0.01) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crates give " + p.getName() + " lootcrate");
        }
    }

    private void equipMainHand() {
        equipIfPresent("虚星剣", item -> entity.getEquipment().setItemInMainHand(item));
    }

    private boolean shouldHellflare(LivingEntity target, boolean retreating, boolean lowHp) {
        if (entity.getLocation().distance(target.getLocation()) > 5.0) return false;
        double chance = lowHp ? 0.70 : 0.45;
        if (retreating) chance += 0.15;
        if (pressureTicks > 20) chance += 0.10;
        return random.nextDouble() < chance;
    }

    private boolean shouldCharge(double distance, boolean retreating, boolean lowHp) {
        if (chargeCooldown > 0) return false;
        if (distance < 5.0 || distance > 16.0) return false;
        if (!retreating && pressureTicks < 20 && !lowHp) return false;
        return random.nextDouble() < (lowHp ? 0.55 : 0.35);
    }

    private boolean shouldPounce(double distance, boolean retreating, boolean lowHp) {
        if (pounceCooldown > 0) return false;
        if (distance < 3.0 || distance > 9.0) return false;
        if (!retreating && pressureTicks < 20 && !lowHp) return false;
        return random.nextDouble() < (lowHp ? 0.65 : 0.45);
    }

    private void performHellflare(LivingEntity target) {
        hellflareCooldown = HELLFLARE_COOLDOWN;
        pressureTicks = Math.max(pressureTicks, 25);

        Location center = entity.getLocation().add(0, 1.0, 0);
        entity.getWorld().playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.7f);
        entity.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 0.9f, 0.8f);
        entity.getWorld().spawnParticle(Particle.FLAME, center, 25, 0.3, 0.3, 0.3, 0.05);
        entity.getWorld().spawnParticle(Particle.SMOKE, center, 15, 0.3, 0.3, 0.3, 0.02);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!entity.isValid() || entity.isDead()) return;
                Location burst = entity.getLocation().add(0, 1.0, 0);
                burst.getWorld().spawnParticle(Particle.FLAME, burst, 80, 1.5, 1.0, 1.5, 0.12);
                burst.getWorld().spawnParticle(Particle.SMOKE, burst, 50, 1.5, 1.0, 1.5, 0.08);
                burst.getWorld().playSound(burst, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 1.1f);

                for (Entity nearby : burst.getWorld().getNearbyEntities(burst, 5.0, 5.0, 5.0)) {
                    if (!(nearby instanceof LivingEntity living) || living.equals(entity)) continue;

                    DamageContext ctx = new DamageContext(entity, living, DeepwitherDamageEvent.DamageType.MAGIC, 18.0);
                    Deepwither.getInstance().getDamageProcessor().process(ctx);

                    Vector away = living.getLocation().toVector().subtract(burst.toVector()).normalize().multiply(1.25);
                    away.setY(0.75);
                    living.setVelocity(away);

                    if (living instanceof Player player) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1, true, true, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, true, true));
                    }
                }
            }
        }.runTaskLater(Deepwither.getInstance(), 4L);
    }

    private void performCharge(LivingEntity target) {
        chargeCooldown = CHARGE_COOLDOWN;
        pressureTicks = Math.max(pressureTicks, 20);

        Vector dir = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        entity.setVelocity(dir.multiply(1.45).setY(0.10));
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.9f, 1.0f);
        entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation().add(0, 1.0, 0), 18, 0.3, 0.2, 0.3, 0.06);

        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            if (!entity.isValid() || entity.isDead()) return;
            Location impact = entity.getLocation();
            impact.getWorld().spawnParticle(Particle.FLAME, impact, 20, 0.4, 0.2, 0.4, 0.05);
            impact.getWorld().playSound(impact, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.1f);
            applyAreaDamage(impact, 2.8, 16.0, DeepwitherDamageEvent.DamageType.PHYSICAL, false, 0.75);
        }, 8L);
    }

    private void performPounce(LivingEntity target) {
        pounceCooldown = POUNCE_COOLDOWN;
        pressureTicks = Math.max(pressureTicks, 25);

        Vector dir = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        entity.setVelocity(dir.multiply(1.05).setY(0.52));
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.9f, 0.8f);
        entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation().add(0, 0.8, 0), 15, 0.35, 0.2, 0.35, 0.04);

        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            if (!entity.isValid() || entity.isDead()) return;
            Location impact = entity.getLocation();
            impact.getWorld().spawnParticle(Particle.FLAME, impact, 60, 1.0, 0.8, 1.0, 0.08);
            impact.getWorld().spawnParticle(Particle.SMOKE, impact, 25, 1.0, 0.8, 1.0, 0.05);
            impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            applyAreaDamage(impact, 3.0, 20.0, DeepwitherDamageEvent.DamageType.PHYSICAL, true, 0.55);
        }, 12L);
    }

    private void applyAreaDamage(Location center, double radius, double damage, DeepwitherDamageEvent.DamageType type, boolean applySlow, double knockbackStrength) {
        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(entity)) continue;

            DamageContext ctx = new DamageContext(entity, living, type, damage);
            Deepwither.getInstance().getDamageProcessor().process(ctx);

            Vector away = living.getLocation().toVector().subtract(center.toVector()).normalize().multiply(knockbackStrength);
            away.setY(0.18);
            living.setVelocity(away);

            if (applySlow && living instanceof Player player) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1, true, true, true));
            }
        }
    }

    private void dropIfPresent(String itemId, double chance, Location loc) {
        if (random.nextDouble() >= chance) return;

        ItemStack item = DW.items().getItem(itemId);
        if (item != null) {
            loc.getWorld().dropItemNaturally(loc, item);
        }
    }

    private boolean equipIfPresent(String itemId, java.util.function.Consumer<ItemStack> slotSetter) {
        ItemStack item = DW.items().getItem(itemId);
        if (item == null) return false;
        slotSetter.accept(item);
        return true;
    }

    private LivingEntity resolveTarget() {
        if (entity instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (isValidTarget(target)) return target;
        }

        LivingEntity nearest = entity.getWorld().getNearbyEntities(entity.getLocation(), 22.0, 10.0, 22.0).stream()
                .filter(e -> e instanceof Player player && player.getGameMode() != GameMode.SPECTATOR)
                .map(e -> (LivingEntity) e)
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(entity.getLocation())))
                .orElse(null);

        if (entity instanceof Mob mob && nearest != null) {
            mob.setTarget(nearest);
        }
        return nearest;
    }

    private boolean isValidTarget(LivingEntity target) {
        return target != null && target.isValid() && !target.isDead() && target.getWorld().equals(entity.getWorld());
    }

    private boolean isRetreating(LivingEntity target) {
        Vector toTarget = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        Vector targetMotion = target.getVelocity().clone();
        targetMotion.setY(0);
        if (targetMotion.lengthSquared() < 0.0001) return false;
        return targetMotion.normalize().dot(toTarget) > 0.15;
    }

    private void spawnAmbientParticles() {
        if (getTicksLived() % 5 != 0) return;
        Location center = entity.getLocation().add(0, 1.0, 0);
        entity.getWorld().spawnParticle(Particle.FLAME, center, 3, 0.25, 0.35, 0.25, 0.02);
        entity.getWorld().spawnParticle(Particle.SMOKE, center, 3, 0.25, 0.35, 0.25, 0.01);
    }
}
