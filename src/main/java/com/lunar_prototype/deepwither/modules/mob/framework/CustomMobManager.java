package com.lunar_prototype.deepwither.modules.mob.framework;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * カスタムモブのライフサイクルとイベント配送を管理するクラス
 */
public class CustomMobManager implements IManager, Listener {

    private final Deepwither plugin;
    private final Map<String, Class<? extends CustomMob>> mobRegistry = new HashMap<>();
    private final Map<UUID, CustomMob> activeMobs = new ConcurrentHashMap<>();
    private final NamespacedKey mobIdKey;

    public CustomMobManager(Deepwither plugin) {
        this.plugin = plugin;
        this.mobIdKey = new NamespacedKey(plugin, "custom_mob_id");
    }

    @Override
    public void init() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTickTask();
    }

    @Override
    public void shutdown() {
        activeMobs.clear();
    }

    /**
     * 新しいカスタムモブの種類を登録します
     */
    public void registerMob(String id, Class<? extends CustomMob> clazz) {
        mobRegistry.put(id, clazz);
    }

    /**
     * カスタムモブをスポーンさせます
     */
    public CustomMob spawnMob(String id, Location loc) {
        return spawnMob(id, loc, EntityType.ZOMBIE); // デフォルト
    }

    /**
     * カスタムモブを特定のエンティティタイプでスポーンさせます
     */
    public CustomMob spawnMob(String id, Location loc, EntityType type) {
        Class<? extends CustomMob> clazz = mobRegistry.get(id);
        if (clazz == null) return null;

        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        
        try {
            CustomMob mobLogic = clazz.getDeclaredConstructor().newInstance();
            entity.getPersistentDataContainer().set(mobIdKey, PersistentDataType.STRING, id);
            
            mobLogic.init(entity);
            activeMobs.put(entity.getUniqueId(), mobLogic);
            return mobLogic;
        } catch (Exception e) {
            e.printStackTrace();
            entity.remove();
            return null;
        }
    }

    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                activeMobs.values().forEach(CustomMob::tick);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // --- イベントディスパッチ ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeepwitherDamage(DeepwitherDamageEvent e) {
        // 攻撃側がカスタムモブか確認
        if (e.getAttacker() != null) {
            CustomMob attackerMob = activeMobs.get(e.getAttacker().getUniqueId());
            if (attackerMob != null) {
                attackerMob.onAttack(e.getVictim(), e);
            }
        }

        // 防御側がカスタムモブか確認
        CustomMob victimMob = activeMobs.get(e.getVictim().getUniqueId());
        if (victimMob != null) {
            victimMob.onDamaged(e.getAttacker() instanceof LivingEntity le ? le : null, e);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        CustomMob mob = activeMobs.remove(e.getEntity().getUniqueId());
        if (mob != null) {
            mob.onDeath();
        }
    }

    /**
     * 特定のエンティティが管理対象か確認し、必要なら紐付けを復元する
     * (サーバー再起動後の対応などは今後の課題)
     */
    public CustomMob getCustomMob(Entity entity) {
        return activeMobs.get(entity.getUniqueId());
    }
}
