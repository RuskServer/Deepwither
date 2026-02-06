package com.lunar_prototype.deepwither.outpost;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.outpost.OutpostManager;
import com.lunar_prototype.deepwither.outpost.OutpostEvent;
import com.lunar_prototype.deepwither.MobSpawnManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({OutpostManager.class, MobSpawnManager.class})
public class OutpostDamageListener implements Listener, IManager {

    private OutpostManager manager;
    private final JavaPlugin plugin;

    public OutpostDamageListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.manager = OutpostManager.getInstance();
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    /**
     * Outpost Mobに与えたダメージを貢献度に記録します。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        OutpostEvent activeEvent = manager.getActiveEvent();
        if (activeEvent == null) return;

        // 1. 被ダメージエンティティがOutpost Mobであるかチェック
        Entity damagedEntity = event.getEntity();
        String mobOutpostId = Deepwither.getInstance().getMobSpawnManager().getMobOutpostId(damagedEntity);

        if (mobOutpostId == null || !mobOutpostId.equals(activeEvent.getOutpostRegionId())) {
            return; // Outpost Mobでなければスキップ
        }

        // 2. 攻撃者がプレイヤーであるかチェック
        Player damager = getFinalDamager(event.getDamager());
        if (damager == null) return;

        // 3. 貢献度を記録
        double damage = event.getFinalDamage();
        activeEvent.getTracker().addDamageDealt(damager.getUniqueId(), damage);
    }

    /**
     * Outpost Mobから受けたダメージを貢献度に記録します。
     * 被ダメージが高いほど貢献度スコアに加算されます (コンフィグ設定による)。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        OutpostEvent activeEvent = manager.getActiveEvent();
        if (activeEvent == null) return;

        // 1. 被ダメージエンティティがプレイヤーであるかチェック
        if (!(event.getEntity() instanceof Player player)) return;

        // 2. ダメージソースが Outpost Mobであるかチェック
        if (event instanceof EntityDamageByEntityEvent damageByEntity) {
            Entity damager = getFinalDamager(damageByEntity.getDamager());
            if (damager == null) return; // プレイヤーではない、または不明なソース

            String mobOutpostId = Deepwither.getInstance().getMobSpawnManager().getMobOutpostId(damager);

            if (mobOutpostId != null && mobOutpostId.equals(activeEvent.getOutpostRegionId())) {
                // 3. 貢献度を記録 (Outpost Mobからのダメージのみ)
                double damage = event.getFinalDamage();
                activeEvent.getTracker().addDamageTaken(player.getUniqueId(), damage);
            }
        }
        // NOTE: ダメージソースがエンティティでない (例: 落下) 場合、貢献度には含めません。
    }

    /**
     * 攻撃者が射撃体(Projectile)だった場合、その発射元(Shooter)を返します。
     * @param damager 攻撃エンティティ
     * @return 最終的な攻撃者 (プレイヤー) または null
     */
    private Player getFinalDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        // MythicMobsがスポーンしたMobが攻撃者の場合、damager自体がMobのEntityとなる
        return null;
    }
}