package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

@DependsOn({})
public class StatManager implements IManager, IStatManager {

    private final Map<UUID, Double> actualCurrentHealth = new HashMap<>();
    private final Map<UUID, StatMap> temporaryBuffs = new HashMap<>();

    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("a3bb7af7-3c5b-4df1-a17e-cdeae1db1d32");
    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("ff5dd7e3-d781-4fee-b3d4-bfe3a5fda85d");

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    @Override
    public PlayerStat of(Player player) {
        return new PlayerStat() {
            @Override
            public double getHP() { return getActualCurrentHealth(player); }
            @Override
            public void setHP(double health) { setActualCurrentHealth(player, health); }
            @Override
            public double getMaxHP() { return getActualMaxHealth(player); }
            @Override
            public void heal(double amount) { StatManager.this.heal(player, amount); }
            @Override
            public StatMap getAll() { return getTotalStats(player); }
            @Override
            public void update() { updatePlayerStats(player); }
        };
    }

    @Override
    public void updatePlayerStats(Player player) {
        StatMap total = getTotalStats(player);
        syncAttackDamage(player, total);
        syncAttributes(player,total);
        syncBukkitHealth(player);
    }

    @Override
    public StatMap getTotalStats(Player player) {
        return getTotalStatsFromEquipment(player);
    }

    @Override
    public double getActualMaxHealth(Player player) {
        StatMap total = getTotalStats(player);
        return total.getFlat(StatType.MAX_HEALTH);
    }

    @Override
    public void applyTemporaryBuff(UUID playerUUID, StatMap buff) {
        temporaryBuffs.put(playerUUID, buff);
    }

    @Override
    public void removeTemporaryBuff(UUID playerUUID) {
        temporaryBuffs.remove(playerUUID);
    }

    public void resetHealthOnEvent(Player player, boolean isLogin) {
        double maxHp = getActualMaxHealth(player);

        if (isLogin) {
            actualCurrentHealth.put(player.getUniqueId(), maxHp);
        } else {
            double respawnHp = Math.max(1.0, maxHp * 0.1);
            actualCurrentHealth.put(player.getUniqueId(), respawnHp);
        }

        syncBukkitHealth(player);
    }

