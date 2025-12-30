package com.lunar_prototype.deepwither;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicDamageEvent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import com.lunar_prototype.deepwither.PlayerSettingsManager;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.*;

import static com.lunar_prototype.deepwither.PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE;

public class DamageManager implements Listener {

    private final Set<UUID> isProcessingDamage = new HashSet<>();
    private final StatManager statManager;
    private final Map<UUID, Long> onHitCooldowns = new HashMap<>();

    // 定数定義
    private static final Set<String> UNDEAD_MOB_IDS = Set.of("melee_skeleton", "ranged_skeleton","melee_zombi");
    private static final double MAGIC_DEFENSE_DIVISOR = 100.0;
    private static final double DEFENSE_DIVISOR = 100.0; // 物理防御用
    private static final double HEAVY_DEFENSE_DIVISOR = 500.0; // 高耐久用

    // ★ I-Frame/無敵時間 関連
    private final Map<UUID, Long> iFrameEndTimes = new HashMap<>();
    private static final long DAMAGE_I_FRAME_MS = 300; // 0.3秒 = 300ミリ秒

    // ★ 攻撃クールダウン減衰無視 関連
    private static final long COOLDOWN_IGNORE_MS = 300; // 0.3秒 = 300ミリ秒
    private final Map<UUID, Long> lastSpecialAttackTime = new HashMap<>(); // Key: Attacker UUID

    // 盾のクールダウン (ms) - 連続ブロック防止用など
    private static final long SHIELD_COOLDOWN_MS = 500;
    private final Map<UUID, Long> lastShieldBlockTime = new HashMap<>();
    private final PlayerSettingsManager settingsManager; // ★追加

    public DamageManager(StatManager statManager, PlayerSettingsManager settingsManager) {
        this.statManager = statManager;
        this.settingsManager = settingsManager; // ★追加
    }

    private final Map<UUID, Map<UUID, MagicHitInfo>> magicHitMap = new HashMap<>();

    private static class MagicHitInfo {
        int hitCount;
        long lastHitTime;

        MagicHitInfo(int hitCount, long lastHitTime) {
            this.hitCount = hitCount;
            this.lastHitTime = lastHitTime;
        }
    }

    private void sendLog(Player player, PlayerSettingsManager.SettingType type, String message) {
        if (settingsManager.isEnabled(player, type)) {
            player.sendMessage(message);
        }
    }

    // ----------------------------------------------------
    // --- A. Mythic Mobs スキルダメージ処理 (魔法 & 物理) ---
    // ----------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onMythicDamage(MythicDamageEvent e) {
        if (!(e.getCaster().getEntity().getBukkitEntity() instanceof Player player)) return;
        if (!(e.getTarget().getBukkitEntity() instanceof LivingEntity targetLiving)) return;

        if (targetLiving instanceof Player pTarget && statManager.getActualCurrentHealth(pTarget) <= 0) {
            e.setCancelled(true);
            return;
        }

        long currentTime = System.currentTimeMillis();
        long iFrameEndTime = iFrameEndTimes.getOrDefault(targetLiving.getUniqueId(), 0L);

        if (currentTime < iFrameEndTime) {
            e.setCancelled(true);
            return;
        }
        iFrameEndTimes.put(targetLiving.getUniqueId(), currentTime + DAMAGE_I_FRAME_MS);


        // 特攻タグ処理
        if (e.getDamageMetadata().getTags().contains("UNDEAD")) {
            if (handleUndeadDamage(player, targetLiving, e)) return;
        }

        // LIFESTEAL処理
        if (e.getDamageMetadata().getTags().contains("LIFESTEAL")) {
            handleLifesteal(player, targetLiving, e.getDamage());
        }

        StatMap attackerStats = StatManager.getTotalStatsFromEquipment(player);
        StatMap defenderStats = getDefenderStats(targetLiving);
        EntityDamageEvent.DamageCause cause = e.getDamageMetadata().getDamageCause();

