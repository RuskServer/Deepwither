package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.core.damage.DamageProcessor;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@DependsOn({StatManager.class, ChargeManager.class, PlayerSettingsManager.class, com.lunar_prototype.deepwither.core.UIManager.class})
public class WeaponMechanicManager implements IManager {

    private final Deepwither plugin;
    private final IStatManager statManager;
    private final ChargeManager chargeManager;
    private final PlayerSettingsManager settingsManager;
    private final com.lunar_prototype.deepwither.core.UIManager uiManager;
    private final Map<UUID, Map<String, Integer>> hitCounters = new ConcurrentHashMap<>();

    public WeaponMechanicManager(Deepwither plugin, IStatManager statManager, ChargeManager chargeManager, PlayerSettingsManager settingsManager, com.lunar_prototype.deepwither.core.UIManager uiManager) {
        this.plugin = plugin;
        this.statManager = statManager;
        this.chargeManager = chargeManager;
        this.settingsManager = settingsManager;
        this.uiManager = uiManager;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        hitCounters.clear();
    }

    public void handleWeaponMechanics(DamageContext context, DamageProcessor processor) {
        Player attacker = context.getAttackerAsPlayer();
        if (attacker == null) return;

        ItemStack item = context.getWeapon();
        if (item == null) return;

        // 1. 剣のスイープ攻撃
        if (isSwordWeapon(item)) {
            handleSwordSweep(attacker, context.getVictim(), context.getFinalDamage(), processor);
        }

        // 1.5 大剣の3撃目スタン
        if (isGreatswordWeapon(item)) {
            handleGreatswordAttack(attacker, context, processor);
        }

        // 2. 槍の貫通攻撃
        if (isSpearWeapon(item)) {
            handleSpearCleave(attacker, context.getVictim(), context.getFinalDamage(), processor);
        }

        // 3. 斧の3撃目防御貫通
        if (isAxeWeapon(item)) {
            handleAxeAttack(attacker, context, processor);
        }

        // 4. ハルバードの広範囲なぎ払い
        if (isHalberdWeapon(item)) {
            handleHalberdAttack(attacker, context.getVictim(), context.getFinalDamage(), processor);
        }

        // 5. ハンマーの溜め攻撃
        handleHammerCrash(attacker, context.getVictim(), context.getFinalDamage(), item, processor);

        // 6. 武器の特殊エフェクト (lava等)
        handleWeaponEffects(context);
    }

