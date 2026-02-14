package com.lunar_prototype.deepwither;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;

public class CustomDamageMechanics implements ITargetedEntitySkill {
    protected final double basePower;
    protected final double multiplier;
    protected final String type;
    protected final List<String> tags;
    protected final boolean canCrit;
    protected final boolean isProjectile;

    public CustomDamageMechanics(MythicLineConfig config) {
        this.basePower = config.getDouble(new String[]{"damage", "d"}, 10.0);
        this.multiplier = config.getDouble(new String[]{"multiplier", "m"}, 1.0);
        this.type = config.getString(new String[]{"type", "t"}, "PHYSICAL").toUpperCase();
        this.tags = Arrays.asList(config.getString(new String[]{"tags", "tg"}, "").split(","));
        this.canCrit = config.getBoolean(new String[]{"canCrit", "crit"}, true);
        this.isProjectile = config.getBoolean(new String[]{"projectile", "p"}, false);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        LivingEntity caster = (LivingEntity) data.getCaster().getEntity().getBukkitEntity();
        if (!(BukkitAdapter.adapt(target) instanceof LivingEntity bukkitTarget)) return SkillResult.INVALID_TARGET;

        Deepwither plugin = Deepwither.getInstance();
        DamageManager damageManager = plugin.getDamageManager();

        if (damageManager.isInvulnerable(bukkitTarget)) return SkillResult.CONDITION_FAILED;

        double baseDamage = 0;
        double finalDamage = 0;
        boolean isMagic = type.equals("MAGIC");

        if (caster instanceof Player player) {
            StatMap attackerStats = StatManager.getTotalStatsFromEquipment(player);

            if (isMagic) {
                baseDamage = (basePower + attackerStats.getFinal(StatType.MAGIC_DAMAGE)) * multiplier;
                if (tags.contains("BURST")) baseDamage = (baseDamage * 0.4) + attackerStats.getFinal(StatType.MAGIC_BURST_DAMAGE);
                if (tags.contains("AOE")) baseDamage = (baseDamage * 0.6) + attackerStats.getFinal(StatType.MAGIC_AOE_DAMAGE);
            } else {
                StatType weaponType = damageManager.getWeaponStatType(player.getInventory().getItemInMainHand());
                double weaponFlat = (weaponType != null) ? attackerStats.getFlat(weaponType) : 0;
                double weaponPercent = (weaponType != null) ? attackerStats.getPercent(weaponType) : 0;

                if (isProjectile) {
                    double distMult = damageManager.calculateDistanceMultiplier(player, bukkitTarget);
                    baseDamage = (basePower + attackerStats.getFlat(StatType.PROJECTILE_DAMAGE) + weaponFlat);
                    double mult = multiplier * (1.0 + (attackerStats.getPercent(StatType.PROJECTILE_DAMAGE) + weaponPercent) / 100.0);
                    baseDamage *= mult * distMult;
                } else {
                    baseDamage = (basePower + attackerStats.getFlat(StatType.ATTACK_DAMAGE) + weaponFlat);
                    double mult = multiplier * (1.0 + (attackerStats.getPercent(StatType.ATTACK_DAMAGE) + weaponPercent) / 100.0);
                    baseDamage *= mult;
                }
            }

            if (canCrit && damageManager.rollChance(attackerStats.getFinal(StatType.CRIT_CHANCE))) {
                baseDamage *= (attackerStats.getFinal(StatType.CRIT_DAMAGE) / 100.0);
                playCriticalEffect(bukkitTarget);
                damageManager.sendLog(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Component.text("クリティカル！", NamedTextColor.GOLD, TextDecoration.BOLD));
            }
        } else {
            baseDamage = basePower * multiplier;
            if (bukkitTarget instanceof Player pTarget) {
                baseDamage = damageManager.applyMobCritLogic(caster, baseDamage, pTarget);
            }
        }

        if (bukkitTarget instanceof Player playerTarget) {
            StatMap defenderStats = StatManager.getTotalStatsFromEquipment(playerTarget);
            finalDamage = damageManager.applyDefense(baseDamage,
                    isMagic ? defenderStats.getFinal(StatType.MAGIC_RESIST) : defenderStats.getFinal(StatType.DEFENSE),
                    isMagic ? 100.0 : 500.0);

            if (playerTarget.isBlocking() && !isMagic) {
                Vector toAttacker = caster.getLocation().toVector().subtract(playerTarget.getLocation().toVector()).normalize();
                if (toAttacker.dot(playerTarget.getLocation().getDirection().normalize()) > 0.5) {
                    double blockRate = Math.max(0.0, Math.min(defenderStats.getFinal(StatType.SHIELD_BLOCK_RATE), 1.0));
                    double blocked = finalDamage * blockRate;
                    finalDamage -= blocked;
                    playerTarget.getWorld().playSound(playerTarget.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                    damageManager.sendLog(playerTarget, PlayerSettingsManager.SettingType.SHOW_MITIGATION, 
                            Component.text("盾防御！ ", NamedTextColor.AQUA)
                                    .append(Component.text("軽減: ", NamedTextColor.GRAY))
                                    .append(Component.text(Math.round(blocked), NamedTextColor.GREEN)));
                }
            }
        } else {
            StatMap defenderStats = damageManager.getDefenderStats(bukkitTarget);
            finalDamage = damageManager.applyDefense(baseDamage,
                    isMagic ? defenderStats.getFinal(StatType.MAGIC_RESIST) : defenderStats.getFinal(StatType.DEFENSE),
                    isMagic ? 100.0 : 100.0);
        }

        if (tags.contains("UNDEAD") && caster instanceof Player p) {
            if (damageManager.handleUndeadDamage(p, bukkitTarget)) return SkillResult.SUCCESS;
        }
        if (tags.contains("LIFESTEAL") && caster instanceof Player p) {
            damageManager.handleLifesteal(p, bukkitTarget, finalDamage);
        }

        finalDamage = Math.max(0.1, finalDamage);
        
        com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent.DamageType damageType;
        if (isMagic) damageType = com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent.DamageType.MAGIC;
        else if (isProjectile) damageType = com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent.DamageType.PROJECTILE;
        else damageType = com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent.DamageType.PHYSICAL;
        
        damageManager.finalizeDamage(bukkitTarget, finalDamage, caster, damageType);

        return SkillResult.SUCCESS;
    }

    private void playCriticalEffect(LivingEntity target) {
        Location hitLoc = target.getLocation().add(0, 1.2, 0);
        World world = hitLoc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.FLASH, hitLoc, 1, 0, 0, 0, 0, Color.WHITE);
        world.spawnParticle(Particle.SONIC_BOOM, hitLoc, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.LAVA, hitLoc, 8, 0.4, 0.4, 0.4, 0.1);
        world.spawnParticle(Particle.CRIT, hitLoc, 30, 0.5, 0.5, 0.5, 0.5);
        world.spawnParticle(Particle.LARGE_SMOKE, hitLoc, 15, 0.2, 0.2, 0.2, 0.05);

        world.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
        world.playSound(hitLoc, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.5f);
        world.playSound(hitLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.6f);
        world.playSound(hitLoc, Sound.ITEM_TRIDENT_HIT, 1.0f, 0.7f);
    }
}