        // ★★★ 修正箇所: 魔法か物理かで分岐 ★★★
        // "ENTITY_EXPLOSION" を魔法ダメージとして定義している前提
        boolean isMagic = (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);

        double baseDamage = 0.0;
        double finalDamage = 0.0;
        String damageMsgPrefix = "";

        if (isMagic) {
            // --- 魔法ダメージ計算 ---
            baseDamage = e.getDamage() + attackerStats.getFinal(StatType.MAGIC_DAMAGE);

            // クリティカル
            boolean isCrit = rollChance(attackerStats.getFinal(StatType.CRIT_CHANCE));
            if (isCrit) {
                baseDamage *= (attackerStats.getFinal(StatType.CRIT_DAMAGE) / 100.0);
                sendLog(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, "§6§l魔法クリティカル！");
            }

            // Burst / AOE 補正
            double bonusDamage = 0;
            boolean isBurst = e.getDamageMetadata().getTags().contains("BURST");
            boolean isAoe = e.getDamageMetadata().getTags().contains("AOE");

            if (isBurst) {
                baseDamage *= 0.4;
                bonusDamage += attackerStats.getFinal(StatType.MAGIC_BURST_DAMAGE);
            }
            if (isAoe) {
                baseDamage *= 0.6;
                bonusDamage += attackerStats.getFinal(StatType.MAGIC_AOE_DAMAGE);
            }
            baseDamage += bonusDamage;

            // 魔法防御計算
            finalDamage = applyDefense(baseDamage, defenderStats.getFinal(StatType.MAGIC_RESIST), MAGIC_DEFENSE_DIVISOR);

            // 連続ヒット減衰
            finalDamage = applyMultiHitDecay(player, targetLiving, finalDamage);

            damageMsgPrefix = isAoe ? "§b§l魔法AOEダメージ！" : (isBurst ? "§c§l魔法バーストダメージ！" : "§b§l魔法ダメージ！");

        } else {
            // --- 物理スキルダメージ計算 (ENTITY_ATTACK, CUSTOMなど) ---
            // ※ MythicMobsのスキルダメージ(e.getDamage()) に 物理攻撃力(ATTACK_DAMAGE) を加算する設計にします
            baseDamage = e.getDamage() + attackerStats.getFinal(StatType.ATTACK_DAMAGE);

            // クリティカル (物理扱いなので同様に判定)
            boolean isCrit = rollChance(attackerStats.getFinal(StatType.CRIT_CHANCE));
            if (isCrit) {
                baseDamage *= (attackerStats.getFinal(StatType.CRIT_DAMAGE) / 100.0);
                player.sendMessage("§6§l物理スキルクリティカル！");
            }

            // 物理防御計算
            finalDamage = applyDefense(baseDamage, defenderStats.getFinal(StatType.DEFENSE), DEFENSE_DIVISOR);

            damageMsgPrefix = "§e§l物理スキルダメージ！";
        }

        // PvPチェック
        if (targetLiving instanceof Player && isPvPPrevented(player, targetLiving)) {
            e.setCancelled(true);
            return;
        }

        // メッセージ表示
        sendLog(player, SHOW_GIVEN_DAMAGE, damageMsgPrefix + " §c+" + Math.round(finalDamage));

