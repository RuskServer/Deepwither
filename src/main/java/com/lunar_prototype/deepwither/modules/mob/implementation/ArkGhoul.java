package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.skill.SkillProjectile;
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
import org.bukkit.block.Block;
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
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * アークグール
 * 旧Mythic設定の動きを、Java側のモブ実装に寄せて再現する。
 */
public class ArkGhoul extends CustomMob {

    private static final double MAX_HP = 150.0;
    private static final double ATTACK_DAMAGE = 25.0;
    private static final double MOVE_SPEED = 0.30;

    private static final int ICE_SHOT_COOLDOWN = 90;
    private static final int CHARGE_COOLDOWN = 70;
    private static final int POUNCE_COOLDOWN = 120;

    private int iceShotCooldown;
    private int chargeCooldown;
    private int pounceCooldown;
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
        if (entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null) {
            entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.15);
        }

        entity.setCustomNameVisible(true);
        entity.customName(Component.text("アークグール", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));

        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);

        equipMainHand();
        equipIfPresent("AF_03H", equipment -> entity.getEquipment().setHelmet(equipment));
        equipIfPresent("AF_03C", equipment -> entity.getEquipment().setChestplate(equipment));
        equipIfPresent("AF_03L", equipment -> entity.getEquipment().setLeggings(equipment));
        equipIfPresent("AF_03B", equipment -> entity.getEquipment().setBoots(equipment));

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.1f, 0.7f);
        entity.getWorld().spawnParticle(Particle.SNOWFLAKE, entity.getLocation().add(0, 1.0, 0), 20, 0.5, 1.0, 0.5, 0.03);
        entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation().add(0, 1.0, 0), 15, 0.5, 1.0, 0.5, 0.02);
    }

    @Override
    public void onTick() {
        if (iceShotCooldown > 0) iceShotCooldown--;
        if (chargeCooldown > 0) chargeCooldown--;
        if (pounceCooldown > 0) pounceCooldown--;
        if (pressureTicks > 0) pressureTicks--;

        LivingEntity target = resolveTarget();
        if (target == null) {
            spawnAmbientParticles();
            return;
        }

        double distance = entity.getLocation().distance(target.getLocation());
        boolean retreating = isRetreating(target);
        boolean lowHp = getHealth() <= getMaxHealth() * 0.60;

        if (shouldPounce(target, distance, retreating, lowHp)) {
            performPounce(target);
            return;
        }

        if (shouldCharge(target, distance, retreating, lowHp)) {
            performCharge(target);
            return;
        }

        if (shouldFireIceShot(target, distance, retreating, lowHp)) {
            fireHomingIceShot(target);
            return;
        }

        if (target instanceof Player player && player.getGameMode() == GameMode.SURVIVAL) {
            entity.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1.0, 0), 1, 0.1, 0.1, 0.1, 0.0);
        }

        spawnAmbientParticles();
    }

    @Override
    public void onAttack(LivingEntity victim, DeepwitherDamageEvent event) {
        pressureTicks = Math.max(pressureTicks, 60);

        if (victim != null) {
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.9f, 1.1f);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, true, true));
        }
    }

    @Override
    public void onDamaged(LivingEntity attacker, DeepwitherDamageEvent event) {
        pressureTicks = Math.max(pressureTicks, 80);

        if (attacker != null && entity.getLocation().distance(attacker.getLocation()) <= 8.0) {
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, 0.8f, 0.8f);
        }
    }

    @Override
    public void onDeath() {
        Location deathLoc = entity.getLocation();
        deathLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, deathLoc, 1);
        deathLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, deathLoc, 40, 0.8, 1.0, 0.8, 0.08);
        deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_ZOMBIE_DEATH, 1.2f, 0.8f);

        dropIfPresent("abyss_slash", 0.01, deathLoc);
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
        if (random.nextDouble() < 0.5) {
            if (!equipIfPresent("gravemelt_cleaver", equipment -> entity.getEquipment().setItemInMainHand(equipment))) {
                equipIfPresent("虚星剣", equipment -> entity.getEquipment().setItemInMainHand(equipment));
            }
        } else {
            if (!equipIfPresent("虚星剣", equipment -> entity.getEquipment().setItemInMainHand(equipment))) {
                equipIfPresent("gravemelt_cleaver", equipment -> entity.getEquipment().setItemInMainHand(equipment));
            }
        }
    }

    private boolean shouldFireIceShot(LivingEntity target, double distance, boolean retreating, boolean lowHp) {
        if (iceShotCooldown > 0) return false;
        if (distance < 8.0 || distance > 24.0) return false;
        if (!entity.hasLineOfSight(target)) return false;
        double chance = lowHp ? 0.55 : 0.35;
        if (retreating) chance += 0.15;
        return random.nextDouble() < chance;
    }

    private boolean shouldCharge(LivingEntity target, double distance, boolean retreating, boolean lowHp) {
        if (chargeCooldown > 0) return false;
        if (distance < 6.0 || distance > 16.0) return false;
        if (!retreating && pressureTicks < 25 && !lowHp) return false;
        return random.nextDouble() < (lowHp ? 0.55 : 0.35);
    }

    private boolean shouldPounce(LivingEntity target, double distance, boolean retreating, boolean lowHp) {
        if (pounceCooldown > 0) return false;
        if (distance < 3.5 || distance > 9.0) return false;
        if (entity.getLocation().getY() - target.getLocation().getY() > 3.0) return false;
        if (!retreating && pressureTicks < 20 && !lowHp) return false;
        return random.nextDouble() < (lowHp ? 0.70 : 0.45);
    }

    private void fireHomingIceShot(LivingEntity target) {
        iceShotCooldown = ICE_SHOT_COOLDOWN;
        pressureTicks = Math.max(pressureTicks, 20);

        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.65f);
        entity.getWorld().spawnParticle(Particle.SNOWFLAKE, entity.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.02);

        new HomingIceShotProjectile(entity, entity.getEyeLocation().clone().add(entity.getEyeLocation().getDirection().clone().multiply(0.6)), target)
                .runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }

    private void performCharge(LivingEntity target) {
        chargeCooldown = CHARGE_COOLDOWN;
        pressureTicks = Math.max(pressureTicks, 30);

        Vector dir = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        entity.setVelocity(dir.multiply(1.55).setY(0.12));
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.9f);
        entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation().add(0, 1.0, 0), 20, 0.3, 0.2, 0.3, 0.08);

        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            if (!entity.isValid() || entity.isDead()) return;
            Location impact = entity.getLocation();
            impact.getWorld().spawnParticle(Particle.BLOCK, impact, 20, 0.4, 0.2, 0.4, 0.05, Material.PACKED_ICE.createBlockData());
            impact.getWorld().playSound(impact, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
            applyAreaDamage(impact, 2.8, 22.0, DeepwitherDamageEvent.DamageType.PHYSICAL, false);
        }, 8L);
    }

    private void performPounce(LivingEntity target) {
        pounceCooldown = POUNCE_COOLDOWN;
        pressureTicks = Math.max(pressureTicks, 35);

        Vector dir = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        entity.setVelocity(dir.multiply(1.15).setY(0.58));
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.7f);
        entity.getWorld().spawnParticle(Particle.SNOWFLAKE, entity.getLocation().add(0, 0.8, 0), 18, 0.4, 0.2, 0.4, 0.05);

        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            if (!entity.isValid() || entity.isDead()) return;
            Location impact = entity.getLocation();
            impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.1f);
            impact.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1);
            impact.getWorld().spawnParticle(Particle.SNOWFLAKE, impact, 40, 1.2, 0.8, 1.2, 0.06);
            applyAreaDamage(impact, 3.5, 28.0, DeepwitherDamageEvent.DamageType.PHYSICAL, true);
        }, 12L);
    }

    private void applyAreaDamage(Location center, double radius, double damage, DeepwitherDamageEvent.DamageType type, boolean slowTargeting) {
        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(entity)) continue;

            DamageContext ctx = new DamageContext(entity, living, type, damage);
            Deepwither.getInstance().getDamageProcessor().process(ctx);

            if (slowTargeting && living instanceof Player player) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, true, true));
            }

            Vector knockback = living.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.45);
            knockback.setY(0.18);
            living.setVelocity(knockback);
        }
    }

    private LivingEntity resolveTarget() {
        if (entity instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (isValidTarget(target)) {
                return target;
            }
        }

        LivingEntity nearest = entity.getWorld().getNearbyEntities(entity.getLocation(), 24.0, 12.0, 24.0).stream()
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
        if (targetMotion.lengthSquared() < 0.0001) {
            return false;
        }
        return targetMotion.normalize().dot(toTarget) > 0.15;
    }

    private void spawnAmbientParticles() {
        if (getTicksLived() % 4 != 0) return;

        Location center = entity.getLocation().add(0, 1.0, 0);
        entity.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 4, 0.35, 0.5, 0.35, 0.03);
        entity.getWorld().spawnParticle(Particle.CLOUD, center, 3, 0.3, 0.4, 0.3, 0.02);
    }

    private final class HomingIceShotProjectile extends SkillProjectile {
        private final UUID targetId;
        private int localTicks;

        private HomingIceShotProjectile(LivingEntity caster, Location spawnLoc, LivingEntity target) {
            super(caster, spawnLoc, spawnLoc.getDirection());
            this.targetId = target.getUniqueId();
            this.speed = 0.85;
            this.hitboxRadius = 0.85;
            this.maxTicks = 90;
        }

        @Override
        public void onTick() {
            LivingEntity target = resolveProjectileTarget();
            if (target == null) {
                cancel();
                return;
            }

            Vector desired = target.getEyeLocation().toVector().subtract(currentLocation.toVector());
            if (desired.lengthSquared() > 0.0001) {
                desired.normalize();
                direction = direction.multiply(0.78).add(desired.multiply(0.22)).normalize();
            }

            currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 8, 0.08, 0.08, 0.08, 0.0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(111, 214, 255), 1.6f));
            currentLocation.getWorld().spawnParticle(Particle.SNOWFLAKE, currentLocation, 2, 0.08, 0.08, 0.08, 0.0);

            if (localTicks % 6 == 0) {
                currentLocation.getWorld().playSound(currentLocation, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.45f, 1.35f);
            }
            localTicks++;
        }

        @Override
        public void onHitEntity(LivingEntity target) {
            currentLocation.getWorld().playSound(currentLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
            currentLocation.getWorld().spawnParticle(Particle.SNOWFLAKE, currentLocation, 30, 0.2, 0.2, 0.2, 0.04);
            currentLocation.getWorld().spawnParticle(Particle.BLOCK, currentLocation, 14, 0.2, 0.2, 0.2, 0.05, Material.ICE.createBlockData());

            DamageContext ctx = new DamageContext(entity, target, DeepwitherDamageEvent.DamageType.MAGIC, 14.0);
            Deepwither.getInstance().getDamageProcessor().process(ctx);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, true, true, true));
            target.setNoDamageTicks(0);

            splash(currentLocation, 2.5, 6.0);
            cancel();
        }

        @Override
        public void onHitBlock(Block block) {
            currentLocation.getWorld().playSound(currentLocation, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
            currentLocation.getWorld().spawnParticle(Particle.SNOWFLAKE, currentLocation, 30, 0.2, 0.2, 0.2, 0.04);
            splash(currentLocation, 2.0, 6.0);
            cancel();
        }

        private LivingEntity resolveProjectileTarget() {
            Entity found = Bukkit.getEntity(targetId);
            if (found instanceof LivingEntity living && isValidTarget(living)) {
                return living;
            }
            return null;
        }

        private void splash(Location center, double radius, double damage) {
            List<Entity> nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius).stream().toList();
            for (Entity nearbyEntity : nearby) {
                if (!(nearbyEntity instanceof LivingEntity living) || living.equals(entity)) continue;

                DamageContext ctx = new DamageContext(entity, living, DeepwitherDamageEvent.DamageType.MAGIC, damage);
                Deepwither.getInstance().getDamageProcessor().process(ctx);
            }
        }
    }
}
