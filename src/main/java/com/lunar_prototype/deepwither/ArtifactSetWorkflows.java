package com.lunar_prototype.deepwither;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public final class ArtifactSetWorkflows {
    private ArtifactSetWorkflows() {}

    private static NamespacedKey cooldownKey(String key) {
        return new NamespacedKey(Deepwither.getInstance(), "artifact_set_cd_" + key);
    }

    public static boolean tryUseCooldown(Player player, String key, long cooldownMillis) {
        if (player == null || key == null || key.isBlank()) {
            return false;
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey cooldownKey = cooldownKey(key);
        long now = System.currentTimeMillis();
        long nextReady = pdc.getOrDefault(cooldownKey, PersistentDataType.LONG, 0L);
        if (now < nextReady) {
            return false;
        }

        pdc.set(cooldownKey, PersistentDataType.LONG, now + Math.max(0L, cooldownMillis));
        return true;
    }

    public static boolean tryRollChance(double chancePercent) {
        return Math.random() * 100.0 < chancePercent;
    }

    public static void grantMana(Player player, double amount) {
        if (player == null || amount <= 0) {
            return;
        }
        Deepwither.getInstance().getManaManager().get(player.getUniqueId()).changeMana(amount);
    }

    public static void grantSpeedBoost(Player player, int ticks, double amount) {
        if (player == null || ticks <= 0 || amount <= 0) {
            return;
        }

        AttributeInstance attr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }

        NamespacedKey key = new NamespacedKey(Deepwither.getInstance(), "artifact_speed_" + UUID.nameUUIDFromBytes((player.getUniqueId() + ":" + amount).getBytes()));
        AttributeModifier existing = attr.getModifier(key);
        if (existing != null) {
            attr.removeModifier(existing);
        }

        AttributeModifier modifier = new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR);
        attr.addModifier(modifier);
        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            if (player.isValid() && player.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                AttributeInstance current = player.getAttribute(Attribute.MOVEMENT_SPEED);
                AttributeModifier live = current.getModifier(key);
                if (live != null) {
                    current.removeModifier(live);
                }
            }
        }, ticks);
    }

    public static void grantOncePerCooldownRegeneration(Player player, String key, long cooldownMillis, int effectTicks, int amplifier) {
        if (player == null || effectTicks <= 0) {
            return;
        }

        if (!tryUseCooldown(player, key, cooldownMillis)) {
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, effectTicks, Math.max(0, amplifier), true, false, true));
    }

    public static void knockBackNearbyEnemies(Player source, double radius, double strength) {
        if (source == null) {
            return;
        }

        for (Entity entity : source.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.equals(source)) {
                continue;
            }
            var direction = living.getLocation().toVector().subtract(source.getLocation().toVector());
            if (direction.lengthSquared() == 0) {
                direction = source.getLocation().getDirection().clone().multiply(-1);
            }
            direction.normalize().multiply(strength).setY(0.35);
            living.setVelocity(direction);
        }
    }

    public static ItemFactory.ArtifactSetWorkflow magicBarrierFullBlock(Component message) {
        return context -> {
            if (!context.isMagicDamage()) {
                return;
            }

            context.cancelDamage();
            context.setDamage(0.0);
            context.playSound(Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.1f);
            context.spawnParticle(Particle.ENCHANT, 32, 0.6, 1.0, 0.6, 0.15);
            if (message != null) {
                context.sendMessage(message);
            }
        };
    }

    public static ItemFactory.ArtifactSetWorkflow magicBarrierWithAbsorption(double absorptionAmount, Component message) {
        return context -> {
            if (!context.isMagicDamage()) {
                return;
            }

            context.cancelDamage();
            context.setDamage(0.0);
            if (absorptionAmount > 0) {
                context.setAbsorptionAmount((float) Math.max(context.getAbsorptionAmount(), absorptionAmount));
            }
            context.playSound(Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.1f);
            context.spawnParticle(Particle.ENCHANT, 24, 0.5, 0.8, 0.5, 0.12);
            context.spawnParticle(Particle.WAX_ON, 12, 0.35, 0.6, 0.35, 0.05);
            if (message != null) {
                context.sendMessage(message);
            }
        };
    }

    public static ItemFactory.ArtifactSetWorkflow reduceDamageOnTrigger(double reductionPercent, Component message) {
        return context -> {
            if (reductionPercent <= 0) {
                return;
            }

            double reduced = context.getDamage() * (1.0 - reductionPercent);
            context.setDamage(Math.max(0.0, reduced));
            context.playSound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
            context.spawnParticle(Particle.CRIT, 16, 0.4, 0.8, 0.4, 0.08);
            if (message != null) {
                context.sendMessage(message);
            }
        };
    }

    public static ItemFactory.ArtifactSetWorkflow magicBarrierFullBlockWithAoe(Component message) {
        return context -> {
            if (!context.isMagicDamage()) {
                return;
            }

            context.cancelDamage();
            context.setDamage(0.0);
            context.playSound(Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.1f);
            context.spawnParticle(Particle.ENCHANT, 32, 0.6, 1.0, 0.6, 0.15);
            knockBackNearbyEnemies(context.getPlayer(), 3.0, 0.9);
            for (Entity entity : context.getPlayer().getNearbyEntities(3, 3, 3)) {
                if (entity instanceof LivingEntity living && !living.equals(context.getPlayer())) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 10, 0, true, false, true));
                }
            }
            if (message != null) {
                context.sendMessage(message);
            }
        };
    }
}