    @Override
    public void naturalRegeneration(Player player, double seconds) {
        double currentHp = getActualCurrentHealth(player);
        double maxHp = getActualMaxHealth(player);

        if (currentHp >= maxHp) return;

        StatMap stats = getTotalStats(player);
        double regenPercent = stats.getFinal(StatType.HP_REGEN) / 100.0;

        double baseRegenPerSecond = maxHp * 0.01 + regenPercent;
        double actualRegenAmount = baseRegenPerSecond * seconds;

        setActualCurrentHealth(player, currentHp + actualRegenAmount);
        player.getWorld().spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0, 2, 0), 1, 0.5, 0.5, 0.5, 0);
    }

    @Override
    public void heal(Player player, double amount) {
        healCustomHealth(player, amount);
    }

    @Deprecated
    public void healCustomHealth(Player player, double amount) {
        double currentHealth = getActualCurrentHealth(player);
        double maxHealth = getActualMaxHealth(player);
        double newHealth = Math.min(currentHealth + amount, maxHealth);
        setActualCurrentHealth(player, newHealth);
    }

    @Override
    public double getActualCurrentHealth(Player player) {
        return actualCurrentHealth.getOrDefault(player.getUniqueId(), getActualMaxHealth(player));
    }

    @Override
    public void setActualCurrentHealth(Player player, double newHealth) {
        double max = getActualMaxHealth(player);
        newHealth = Math.min(newHealth, max);
        newHealth = Math.max(newHealth, 0.0);
        actualCurrentHealth.put(player.getUniqueId(), newHealth);
        syncBukkitHealth(player);
    }

    @Override
    public void setActualCurrenttoMaxHealth(Player player) {
        setActualCurrenttoMaxHelth(player);
    }

    @Deprecated
    @Override
    public void setActualCurrenttoMaxHelth(Player player) {
        double max = getTotalStats(player).getFinal(StatType.MAX_HEALTH);
        actualCurrentHealth.put(player.getUniqueId(), max);
        syncBukkitHealth(player);
    }

    public void syncBukkitHealth(Player player) {
        if (player.isDead()) return;

        double actualMax = getActualMaxHealth(player);
        double actualCurrent = getActualCurrentHealth(player);

        if (actualCurrent > actualMax) {
            actualCurrent = actualMax;
            actualCurrentHealth.put(player.getUniqueId(), actualCurrent);
        }

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null && maxHealthAttr.getValue() != 20.0) {
            AttributeModifier existing = maxHealthAttr.getModifier(new NamespacedKey("minecraft",MAX_HEALTH_MODIFIER_ID.toString()));
            if (existing != null) maxHealthAttr.removeModifier(existing);
            maxHealthAttr.setBaseValue(20.0);
        }

        double ratio = (actualMax > 0) ? actualCurrent / actualMax : 0.0;
        double bukkitHealth = ratio * 20.0;

        if (actualCurrent > 0 && bukkitHealth < 0.5) {
            bukkitHealth = 0.5;
        }

        player.setHealth(Math.max(0.0, bukkitHealth));
    }

    @Deprecated
    public static StatMap getTotalStatsFromEquipment(Player player) {
        StatMap total = new StatMap();
        PlayerLevelData data = Deepwither.getInstance().getLevelManager().get(player);

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (shouldReadStats(mainHand)) {
            total.add(readStatsFromItem(mainHand));
        }

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            total.add(readStatsFromItem(armor));
        }

        List<ItemStack> artifacts = Deepwither.getInstance().getArtifactManager().getPlayerArtifacts(player);
        for (ItemStack artifact : artifacts) {
            total.add(readStatsFromItem(artifact));
        }

        ItemStack backpack = Deepwither.getInstance().getArtifactManager().getPlayerBackpack(player);
        total.add(readStatsFromItem(backpack));

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isOffHandEquipment(offHand)) {
            total.add(readStatsFromItem(offHand));
        }

        PlayerAttributeData attr = Deepwither.getInstance().getAttributeManager().get(player.getUniqueId());
        if (attr != null) {
            for (StatType type : StatType.values()) {
                int points = attr.getAllocated(type);
                switch (type) {
                    case STR -> {
                        double currentPercent = total.getPercent(StatType.ATTACK_DAMAGE);
                        total.setPercent(StatType.ATTACK_DAMAGE, currentPercent + (points * 1.0));
                    }
                    case VIT -> {
                        double hpPercent = total.getPercent(StatType.MAX_HEALTH);
                        total.setPercent(StatType.MAX_HEALTH, hpPercent + (points * 1.0));
                        double defPercent = total.getPercent(StatType.DEFENSE);
                        total.setPercent(StatType.DEFENSE, defPercent + (points * 0.5));
                    }
                    case MND -> {
                        double val = total.getFlat(StatType.CRIT_DAMAGE);
                        total.setFlat(StatType.CRIT_DAMAGE, val + points * 1.5);
                        double pDmgPercent = total.getPercent(StatType.PROJECTILE_DAMAGE);
                        total.setPercent(StatType.PROJECTILE_DAMAGE, pDmgPercent + (points * 1.5));
                    }
                    case INT -> {
                        double cdVal = total.getFlat(StatType.COOLDOWN_REDUCTION);
                        total.setFlat(StatType.COOLDOWN_REDUCTION, cdVal + points * 0.1);
                        double manaPercent = total.getPercent(StatType.MAX_MANA);
                        total.setPercent(StatType.MAX_MANA, manaPercent + (points * 2.0));
                    }
                    case AGI -> {
                        double critChanceVal = total.getFlat(StatType.CRIT_CHANCE);
                        total.setFlat(StatType.CRIT_CHANCE, critChanceVal + points * 0.2);
                        double speedVal = total.getFlat(StatType.MOVE_SPEED);
                        total.setFlat(StatType.MOVE_SPEED, speedVal + points * 0.0025);
                    }
                }
            }
        }

        SkillData skillData = Deepwither.getInstance().getSkilltreeManager().load(player.getUniqueId());
        if (skillData != null) {
            total.add(skillData.getPassiveStats());
        }

        StatManager instance = (StatManager) Deepwither.getInstance().getStatManager();
        StatMap tempBuff = instance.temporaryBuffs.get(player.getUniqueId());
        if (tempBuff != null) {
            total.add(tempBuff);
        }

        double baseHp = 20.0;
        double currentHp = total.getFinal(StatType.MAX_HEALTH);
        double levelhp = 2 * data.getLevel();
        total.setFlat(StatType.MAX_HEALTH, currentHp + baseHp + levelhp);
        
        double baseMana = 100.0;
        double currentMana = total.getFinal(StatType.MAX_MANA);
        total.setFlat(StatType.MAX_MANA, currentMana + baseMana);

        return total;
    }

    public static double getEffectiveCooldown(Player player, double baseCooldown) {
        StatMap stats = Deepwither.getInstance().getStatManager().getTotalStats(player);
        double cooldownReduction = stats.getFinal(StatType.COOLDOWN_REDUCTION);
        return baseCooldown * (1.0 - (cooldownReduction / 100.0));
    }

    @Deprecated
    public static StatMap readStatsFromItem(ItemStack item) {
        StatMap stats = new StatMap();
        if (item == null || !item.hasItemMeta()) return stats;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        for (StatType type : StatType.values()) {
            Double flat = container.get(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE);
            Double percent = container.get(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE);
            if (flat != null) stats.setFlat(type, flat);
            if (percent != null) stats.setPercent(type, percent);
        }
        return stats;
    }

    public static void syncAttackDamage(Player player, StatMap stats) {
        double flat = stats.getFlat(StatType.ATTACK_DAMAGE);
        double percent = stats.getPercent(StatType.ATTACK_DAMAGE);
        double value = flat * (1 + percent / 100.0);

        AttributeInstance attr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attr == null) return;

        AttributeModifier existing = attr.getModifier(new NamespacedKey("minecraft",ATTACK_DAMAGE_MODIFIER_ID.toString()));
        if (existing != null) attr.removeModifier(existing);

        if (value == 0) return;

        AttributeModifier modifier = new AttributeModifier(
                new NamespacedKey("minecraft",ATTACK_DAMAGE_MODIFIER_ID.toString()),
                value,
                AttributeModifier.Operation.ADD_NUMBER
        );
        attr.addModifier(modifier);
    }

    public static void syncAttributes(Player player, StatMap stats) {
        syncAttribute(player, Attribute.ARMOR, stats.getFinal(StatType.DEFENSE));
        if (stats.getFinal(StatType.ATTACK_SPEED) > 0.1){
            double modifierValue = stats.getFinal(StatType.ATTACK_SPEED) - 4.0;
            syncAttribute(player,Attribute.ATTACK_SPEED,modifierValue);
        }

        syncAttribute(player,Attribute.ENTITY_INTERACTION_RANGE,stats.getFinal(StatType.REACH));

        double speedBonus = stats.getFinal(StatType.MOVE_SPEED);

        if (speedBonus < 0) {
            double resistance = stats.getFinal(StatType.REDUCES_MOVEMENT_SPEED_DECREASE);
            if (resistance > 0) {
                double reductionFactor = Math.min(100.0, resistance) / 100.0;
                speedBonus = speedBonus * (1.0 - reductionFactor);
            }
        }
        syncAttribute(player, Attribute.MOVEMENT_SPEED, speedBonus);
    }

    private static void syncAttribute(Player player, Attribute attrType,double value) {
        AttributeInstance attr = player.getAttribute(attrType);
        if (attr == null) return;

        NamespacedKey att_key = new NamespacedKey(Deepwither.getInstance(),"RPG");
        NamespacedKey baseAttackSpeed = NamespacedKey.minecraft("base_attack_speed");
        attr.removeModifier(baseAttackSpeed);

        for (AttributeModifier mod : new HashSet<>(attr.getModifiers())) {
            if (mod.getKey().equals(att_key)) {
                attr.removeModifier(mod);
            }
        }

        if (value == 0) return;

        AttributeModifier modifier = new AttributeModifier(att_key,value, AttributeModifier.Operation.ADD_NUMBER);
        attr.addModifier(modifier);
    }

    private static boolean isOffHandEquipment(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            List<Component> lore = meta.lore();
            if (lore != null) {
                for (Component line : lore) {
                    String strippedLine = PlainTextComponentSerializer.plainText().serialize(line);
                    if (strippedLine.contains("カテゴリ:オフハンド装備")) return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldReadStats(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return true;

        if (meta instanceof Damageable damageable) {
            if (damageable.hasMaxDamage()) {
                int maxDurability = damageable.getMaxDamage();
                if (maxDurability > 0) {
                    int currentDamage = damageable.getDamage();
                    if (maxDurability - currentDamage <= 1) return false;
                }
            } else {
                int vanillaMax = item.getType().getMaxDurability();
                if (vanillaMax > 0) {
                    int currentDamage = damageable.getDamage();
                    if (vanillaMax - currentDamage <= 1) return false;
                }
            }
        }
        return true;
    }
}