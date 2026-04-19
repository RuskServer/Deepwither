package com.lunar_prototype.deepwither.modules.mob.service;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class MobLevelService implements IManager, Listener {

    private final JavaPlugin plugin;
    private boolean enabled;
    private double healthMult;
    private double damageMult;
    private String nameFormat;

    private final NamespacedKey LEVEL_KEY;
    private final NamespacedKey BASENAME_KEY;

    public MobLevelService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.LEVEL_KEY = new NamespacedKey(plugin, "mob_level");
        this.BASENAME_KEY = new NamespacedKey(plugin, "mob_basename");
    }

    @Override
    public void init() {
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("leveling.enabled", true);
        this.healthMult = config.getDouble("leveling.health_multiplier", 0.05);
        this.damageMult = config.getDouble("leveling.damage_multiplier", 0.02);
        this.nameFormat = config.getString("leveling.name_format", "&8[Lv.{level}] &r{name} &c{hp}❤");
    }

    public void applyLevel(LivingEntity entity, String mobName, int level) {
        if (!enabled) return;

        entity.getPersistentDataContainer().set(LEVEL_KEY, PersistentDataType.INTEGER, level);
        entity.getPersistentDataContainer().set(BASENAME_KEY, PersistentDataType.STRING, mobName);

        StatManager sm = (StatManager) com.lunar_prototype.deepwither.Deepwither.getInstance().getStatManager();
        
        // すでに仮想体力が設定されている場合（CustomMob.onSpawn等）はそれをベースにし、
        // そうでなければバニラの属性値をベースにする
        double baseHealth = sm.getMobMaxHealth(entity);
        double newMaxHealth = baseHealth * (1.0 + (level * healthMult));
        
        // 仮想体力のセット
        sm.setMobMaxHealth(entity, newMaxHealth);
        sm.setMobHealth(entity, newMaxHealth);

        // 攻撃力のスケーリング（バニラ属性値をベースにするが、適用は属性値に対して行う）
        AttributeInstance attackAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackAttr != null) {
            double baseDamage = attackAttr.getBaseValue();
            double newDamage = baseDamage * (1.0 + (level * damageMult));
            attackAttr.setBaseValue(newDamage);
        }

        updateMobName(entity, mobName, level);
    }

    public void updateMobName(LivingEntity entity) {
        if (!entity.getPersistentDataContainer().has(LEVEL_KEY, PersistentDataType.INTEGER)) return;
        Integer levelObj = entity.getPersistentDataContainer().get(LEVEL_KEY, PersistentDataType.INTEGER);
        if (levelObj == null) return;
        int level = levelObj;

        String baseName = entity.getPersistentDataContainer().get(BASENAME_KEY, PersistentDataType.STRING);
        if (baseName == null) {
            ActiveMob am = MythicBukkit.inst().getMobManager().getMythicMobInstance(entity);
            baseName = (am != null && am.getType().getDisplayName().isPresent()) ? am.getType().getDisplayName().get() : entity.getName();
        }

        updateMobName(entity, baseName, level);
    }

    private void updateMobName(LivingEntity entity, String baseName, int level) {
        IStatManager statManager = Deepwither.getInstance().getStatManager();
        int currentHp = (int) statManager.getMobHealth(entity);
        int maxHp = (int) statManager.getMobMaxHealth(entity);

        String displayName = nameFormat
                .replace("{level}", String.valueOf(level))
                .replace("{name}", baseName)
                .replace("{hp}", String.valueOf(currentHp))
                .replace("{max_hp}", String.valueOf(maxHp));

        entity.customName(LegacyComponentSerializer.legacyAmpersand().deserialize(displayName));
        entity.setCustomNameVisible(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof LivingEntity living && enabled) {
            plugin.getServer().getScheduler().runTask(plugin, () -> updateMobName(living));
        }
    }

    @EventHandler
    public void onRegen(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof LivingEntity living && enabled) {
            updateMobName(living);
        }
    }
}
