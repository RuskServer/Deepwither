package com.lunar_prototype.deepwither;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.ChatColor;
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

public class MobLevelManager implements Listener {

    private final JavaPlugin plugin;
    private boolean enabled;
    private double healthMult;
    private double damageMult;
    private String nameFormat;

    // PDC用のキー (レベル保存用)
    private final org.bukkit.NamespacedKey LEVEL_KEY;

    public MobLevelManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.LEVEL_KEY = new org.bukkit.NamespacedKey(plugin, "mob_level");
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("leveling.enabled", true);
        this.healthMult = config.getDouble("leveling.health_multiplier", 0.05);
        this.damageMult = config.getDouble("leveling.damage_multiplier", 0.02);
        this.nameFormat = config.getString("leveling.name_format", "&8[Lv.{level}] &r{name} &c{hp}♥");
    }

    /**
     * モブにレベルを適用するメインメソッド
     * @param entity 対象のエンティティ
     * @param mobName モブの元の名前 (MythicMobsのDisplayNameなど)
     * @param level 適用するレベル
     */
    public void applyLevel(LivingEntity entity, String mobName, int level) {
        if (!enabled) return;

        // 1. レベルをPDCに保存 (後で参照するため)
        entity.getPersistentDataContainer().set(LEVEL_KEY, PersistentDataType.INTEGER, level);

        // 2. ステータス強化 (BaseValueに対して倍率をかける)
        // 体力
        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double baseHealth = maxHealthAttr.getBaseValue();
            double newMaxHealth = baseHealth * (1.0 + (level * healthMult));
            maxHealthAttr.setBaseValue(newMaxHealth);
            entity.setHealth(newMaxHealth); // HPを全回復
        }

        // 攻撃力
        AttributeInstance attackAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackAttr != null) {
            double baseDamage = attackAttr.getBaseValue();
            double newDamage = baseDamage * (1.0 + (level * damageMult));
            attackAttr.setBaseValue(newDamage);
        }

        // 3. 名前の更新
        updateMobName(entity, mobName, level);
    }

    /**
     * 名前とHP表示を更新する
     */
    public void updateMobName(LivingEntity entity) {
        if (!entity.getPersistentDataContainer().has(LEVEL_KEY, PersistentDataType.INTEGER)) return;
        int level = entity.getPersistentDataContainer().get(LEVEL_KEY, PersistentDataType.INTEGER);

        // MythicMobsのインスタンスから元の名前を取得できればベストですが、
        // 簡易的に現在のカスタムネームからパースするか、別途保存が必要です。
        // ここではMythicMobsAPIを使って元の表示名を取得する例とします。
        ActiveMob am = MythicBukkit.inst().getMobManager().getMythicMobInstance(entity);
        String baseName = (am != null) ? am.getType().getDisplayName().get() : entity.getName();

        updateMobName(entity, baseName, level);
    }

    private void updateMobName(LivingEntity entity, String baseName, int level) {
        int currentHp = (int) entity.getHealth();
        int maxHp = (int) entity.getAttribute(Attribute.MAX_HEALTH).getValue();

        String displayName = nameFormat
                .replace("{level}", String.valueOf(level))
                .replace("{name}", baseName)
                .replace("{hp}", String.valueOf(currentHp))
                .replace("{max_hp}", String.valueOf(maxHp));

        entity.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
        entity.setCustomNameVisible(true);
    }

    // --- イベントリスナー (HP変動時に名前更新) ---

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof LivingEntity living && enabled) {
            // ダメージ適用後に名前を更新するために1tick遅延させる
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