        e.setDamage(0.0);
        applyCustomDamage(targetLiving, finalDamage, player);
    }

    // ----------------------------------------------------
    // --- B. 物理ダメージ処理 (通常攻撃: 近接 & 遠距離 & 盾) ---
    // ----------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onPhysicalDamage(EntityDamageByEntityEvent e) {
        // ... (既存のコードはそのまま維持)
        if (e.isCancelled()) return;

        // 攻撃者の特定 (プレイヤーのみ処理)
        Player attacker = null;
        boolean isProjectile = false;
        Projectile projectileEntity = null;

        if (e.getDamager() instanceof Player p) {
            attacker = p;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
            isProjectile = true;
            projectileEntity = proj;
        }

        if (attacker == null) return;
        if (isProcessingDamage.contains(attacker.getUniqueId())) return;
        if (!(e.getEntity() instanceof LivingEntity targetLiving)) return;

        if (targetLiving instanceof Player pTarget && statManager.getActualCurrentHealth(pTarget) <= 0) {
            e.setCancelled(true);
            return;
        }

        if (attacker instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(ItemLoader.IS_WAND, PersistentDataType.BOOLEAN)) {
                e.setCancelled(true);
                return;
            }
        }

        long currentTime = System.currentTimeMillis();
        long iFrameEndTime = iFrameEndTimes.getOrDefault(targetLiving.getUniqueId(), 0L);

        if (currentTime < iFrameEndTime) {
            e.setCancelled(true);
            return;
        }

        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) {
            iFrameEndTimes.put(targetLiving.getUniqueId(), currentTime + DAMAGE_I_FRAME_MS);
        }

        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;

        if (targetLiving instanceof Player && isPvPPrevented(attacker, targetLiving)) {
            e.setCancelled(true);
            return;
        }

        StatMap attackerStats = StatManager.getTotalStatsFromEquipment(attacker);
        StatMap defenderStats = getDefenderStats(targetLiving);

        double attackPower = isProjectile ? attackerStats.getFinal(StatType.PROJECTILE_DAMAGE) : attackerStats.getFinal(StatType.ATTACK_DAMAGE);
        double baseDamage = attackPower + (isProjectile ? e.getDamage() : 0);

        boolean isCrit = rollChance(attackerStats.getFinal(StatType.CRIT_CHANCE));
        if (isCrit) {
            baseDamage *= (attackerStats.getFinal(StatType.CRIT_DAMAGE) / 100.0);
        }

        double distMult = 1.0;
        if (isProjectile) {
            distMult = calculateDistanceMultiplier(attacker, targetLiving);
            baseDamage *= distMult;
        }

        double finalDamage = applyDefense(baseDamage, defenderStats.getFinal(StatType.DEFENSE), DEFENSE_DIVISOR);

        boolean ignoreAttackCooldown = false;

        if (!isProjectile) {
            long lastAttack = lastSpecialAttackTime.getOrDefault(attacker.getUniqueId(), 0L);
            if (currentTime < lastAttack + COOLDOWN_IGNORE_MS) {
                ignoreAttackCooldown = true;
            }
            lastSpecialAttackTime.put(attacker.getUniqueId(), currentTime);
        }

        if (!isProjectile) {
            if (!ignoreAttackCooldown) {
                float cooldown = attacker.getAttackCooldown();
                if (cooldown < 1.0f) {
                    double reduced = finalDamage * (1.0 - cooldown);
                    finalDamage *= cooldown;
                    sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, "§c攻撃クールダウン！ §c-" + Math.round(reduced) + " §7ダメージ §a(" + Math.round(finalDamage) + ")");
                }
            }
        }

        if (!isProjectile && isSwordWeapon(attacker.getInventory().getItemInMainHand())) {
            if (attacker.getAttackCooldown() >= 0.9f) {
                double rangeH = 3.0;
                double rangeV = 1.5;

                List<Entity> nearbyEntities = targetLiving.getNearbyEntities(rangeH, rangeV, rangeH);
                targetLiving.getWorld().spawnParticle(Particle.SWEEP_ATTACK, targetLiving.getLocation().add(0, 1, 0), 1);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                double sweepDamage = finalDamage * 0.5;

                for (Entity nearby : nearbyEntities) {
                    if (nearby.equals(attacker) || nearby.equals(targetLiving)) continue;

                    if (nearby instanceof LivingEntity livingTarget) {
                        applyCustomDamage(livingTarget,sweepDamage,attacker);
                        livingTarget.setVelocity(attacker.getLocation().getDirection().multiply(0.3).setY(0.1));
                    }
                }
            }
        }

        ChargeManager chargeManager = Deepwither.getInstance().getChargeManager();
        String chargedType = chargeManager.consumeCharge(attacker.getUniqueId());
        ItemStack item = attacker.getInventory().getItemInMainHand();
        String type = item.getItemMeta().getPersistentDataContainer().get(ItemLoader.CHARGE_ATTACK_KEY, PersistentDataType.STRING);
        if (type != null) {
            if (chargedType != null && chargedType.equals("hammer")) {
                finalDamage *= 3.0;
                Location loc = targetLiving.getLocation();
                loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
                loc.getWorld().spawnParticle(Particle.SONIC_BOOM, loc, 1);
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
                loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.1f);

                double range = 4.0;
                double areaDamage = finalDamage * 0.7;

                for (Entity nearby : targetLiving.getNearbyEntities(range, range, range)) {
                    if (nearby instanceof LivingEntity living && !nearby.equals(attacker)) {
                        if (!nearby.equals(targetLiving)) {
                            applyCustomDamage(living, areaDamage, attacker);
                        }
                        Vector kb = living.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(1.5).setY(0.6);
                        living.setVelocity(kb);
                    }
                }
                sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, "§c§lCRASH!! §6ハンマーの溜め攻撃を叩き込んだ！");
            }
        }

        if (targetLiving instanceof Player defenderPlayer && defenderPlayer.isBlocking()) {
            Vector toAttackerVec = attacker.getLocation().toVector().subtract(defenderPlayer.getLocation().toVector()).normalize();
            Vector defenderLookVec = defenderPlayer.getLocation().getDirection().normalize();

            if (toAttackerVec.dot(defenderLookVec) > 0.5) {
                if (e.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
                    e.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0);
                }
                double blockRate = defenderStats.getFinal(StatType.SHIELD_BLOCK_RATE);
                blockRate = Math.max(0.0, Math.min(blockRate, 1.0));
                double blockedDamage = finalDamage * blockRate;
                finalDamage -= blockedDamage;
                defenderPlayer.getWorld().playSound(defenderPlayer.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                sendLog(defenderPlayer, PlayerSettingsManager.SettingType.SHOW_MITIGATION, "§b盾防御！ §7軽減: §a" + Math.round(blockedDamage) + " §c(" + Math.round(finalDamage) + "被弾)");
            }
        }

        finalDamage = Math.max(0.1, finalDamage);

        e.setDamage(0.0);
        if (isProjectile) projectileEntity.remove();

        if (isCrit) {
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, "§6§lクリティカル！ §c+" + Math.round(finalDamage));
            attacker.getWorld().spawnParticle(Particle.CRIT, targetLiving.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        } else if (isProjectile) {
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, "§7遠距離命中 §c+" + Math.round(finalDamage) + " §e[" + String.format("%.0f%%", distMult * 100) + "]");
        }

        applyCustomDamage(targetLiving, finalDamage, attacker);
        tryTriggerOnHitSkill(attacker, targetLiving, attacker.getInventory().getItemInMainHand());

        if (!isProjectile && isSpearWeapon(attacker.getInventory().getItemInMainHand())) {
            spawnSpearThrustEffect(attacker);
            handleSpearCleave(attacker, targetLiving, finalDamage);
        }
    }

    // ... (C. 被ダメージ処理以降のコードは変更なしのため省略。元のファイルと同じものを保持してください)
    // ----------------------------------------------------
    // --- C. 被ダメージ処理 (環境・Mob・その他) ---
    // ----------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerReceivingDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (e.isCancelled()) return;

        if (statManager.getActualCurrentHealth(player) <= 0) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long iFrameEndTime = iFrameEndTimes.getOrDefault(player.getUniqueId(), 0L);

        if (currentTime < iFrameEndTime) {
            e.setCancelled(true);
            return;
        }

        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) {
            iFrameEndTimes.put(player.getUniqueId(), currentTime + DAMAGE_I_FRAME_MS);
        }

        LivingEntity attacker = null;
        if (e instanceof EntityDamageByEntityEvent ev) {
            if (ev.getDamager() instanceof LivingEntity le) attacker = le;
            else if (ev.getDamager() instanceof Projectile p && p.getShooter() instanceof LivingEntity le) attacker = le;
            if (attacker instanceof Player) return;
        }

        double incomingDamage = e.getFinalDamage();
        StatMap defenderStats = StatManager.getTotalStatsFromEquipment(player);

        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {

            double reduced = applyDefense(incomingDamage, defenderStats.getFinal(StatType.MAGIC_RESIST), MAGIC_DEFENSE_DIVISOR);
            sendLog(player, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, "§5§l魔法被弾！ §c" + String.format("%.1f", reduced));

            e.setDamage(0.0);
            processPlayerDamageWithAbsorption(player, reduced, attacker != null ? attacker.getName() : "魔法");
            return;
        }

        if (attacker instanceof Mob) {
            incomingDamage = handleMobDamageLogic(attacker, incomingDamage, player);
        }

        double finalDamage = applyDefense(incomingDamage, defenderStats.getFinal(StatType.DEFENSE), HEAVY_DEFENSE_DIVISOR);

        if (player.isBlocking()) {
            Vector toAttackerVec = attacker.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            Vector defenderLookVec = player.getLocation().getDirection().normalize();

            if (toAttackerVec.dot(defenderLookVec) > 0.5) {
                if (e.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
                    e.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0);
                }

                double blockRate = defenderStats.getFinal(StatType.SHIELD_BLOCK_RATE);
                blockRate = Math.max(0.0, Math.min(blockRate, 1.0));
                double blockedDamage = finalDamage * blockRate;
                finalDamage -= blockedDamage;
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                sendLog(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION, "§b盾防御！ §7軽減: §a" + Math.round(blockedDamage) + " §c(" + Math.round(finalDamage) + "被弾)");
            }
        }

        e.setDamage(0.0);
        processPlayerDamageWithAbsorption(player, finalDamage, attacker != null ? attacker.getName() : "環境");
    }

    private StatMap getDefenderStats(LivingEntity entity) {
        if (entity instanceof Player p) {
            return StatManager.getTotalStatsFromEquipment(p);
        }
        return new StatMap();
    }

    private double applyDefense(double damage, double defense, double divisor) {
        double reduction = defense / (defense + divisor);
        return damage * (1.0 - reduction);
    }

    private boolean rollChance(double chance) {
        return (Math.random() * 100) + 1 <= chance;
    }

    private boolean handleUndeadDamage(Player player, LivingEntity target, MythicDamageEvent e) {
        String mobId = MythicBukkit.inst().getMobManager().getActiveMob(target.getUniqueId())
                .map(am -> am.getMobType()).orElse(null);

        if (mobId != null && UNDEAD_MOB_IDS.contains(mobId)) {
            double damage = target.getHealth() * 0.5;
            sendLog(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, "§5§l聖特攻！ §fアンデッドに§5§l50%ダメージ！");
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);

            e.setDamage(0.0);
            e.setCancelled(true);
            applyCustomDamage(target, damage, player);
            return true;
        }
        return false;
    }

    private void handleLifesteal(Player player, LivingEntity target, double baseDamage) {
        double heal = target.getMaxHealth() * (baseDamage / 100.0 / 100.0);
        heal = Math.min(heal, player.getMaxHealth() * 0.20);

        double newHealth = Math.min(player.getHealth() + heal, player.getMaxHealth());
        player.setHealth(newHealth);

        sendLog(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, String.format("§a§lLS！ §2%.1f §a回復", heal));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
    }

    private double handleMobDamageLogic(LivingEntity attacker, double damage, Player target) {
        if (rollChance(20)) {
            damage *= 1.5;
            target.sendMessage("§4§l敵のクリティカル！");
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
        }
        return damage;
    }

    private void handleSpearCleave(Player attacker, LivingEntity mainTarget, double damage) {
        double cleaveDmg = damage * 0.5;
        Vector dir = attacker.getLocation().getDirection().normalize();
        Location checkLoc = mainTarget.getLocation().clone().add(dir);

        mainTarget.getWorld().getNearbyEntities(checkLoc, 1.5, 1.5, 1.5).stream()
                .filter(e -> e instanceof LivingEntity && e != attacker && e != mainTarget)
                .map(e -> (LivingEntity) e)
                .forEach(target -> {
                    if (!isProcessingDamage.contains(attacker.getUniqueId())) {
                        applyCustomDamage(target, cleaveDmg, attacker);
                        attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0,1,0), 1);
                        sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, "§e貫通！ §c+" + Math.round(cleaveDmg));
                    }
                });
    }

    private void spawnSpearThrustEffect(Player attacker) {
        Location start = attacker.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        double length = 2.8;
        double step = 0.2;

        for (double i = 0; i <= length; i += step) {
            Location point = start.clone().add(dir.clone().multiply(i));

            attacker.getWorld().spawnParticle(
                    Particle.CRIT,
                    point,
                    1,
                    0, 0, 0,
                    0
            );
        }

        attacker.getWorld().playSound(
                attacker.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                0.6f,
                1.4f
        );
    }


    public void applyCustomDamage(LivingEntity target, double damage, Player damager) {
        if (target instanceof Player p) {
            if (this.isPvPPrevented(damager, target)) {
                return;
            }
            processPlayerDamageWithAbsorption(p, damage, damager.getName());
        } else {
            isProcessingDamage.add(damager.getUniqueId());
            try {
                target.damage(damage, damager);
                if (!target.isDead() && target.getHealth() <= 0.5) {
                    target.setHealth(0.0);
                    if (!target.isDead()) {
                        target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0, 1, 0), 1);
                        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_DEATH, 1.0f, 1.0f);
                        target.remove();
                    }
                }
            } finally {
                isProcessingDamage.remove(damager.getUniqueId());
            }
        }
    }

    private double applyMultiHitDecay(Player attacker, LivingEntity target, double damage) {
        UUID attackerId = attacker.getUniqueId();
        UUID targetId = target.getUniqueId();
        long now = System.currentTimeMillis();

        magicHitMap.putIfAbsent(attackerId, new HashMap<>());
        Map<UUID, MagicHitInfo> targetMap = magicHitMap.get(attackerId);

        MagicHitInfo info = targetMap.get(targetId);

        if (info != null && now - info.lastHitTime <= 1000) {
            info.hitCount++;
            info.lastHitTime = now;
        } else {
            info = new MagicHitInfo(1, now);
            targetMap.put(targetId, info);
        }

        if (info.hitCount >= 2) {
            double multiplier = Math.pow(0.85, info.hitCount - 1);
            multiplier = Math.max(multiplier, 0.5);
            return damage * multiplier;
        }

        return damage;
    }


    private void processPlayerDamageWithAbsorption(Player player, double damage, String sourceName) {
        if (statManager.getActualCurrentHealth(player) <= 0) {
            return;
        }
        double absorption = player.getAbsorptionAmount() * 10.0;

        if (absorption > 0) {
            if (damage <= absorption) {
                double newAbs = absorption - damage;
                player.setAbsorptionAmount((float)(newAbs / 10.0));
                sendLog(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION, "§eシールド防御: -" + Math.round(damage));
                return;
            } else {
                player.setAbsorptionAmount(0f);
                sendLog(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION, "§cシールドブレイク！");
                return;
            }
        }

        double currentHp = statManager.getActualCurrentHealth(player);
        double newHp = currentHp - damage;
        statManager.setActualCurrentHealth(player, newHp);

        if (newHp <= 0) player.sendMessage("§4" + sourceName + "に倒されました。");
        else sendLog(player, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, "§c-" + Math.round(damage) + " HP");;
    }


    private double calculateDistanceMultiplier(Player player, LivingEntity targetLiving) {
        double distance = targetLiving.getLocation().distance(player.getLocation());

        final double MIN_DISTANCE = 10.0;
        final double MAX_BOOST_DISTANCE = 40.0;
        final double MAX_MULTIPLIER = 1.2;
        final double MIN_MULTIPLIER = 0.6;

        double distanceMultiplier;

        if (distance <= MIN_DISTANCE) {
            double range = MIN_DISTANCE;
            double minMaxDiff = 1.0 - MIN_MULTIPLIER;
            distanceMultiplier = MIN_MULTIPLIER + (distance / range) * minMaxDiff;

        } else if (distance >= MAX_BOOST_DISTANCE) {
            distanceMultiplier = MAX_MULTIPLIER;

        } else {
            double range = MAX_BOOST_DISTANCE - MIN_DISTANCE;
            double current = distance - MIN_DISTANCE;
            double minMaxDiff = MAX_MULTIPLIER - 1.0;
            distanceMultiplier = 1.0 + (current / range) * minMaxDiff;
        }

        return Math.max(MIN_MULTIPLIER, Math.min(distanceMultiplier, MAX_MULTIPLIER));
    }

    private boolean isSpearWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (String line : lore) {
                if (line.contains("§7カテゴリ:§f槍")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSwordWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (String line : lore) {
                if (line.contains("§7カテゴリ:§f剣")) {
                    return true;
                } else if (line.contains("§7カテゴリ:§f大剣")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPvPPrevented(Player attacker, LivingEntity target) {
        if (!target.getWorld().getName().equals(attacker.getWorld().getName())) {
            return false;
        }

        if (!(target instanceof Player)) {
            return false;
        }

        if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return false;
        }

        try {
            com.sk89q.worldguard.protection.regions.RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.regions.RegionQuery query = container.createQuery();
            com.sk89q.worldguard.protection.ApplicableRegionSet set = query.getApplicableRegions(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(attacker.getLocation()));

            if (!set.testState(null, com.sk89q.worldguard.protection.flags.Flags.PVP)) {
                attacker.sendMessage("§cこの区域ではPvPが禁止されています。");
                return true;
            }
            return false;

        } catch (NoClassDefFoundError ex) {
            return false;
        }
    }

    private void tryTriggerOnHitSkill(Player attacker, LivingEntity target, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        Double chance = container.get(ItemLoader.SKILL_CHANCE_KEY, PersistentDataType.DOUBLE);
        Integer cooldown = container.get(ItemLoader.SKILL_COOLDOWN_KEY, PersistentDataType.INTEGER);
        String skillId = container.get(ItemLoader.SKILL_ID_KEY, PersistentDataType.STRING);

        if (chance == null || skillId == null) return;

        long lastTrigger = onHitCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = (cooldown != null) ? cooldown * 1000L : 0L;

        if (currentTime < lastTrigger + cooldownMillis) {
            return;
        }

        int roll = (int) (Math.random() * 100) + 1;

        if (roll <= chance) {
            MythicBukkit.inst().getAPIHelper().castSkill(attacker.getPlayer(),skillId);
            onHitCooldowns.put(attacker.getUniqueId(), currentTime);
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, "§a[On-Hit] スキル「§b" + skillId + "§a」を発動！");;
        }
    }

    @EventHandler
    public void onRegain(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && !e.isCancelled()) {
            double amount = e.getAmount();
            e.setCancelled(true);
            statManager.healCustomHealth(p, amount);
        }
    }
}