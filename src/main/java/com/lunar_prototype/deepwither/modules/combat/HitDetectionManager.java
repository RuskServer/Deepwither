package com.lunar_prototype.deepwither.modules.combat;

import com.lunar_prototype.deepwither.*;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.core.damage.DamageProcessor;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HitDetectionManager implements IManager {

    private final Deepwither plugin;
    private final StatManager statManager;
    private final DamageProcessor damageProcessor;

    private final Set<UUID> debugPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // 攻撃済みフラグ（二重ダメージ防止）
    private final Map<UUID, Set<UUID>> recentlyHit = new ConcurrentHashMap<>();

    private static final Map<String, WeaponHitProfile> PROFILES = new HashMap<>();

    static {
        // 剣: 標準的ななぎ払い
        PROFILES.put("剣", new WeaponHitProfile(new ArcShape(120, 2.0), 3.0));
        // 大剣: リーチが長く、角度は少し狭い
        PROFILES.put("大剣", new WeaponHitProfile(new ArcShape(80, 2.5), 4.0));
        // 槍: 非常に長く、細い
        PROFILES.put("槍", new WeaponHitProfile(new RayShape(0.3), 2.0));
        // 斧: 重厚ななぎ払い
        PROFILES.put("斧", new WeaponHitProfile(new ArcShape(50, 3.0), 3.0));
        // ハルバード: 広範囲
        PROFILES.put("ハルバード", new WeaponHitProfile(new ArcShape(60, 2.5), 1.5));
        // 鎌: 横に広い
        PROFILES.put("鎌", new WeaponHitProfile(new ArcShape(160, 2.0), 2.5));
        // メイス・ハンマー: 判定は短めだが厚い
        PROFILES.put("メイス", new WeaponHitProfile(new ArcShape(30, 3.5), 1.5));
        PROFILES.put("ハンマー", new WeaponHitProfile(new ArcShape(30, 4.0), 2.5));
        PROFILES.put("マチェット", new WeaponHitProfile(new ArcShape(40, 2.0), 2.8));
    }

    public HitDetectionManager(Deepwither plugin, StatManager statManager, DamageProcessor damageProcessor) {
        this.plugin = plugin;
        this.statManager = statManager;
        this.damageProcessor = damageProcessor;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        recentlyHit.clear();
        debugPlayers.clear();
    }

    public void toggleDebug(Player player) {
        if (debugPlayers.contains(player.getUniqueId())) {
            debugPlayers.remove(player.getUniqueId());
            player.sendMessage("§a[HitDetection] デバッグ表示を無効にしました。");
        } else {
            debugPlayers.add(player.getUniqueId());
            player.sendMessage("§a[HitDetection] デバッグ表示を有効にしました。");
        }
    }

    public boolean isRecentlyHit(UUID attacker, UUID victim) {
        Set<UUID> set = recentlyHit.get(attacker);
        return set != null && set.contains(victim);
    }

    public void performHitDetection(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        WeaponHitProfile profile = getProfile(item);
        if (profile == null) return;

        double reachBonus = statManager.getTotalStats(player).getFinal(StatType.REACH);
        double finalReach = profile.baseReach + reachBonus;

        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection();

        // 実際の攻撃演出（斬撃エフェクトなど）
        profile.shape.spawnSlashEffect(origin, direction, finalReach);

        // デバッグ表示
        if (debugPlayers.contains(player.getUniqueId())) {
            profile.shape.drawDebug(origin, direction, finalReach);
        }

        // 判定対象の収集
        Collection<Entity> candidates = player.getWorld().getNearbyEntities(origin, finalReach + 2, finalReach + 2, finalReach + 2);
        List<LivingEntity> hits = new ArrayList<>();

        for (Entity entity : candidates) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;

            // 形状チェック
            if (!profile.shape.isHit(origin, direction, target, finalReach)) continue;

            // 遮蔽チェック (壁越し判定を防止)
            RayTraceResult ray = player.getWorld().rayTraceBlocks(origin, target.getLocation().add(0, target.getHeight()/2, 0).toVector().subtract(origin.toVector()),
                    origin.distance(target.getLocation().add(0, target.getHeight()/2, 0)), FluidCollisionMode.NEVER, true);
            if (ray != null && ray.getHitBlock() != null) continue;

            hits.add(target);
        }

        if (hits.isEmpty()) return;

        // ヒット処理
        Set<UUID> hitSet = recentlyHit.computeIfAbsent(player.getUniqueId(), k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

        for (LivingEntity victim : hits) {
            // 二重ヒット防止（同スイング内）
            if (hitSet.contains(victim.getUniqueId())) continue;
            hitSet.add(victim.getUniqueId());

            // ダメージ処理
            processHit(player, victim, item);
        }

        // 1tick後にフラグをクリア
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> recentlyHit.remove(player.getUniqueId()), 1L);
    }

    private void processHit(Player attacker, LivingEntity victim, ItemStack weapon) {
        // DamageManager.onPhysicalDamage のロジックを模倣して DamageProcessor に投げる
        double baseDamage = attacker.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getValue();

        // クールダウン状態
        float attackCooldown = attacker.getAttackCooldown();
        boolean isFullCharge = attackCooldown >= 0.9f;

        DamageContext context = new DamageContext(attacker, victim, DeepwitherDamageEvent.DamageType.PHYSICAL, baseDamage);
        context.setWeapon(weapon);
        context.put("is_full_charge", isFullCharge);

        // 武器種別の取得
        String category = getWeaponCategory(weapon);
        if (category != null) {
            context.setWeaponStatType(Deepwither.getInstance().getDamageManager().getWeaponStatType(weapon));
        }

        // クリティカル判定
        StatMap attackerStats = statManager.getTotalStats(attacker);
        if (isFullCharge && com.lunar_prototype.deepwither.core.damage.DamageCalculator.rollPseudoChance(attacker, attackerStats.getFinal(StatType.CRIT_CHANCE))) {
            context.setCrit(true);
        }

        // メカニクスとアーティファクト
        Deepwither.getInstance().getArtifactManager().handleArtifactSetTrigger(context, com.lunar_prototype.deepwither.ItemFactory.ArtifactSetTrigger.ATTACK_HIT);
        if (context.isCrit()) {
            Deepwither.getInstance().getArtifactManager().handleArtifactSetTrigger(context, com.lunar_prototype.deepwither.ItemFactory.ArtifactSetTrigger.CRIT);
        }

        Deepwither.getInstance().getWeaponMechanicManager().handleWeaponMechanics(context, damageProcessor);

        // ダメージ適用
        damageProcessor.process(context);
    }

    public WeaponHitProfile getProfile(ItemStack item) {
        String category = getWeaponCategory(item);
        if (category == null) return null;
        return PROFILES.get(category);
    }

    private String getWeaponCategory(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(ItemFactory.ITEM_TYPE_KEY, PersistentDataType.STRING);
    }
}
