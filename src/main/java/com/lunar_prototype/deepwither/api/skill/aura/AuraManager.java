package com.lunar_prototype.deepwither.api.skill.aura;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * エンティティごとの動的なバフ・デバフ（オーラ）を管理するマネージャー。
 * 持続時間だけでなく、任意のメタデータ（ダメージ量、攻撃者など）を保持できます。
 */
public class AuraManager implements IManager {
    
    /**
     * オーラのインスタンス情報を保持する内部クラス。
     */
    public static class AuraInstance {
        private final String id;
        private final long endTime;
        private final Map<String, Object> metadata;

        public AuraInstance(String id, long endTime, Map<String, Object> metadata) {
            this.id = id;
            this.endTime = endTime;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }

        public String getId() { return id; }
        public long getEndTime() { return endTime; }
        @SuppressWarnings("unchecked")
        public <T> T getMetadata(String key) { return (T) metadata.get(key); }
        public Map<String, Object> getAllMetadata() { return metadata; }
        public boolean isExpired() { return System.currentTimeMillis() > endTime; }
    }

    private final Map<UUID, Map<String, AuraInstance>> activeAuras = new ConcurrentHashMap<>();

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        activeAuras.clear();
    }

    /**
     * オーラを付与します。
     */
    public void addAura(LivingEntity entity, String auraId, int durationTicks, Map<String, Object> metadata) {
        long endTime = System.currentTimeMillis() + (durationTicks * 50L);
        AuraInstance instance = new AuraInstance(auraId, endTime, metadata);
        activeAuras.computeIfAbsent(entity.getUniqueId(), k -> new ConcurrentHashMap<>())
                   .put(auraId, instance);
    }

    /**
     * 互換用: メタデータなしでオーラを付与します。
     */
    public void addAura(Player player, String auraId, int durationTicks) {
        addAura((LivingEntity) player, auraId, durationTicks, null);
    }

    /**
     * オーラを削除します。
     */
    public void removeAura(LivingEntity entity, String auraId) {
        Map<String, AuraInstance> entityAuras = activeAuras.get(entity.getUniqueId());
        if (entityAuras != null) {
            entityAuras.remove(auraId);
        }
    }

    /**
     * 互換用: オーラを削除します。
     */
    public void removeAura(Player player, String auraId) {
        removeAura((LivingEntity) player, auraId);
    }

    /**
     * 特定のオーラインスタンスを取得します。
     */
    public AuraInstance getAuraInstance(LivingEntity entity, String auraId) {
        Map<String, AuraInstance> entityAuras = activeAuras.get(entity.getUniqueId());
        if (entityAuras == null) return null;

        AuraInstance instance = entityAuras.get(auraId);
        if (instance == null) return null;

        if (instance.isExpired()) {
            entityAuras.remove(auraId);
            return null;
        }
        return instance;
    }

    /**
     * 互換用: 特定のオーラインスタンスを取得します。
     */
    public AuraInstance getAuraInstance(Player player, String auraId) {
        return getAuraInstance((LivingEntity) player, auraId);
    }

    /**
     * 特定のオーラを持っているかチェックします。
     */
    public boolean hasAura(LivingEntity entity, String auraId) {
        Map<String, AuraInstance> entityAuras = activeAuras.get(entity.getUniqueId());
        if (entityAuras == null) return false;
        
        AuraInstance instance = entityAuras.get(auraId);
        if (instance == null) return false;
        
        if (instance.isExpired()) {
            entityAuras.remove(auraId);
            return false;
        }
        return true;
    }

    /**
     * 互換用: 特定のオーラを持っているかチェックします。
     */
    public boolean hasAura(Player player, String auraId) {
        return hasAura((LivingEntity) player, auraId);
    }

    /**
     * 全オーラを解除します。
     */
    public void clearAuras(LivingEntity entity) {
        activeAuras.remove(entity.getUniqueId());
    }

    /**
     * 互換用: 全オーラを解除します。
     */
    public void clearAuras(Player player) {
        clearAuras((LivingEntity) player);
    }

    /**
     * 毎秒（20tick）呼び出される更新処理。継続ダメージ（DOT）などのロジックを処理します。
     */
    public void tick() {
        Iterator<Map.Entry<UUID, Map<String, AuraInstance>>> topIterator = activeAuras.entrySet().iterator();
        
        while (topIterator.hasNext()) {
            Map.Entry<UUID, Map<String, AuraInstance>> entry = topIterator.next();
            UUID entityId = entry.getKey();
            LivingEntity entity = (LivingEntity) Bukkit.getEntity(entityId);
            
            if (entity == null || entity.isDead()) {
                topIterator.remove();
                continue;
            }

            Map<String, AuraInstance> userAuras = entry.getValue();
            Iterator<Map.Entry<String, AuraInstance>> auraIterator = userAuras.entrySet().iterator();
            
            while (auraIterator.hasNext()) {
                AuraInstance aura = auraIterator.next().getValue();
                
                if (aura.isExpired()) {
                    auraIterator.remove();
                    continue;
                }

                // 継続ダメージ（DOT）の処理: lava_burn
                if (aura.getId().equals("lava_burn")) {
                    processLavaBurn(entity, aura);
                }
            }
            
            if (userAuras.isEmpty()) {
                topIterator.remove();
            }
        }
    }

    private void processLavaBurn(LivingEntity victim, AuraInstance aura) {
        Double damage = aura.getMetadata("damage_per_tick");
        LivingEntity attacker = aura.getMetadata("attacker");
        
        if (damage != null && damage > 0) {
            // カスタムダメージプロセスを呼び出し
            DamageContext ctx = new DamageContext(attacker, victim, DeepwitherDamageEvent.DamageType.MAGIC, damage);
            Deepwither.getInstance().getDamageProcessor().process(ctx);
            
            // 視覚演出
            victim.getWorld().spawnParticle(org.bukkit.Particle.LAVA, victim.getLocation().add(0, 1, 0), 2, 0.2, 0.3, 0.2, 0.1);
            victim.getWorld().spawnParticle(org.bukkit.Particle.FLAME, victim.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0.05);
        }
    }
}
