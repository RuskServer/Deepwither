package com.lunar_prototype.deepwither.core.damage;

import com.lunar_prototype.deepwither.StatType;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * ダメージ計算のコンテキストを保持するクラス
 */
public class DamageContext {
    private final LivingEntity attacker;
    private final LivingEntity victim;
    private DeepwitherDamageEvent.DamageType damageType;
    
    private double baseDamage;
    private double finalDamage;
    
    private boolean isCrit = false;
    private boolean isProjectile = false;
    private double distanceMultiplier = 1.0;
    private double defenseBypassPercent = 0.0;
    private boolean isTrueDamage = false;

    private final Set<String> tags = new HashSet<>();
    private final java.util.Map<String, Object> metadata = new java.util.HashMap<>();
    private StatType weaponStatType;
    private ItemStack weapon;

    public DamageContext(LivingEntity attacker, LivingEntity victim, DeepwitherDamageEvent.DamageType damageType, double baseDamage) {
        this.attacker = attacker;
        this.victim = victim;
        this.damageType = damageType;
        this.baseDamage = baseDamage;
        this.finalDamage = baseDamage;
    }

    // Getters and Setters
    public LivingEntity getAttacker() { return attacker; }
    public Player getAttackerAsPlayer() { return attacker instanceof Player ? (Player) attacker : null; }
    public LivingEntity getVictim() { return victim; }
    public Player getVictimAsPlayer() { return victim instanceof Player ? (Player) victim : null; }
    
    public DeepwitherDamageEvent.DamageType getDamageType() { return damageType; }
    public void setDamageType(DeepwitherDamageEvent.DamageType damageType) { this.damageType = damageType; }

    public double getBaseDamage() { return baseDamage; }
    public void setBaseDamage(double baseDamage) { this.baseDamage = baseDamage; }

    public double getFinalDamage() { return finalDamage; }
    public void setFinalDamage(double finalDamage) { this.finalDamage = finalDamage; }

    public boolean isCrit() { return isCrit; }
    public void setCrit(boolean crit) { isCrit = crit; }

    public boolean isProjectile() { return isProjectile; }
    public void setProjectile(boolean projectile) { isProjectile = projectile; }

    public double getDistanceMultiplier() { return distanceMultiplier; }
    public void setDistanceMultiplier(double distanceMultiplier) { this.distanceMultiplier = distanceMultiplier; }

    public double getDefenseBypassPercent() { return defenseBypassPercent; }
    public void setDefenseBypassPercent(double defenseBypassPercent) {
        this.defenseBypassPercent = Math.max(0.0, Math.min(100.0, defenseBypassPercent));
    }

    public boolean isTrueDamage() { return isTrueDamage; }
    public void setTrueDamage(boolean trueDamage) { isTrueDamage = trueDamage; }

    public Set<String> getTags() { return tags; }
    public void addTag(String tag) { tags.add(tag); }
    public boolean hasTag(String tag) { return tags.contains(tag); }

    public StatType getWeaponStatType() { return weaponStatType; }
    public void setWeaponStatType(StatType weaponStatType) { this.weaponStatType = weaponStatType; }

    public ItemStack getWeapon() { return weapon; }
    public void setWeapon(ItemStack weapon) { this.weapon = weapon; }

    public void put(String key, Object value) { metadata.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) { return (T) metadata.get(key); }
    
    public boolean isMagic() {
        return damageType == DeepwitherDamageEvent.DamageType.MAGIC;
    }
}