    private void handleWeaponEffects(DamageContext context) {
        ItemStack item = context.getWeapon();
        if (item == null || !item.hasItemMeta()) return;
        
        ItemMeta meta = item.getItemMeta();
        String effect = meta.getPersistentDataContainer().get(ItemLoader.WEAPON_EFFECT_KEY, PersistentDataType.STRING);
        if (effect == null) return;

        if ("lava".equals(effect)) {
            // 武器の攻撃力の10%を毎秒ダメージとして設定
            double dotDamage = context.getFinalDamage() * 0.1;
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("damage_per_tick", dotDamage);
            metadata.put("attacker", context.getAttacker());

            // 5秒間（100tick）の独自延焼を付与
            plugin.getAuraManager().addAura(context.getVictim(), "lava_burn", 100, metadata);
            
            // ヒットパーティクル
            context.getVictim().getWorld().spawnParticle(Particle.LAVA, context.getVictim().getLocation().add(0, 1, 0), 4, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private void handleHalberdAttack(Player attacker, LivingEntity target, double damage, DamageProcessor processor) {
        double rangeH = 3.5;
        double rangeV = 2.0;
        List<Entity> nearbyEntities = target.getNearbyEntities(rangeH, rangeV, rangeH);
        
        List<LivingEntity> extraTargets = nearbyEntities.stream()
                .filter(e -> e instanceof LivingEntity && !e.equals(attacker) && !e.equals(target))
                .map(e -> (LivingEntity) e)
                .toList();

        // 自分 + メインターゲット + 周囲の敵
        int totalHit = 1 + extraTargets.size();
        boolean powerBonus = totalHit >= 3;

        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);

        if (powerBonus) {
            uiManager.of(attacker).combatAction("HALBERD CLEAVE!! (" + totalHit + " targets)", NamedTextColor.GOLD);
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.5f, 1.5f);
        }

        double baseRatio = 0.5;
        double finalRatio = powerBonus ? baseRatio * 1.5 : baseRatio;

        for (LivingEntity extra : extraTargets) {
            double cleaveDmg = damage * finalRatio;
            DamageContext cleaveContext = new DamageContext(attacker, extra, contextToType(processor), cleaveDmg);
            processor.process(cleaveContext);
            
            extra.getWorld().spawnParticle(Particle.SWEEP_ATTACK, extra.getLocation().add(0, 1, 0), 1);
            if (powerBonus) {
                extra.getWorld().spawnParticle(Particle.CRIT, extra.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.1);
            }
        }
    }

    private void handleAxeAttack(Player attacker, DamageContext context, DamageProcessor processor) {
        if (!isFullCharge(context)) {
            return;
        }

        UUID uuid = attacker.getUniqueId();
        String weaponId = context.getWeapon().getType().name(); // もしCustomIDがあればそれが望ましいが、一旦Typeで
        
        // PDCからCustomIDを取得できるか確認
        ItemMeta meta = context.getWeapon().getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "custom_id"), PersistentDataType.STRING)) {
            weaponId = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "custom_id"), PersistentDataType.STRING);
        }

        Map<String, Integer> playerHits = hitCounters.computeIfAbsent(uuid, k -> new HashMap<>());
        int currentHits = playerHits.getOrDefault(weaponId, 0) + 1;
        
        if (currentHits >= 3) {
            // 3撃目のボーナス: 防御貫通タグを付与 (DamageManager側で50%防御減算として処理)
            context.setDefenseBypassPercent(Math.max(context.getDefenseBypassPercent(), 50.0));
            
            playerHits.put(weaponId, 0); // リセット
            
            attacker.getWorld().spawnParticle(Particle.CRIT, context.getVictim().getLocation().add(0, 1.2, 0), 10, 0.2, 0.2, 0.2, 0.1);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5f, 1.2f);
            
            uiManager.of(attacker).combatAction("ARMOR CRUSH!!", NamedTextColor.RED);
            uiManager.of(attacker).message(PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, 
                    Component.text("50% 防御貫通攻撃！", NamedTextColor.GOLD));
        } else {
            playerHits.put(weaponId, currentHits);
            
            // 進捗をアクションバーで通知
            uiManager.of(attacker).progressBar("斧コンボ", currentHits, 3, NamedTextColor.GRAY);
        }
    }

    private void handleGreatswordAttack(Player attacker, DamageContext context, DamageProcessor processor) {
        if (!isFullCharge(context)) {
            return;
        }

        UUID uuid = attacker.getUniqueId();
        String weaponId = context.getWeapon().getType().name();
        
        ItemMeta meta = context.getWeapon().getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "custom_id"), PersistentDataType.STRING)) {
            weaponId = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "custom_id"), PersistentDataType.STRING);
        }

        Map<String, Integer> playerHits = hitCounters.computeIfAbsent(uuid, k -> new HashMap<>());
        int currentHits = playerHits.getOrDefault(weaponId, 0) + 1;
        
        if (currentHits >= 3) {
            // 3撃目のボーナス: 0.3秒スタン (移動不能 + ジャンプ不能)
            LivingEntity victim = context.getVictim();
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 6, 10, false, false, false));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 6, 200, false, false, false));
            
            // 威力50%アップ
            context.setFinalDamage(context.getFinalDamage() * 1.5);
            
            playerHits.put(weaponId, 0); // リセット
            
            victim.getWorld().spawnParticle(Particle.EXPLOSION, victim.getLocation().add(0, 1, 0), 1);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.8f, 0.5f);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 0.8f);

            uiManager.of(attacker).combatAction("STUN!!", NamedTextColor.AQUA);
            uiManager.of(attacker).message(PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, 
                    Component.text("大剣の強撃で相手を怯ませた！", NamedTextColor.YELLOW));
        } else {
            playerHits.put(weaponId, currentHits);
            
            // 進捗をアクションバーで通知
            uiManager.of(attacker).progressBar("大剣コンボ", currentHits, 3, NamedTextColor.GOLD);
        }
    }

    private void handleSwordSweep(Player attacker, LivingEntity target, double damage, DamageProcessor processor) {
        if (attacker.getAttackCooldown() < 0.9f) return;

        double rangeH = 3.0;
        double rangeV = 1.5;
        List<Entity> nearbyEntities = target.getNearbyEntities(rangeH, rangeV, rangeH);
        
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        
        double sweepDamage = damage * 0.5;
        for (Entity nearby : nearbyEntities) {
            if (nearby.equals(attacker) || nearby.equals(target)) continue;
            if (nearby instanceof LivingEntity livingTarget) {
                // 新しいコンテキストでダメージを適用 (再帰的にメカニクスは発動させないよう注意)
                DamageContext sweepContext = new DamageContext(attacker, livingTarget, contextToType(processor), sweepDamage);
                processor.process(sweepContext);
                livingTarget.setVelocity(attacker.getLocation().getDirection().multiply(0.3).setY(0.1));
            }
        }
    }

    private void handleSpearCleave(Player attacker, LivingEntity mainTarget, double damage, DamageProcessor processor) {
        spawnSpearThrustEffect(attacker);
        
        Vector dir = attacker.getLocation().getDirection().normalize();
        // メインターゲットの奥方向をより広範囲にカバーするように調整
        Location checkLoc = mainTarget.getLocation().clone().add(dir.clone().multiply(1.5));
        
        // 直線状の判定として、距離でソートして減衰を適用
        List<LivingEntity> extraTargets = mainTarget.getWorld().getNearbyEntities(checkLoc, 1.8, 1.8, 1.8).stream()
                .filter(e -> e instanceof LivingEntity && e != attacker && e != mainTarget)
                .map(e -> (LivingEntity) e)
                .sorted(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(attacker.getLocation())))
                .toList();

        double baseCleaveRatio = 0.5; // 貫通ダメージの基準（メインの50%）
        double decayFactor = 0.8;    // 1体ヒットごとに20%減衰

        for (int i = 0; i < extraTargets.size(); i++) {
            LivingEntity target = extraTargets.get(i);
            
            // ヒット数に応じた減衰計算 (0体目=メインの次の敵, 1体目=そのさらに奥...)
            double currentRatio = baseCleaveRatio * Math.pow(decayFactor, i);
            double cleaveDmg = damage * currentRatio;

            if (cleaveDmg < 0.5) continue; 

            DamageContext cleaveContext = new DamageContext(attacker, target, contextToType(processor), cleaveDmg);
            processor.process(cleaveContext);
            
            target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
            
            String countSuffix = (extraTargets.size() > 1) ? "(" + (i + 1) + "体目)" : "";
            uiManager.of(attacker).message(PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, 
                    Component.text("貫通" + countSuffix + "! ", NamedTextColor.YELLOW)
                            .append(Component.text("+" + Math.round(cleaveDmg), NamedTextColor.RED)));
        }
    }

    private void handleHammerCrash(Player attacker, LivingEntity target, double damage, ItemStack item, DamageProcessor processor) {
        String chargedType = chargeManager.consumeCharge(attacker.getUniqueId());
        ItemMeta meta = item != null ? item.getItemMeta() : null;
        String type = meta != null ? meta.getPersistentDataContainer().get(ItemLoader.CHARGE_ATTACK_KEY, PersistentDataType.STRING) : null;
        
        if (type != null && "hammer".equals(chargedType)) {
            // ハンマーの溜め攻撃は既に計算済みのダメージを3倍にするのではなく、
            // ここで追加の範囲ダメージを発生させる
            Location loc = target.getLocation();
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
            loc.getWorld().spawnParticle(Particle.SONIC_BOOM, loc, 1);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
            loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.1f);

            double range = 4.0;
            double areaDamage = damage * 0.7 * 3.0; // 溜め倍率を考慮
            for (Entity nearby : target.getNearbyEntities(range, range, range)) {
                if (nearby instanceof LivingEntity living && !nearby.equals(attacker)) {
                    if (!nearby.equals(target)) {
                        DamageContext areaContext = new DamageContext(attacker, living, contextToType(processor), areaDamage);
                        processor.process(areaContext);
                    }
                    Vector kb = living.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(1.5).setY(0.6);
                    living.setVelocity(kb);
                }
            }
            uiManager.of(attacker).combatAction("CRASH!!", NamedTextColor.RED);
            uiManager.of(attacker).message(PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, 
                    Component.text("ハンマーの溜め攻撃を叩き込んだ！", NamedTextColor.GOLD));
        }
    }

    private void spawnSpearThrustEffect(Player attacker) {
        Location start = attacker.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        double length = 2.8;
        double step = 0.2;
        for (double i = 0; i <= length; i += step) {
            Location point = start.clone().add(dir.clone().multiply(i));
            attacker.getWorld().spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
        }
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 1.4f);
    }

    private com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent.DamageType contextToType(DamageProcessor processor) {
        // デフォルトで物理として扱う（再帰用）
        return com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent.DamageType.PHYSICAL;
    }

    private boolean hasCategory(ItemStack item, String category) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(ItemFactory.ITEM_TYPE_KEY, PersistentDataType.STRING)) return false;

        String type = meta.getPersistentDataContainer().get(ItemFactory.ITEM_TYPE_KEY, PersistentDataType.STRING);
        return category.equals(type);
    }

    private boolean isHalberdWeapon(ItemStack item) {
        return hasCategory(item, "ハルバード");
    }

    private boolean isSpearWeapon(ItemStack item) {
        return hasCategory(item, "槍");
    }

    private boolean isAxeWeapon(ItemStack item) {
        return hasCategory(item, "斧");
    }

    private boolean isSwordWeapon(ItemStack item) {
        return hasCategory(item, "剣");
    }

    private boolean isGreatswordWeapon(ItemStack item) {
        return hasCategory(item, "大剣");
    }

    private boolean isFullCharge(DamageContext context) {
        return Boolean.TRUE.equals(context.get("is_full_charge"));
    }
}
