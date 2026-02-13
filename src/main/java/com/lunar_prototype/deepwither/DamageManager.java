package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.PlayerSettingsManager;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.event.onPlayerRecevingDamageEvent;
import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

@DependsOn({StatManager.class, PlayerSettingsManager.class, ChargeManager.class, ManaManager.class})
public class DamageManager implements Listener, IManager {

    private final Set<UUID> isProcessingDamage = new HashSet<>();
    private final IStatManager statManager;
    private final Map<UUID, Long> onHitCooldowns = new HashMap<>();
    private final JavaPlugin plugin;

    // 定数定義
    private static final Set<String> UNDEAD_MOB_IDS = Set.of("melee_skeleton", "ranged_skeleton", "melee_zombi");
    private static final double MAGIC_DEFENSE_DIVISOR = 100.0;
    private static final double DEFENSE_DIVISOR = 100.0; // 物理防御用
    private static final double HEAVY_DEFENSE_DIVISOR = 500.0; // 高耐久用

    // ★ I-Frame/無敵時間 関連
    private final Map<UUID, Long> iFrameEndTimes = new HashMap<>();
    private static final long DAMAGE_I_FRAME_MS = 300; // 0.3秒 = 300ミリ秒

    // ★ コンボシステム
    private final Map<UUID, Integer> comboCounts = new HashMap<>();
    private final Map<UUID, Long> lastComboHitTimes = new HashMap<>();
    private static final long COMBO_TIMEOUT_MS = 2000; // 2秒でコンボリセット

    // ★ 攻撃クールダウン減衰無視 関連
    private static final long COOLDOWN_IGNORE_MS = 300; // 0.3秒 = 300ミリ秒
    private final Map<UUID, Long> lastSpecialAttackTime = new HashMap<>(); // Key: Attacker UUID

    // 盾のクールダウン (ms) - 連続ブロック防止用など
    private final PlayerSettingsManager settingsManager; // ★追加

    public DamageManager(JavaPlugin plugin, IStatManager statManager, PlayerSettingsManager settingsManager) {
        this.plugin = plugin;
        this.statManager = statManager;
        this.settingsManager = settingsManager; // ★追加
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    private final Map<UUID, Map<UUID, MagicHitInfo>> magicHitMap = new HashMap<>();

    private static class MagicHitInfo {
        int hitCount;
        long lastHitTime;

        MagicHitInfo(int hitCount, long lastHitTime) {
            this.hitCount = hitCount;
            this.lastHitTime = lastHitTime;
        }
    }

    public void sendLog(Player player, PlayerSettingsManager.SettingType type, String message) {
        if (settingsManager.isEnabled(player, type)) {
            player.sendMessage(message);
        }
    }

    // ----------------------------------------------------
    // --- B. 物理ダメージ処理 (通常攻撃: 近接 & 遠距離 & 盾) ---
    // ----------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onPhysicalDamage(EntityDamageByEntityEvent e) {
        // ... (既存のコードはそのまま維持)
        if (e.isCancelled())
            return;

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

        if (attacker == null)
            return;
        if (isProcessingDamage.contains(attacker.getUniqueId()))
            return;
        if (!(e.getEntity() instanceof LivingEntity targetLiving))
            return;

        if (targetLiving instanceof Player pTarget && statManager.getActualCurrentHealth(pTarget) <= 0) {
            e.setCancelled(true);
            return;
        }

        if (attacker instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(ItemLoader.IS_WAND,
                    PersistentDataType.BOOLEAN)) {
                e.setCancelled(true);
                return;
            }
        }

        long currentTime = System.currentTimeMillis();

        if (isInvulnerable(targetLiving)) {
            e.setCancelled(true);
            return;
        }

        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)
            return;

        if (targetLiving instanceof Player && isPvPPrevented(attacker, targetLiving)) {
            e.setCancelled(true);
            return;
        }

        StatMap attackerStats = statManager.getTotalStats(attacker);
        StatMap defenderStats = getDefenderStats(targetLiving);

        // 1. 武器カテゴリの特定
        StatType weaponType = getWeaponStatType(attacker.getInventory().getItemInMainHand());

        // 2. ダメージ計算の強化
        double attackPowerFlat = attackerStats
                .getFlat(isProjectile ? StatType.PROJECTILE_DAMAGE : StatType.ATTACK_DAMAGE);
        double attackPowerPercent = attackerStats
                .getPercent(isProjectile ? StatType.PROJECTILE_DAMAGE : StatType.ATTACK_DAMAGE);

        // 武器固有のボーナスを取得 (Flat & Percent)
        double weaponFlat = (weaponType != null) ? attackerStats.getFlat(weaponType) : 0;
        double weaponPercent = (weaponType != null) ? attackerStats.getPercent(weaponType) : 0;

        // 合算計算
        double baseDamage = (e.getDamage() + attackPowerFlat + weaponFlat);
        double totalMultiplier = 1.0 + ((attackPowerPercent + weaponPercent) / 100.0);
        baseDamage *= totalMultiplier;

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

        // --- コンボパッシブの処理 ---
        SkilltreeManager.SkillData skillData = Deepwither.getInstance().getSkilltreeManager().load(attacker.getUniqueId());
        if (skillData.hasSpecialEffect("COMBO_DAMAGE")) {
            double comboValue = skillData.getSpecialEffectValue("COMBO_DAMAGE"); // 1スタックあたりの上昇量(%)
            long lastHit = lastComboHitTimes.getOrDefault(attacker.getUniqueId(), 0L);
            int currentCombo = comboCounts.getOrDefault(attacker.getUniqueId(), 0);

            if (currentTime - lastHit > COMBO_TIMEOUT_MS) {
                currentCombo = 0;
            }

            double comboMultiplier = 1.0 + (currentCombo * (comboValue / 100.0));
            finalDamage *= comboMultiplier;

            if (currentCombo > 0) {
                sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                        "§eコンボ継続中! x" + currentCombo + " §6(+" + Math.round((comboMultiplier - 1.0) * 100) + "%)");
            }

            // コンボ加算 (最大5スタックとする。本来はYAMLから取得すべきだが一旦固定)
            comboCounts.put(attacker.getUniqueId(), Math.min(currentCombo + 1, 5));
            lastComboHitTimes.put(attacker.getUniqueId(), currentTime);
        }

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
                    sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                            "§c攻撃クールダウン！ §c-" + Math.round(reduced) + " §7ダメージ §a(" + Math.round(finalDamage) + ")");
                }
            }
        }

        if (!isProjectile && isSwordWeapon(attacker.getInventory().getItemInMainHand())) {
            if (attacker.getAttackCooldown() >= 0.9f) {
                double rangeH = 3.0;
                double rangeV = 1.5;

                List<Entity> nearbyEntities = targetLiving.getNearbyEntities(rangeH, rangeV, rangeH);
                targetLiving.getWorld().spawnParticle(Particle.SWEEP_ATTACK, targetLiving.getLocation().add(0, 1, 0),
                        1);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                double sweepDamage = finalDamage * 0.5;

                for (Entity nearby : nearbyEntities) {
                    if (nearby.equals(attacker) || nearby.equals(targetLiving))
                        continue;

                    if (nearby instanceof LivingEntity livingTarget) {
                        applyCustomDamage(livingTarget, sweepDamage, attacker);
                        livingTarget.setVelocity(attacker.getLocation().getDirection().multiply(0.3).setY(0.1));
                    }
                }
            }
        }

        ChargeManager chargeManager = Deepwither.getInstance().getChargeManager();
        String chargedType = chargeManager.consumeCharge(attacker.getUniqueId());
        ItemStack item = attacker.getInventory().getItemInMainHand();
        String type = item.getItemMeta().getPersistentDataContainer().get(ItemLoader.CHARGE_ATTACK_KEY,
                PersistentDataType.STRING);
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
                        Vector kb = living.getLocation().toVector().subtract(attacker.getLocation().toVector())
                                .normalize().multiply(1.5).setY(0.6);
                        living.setVelocity(kb);
                    }
                }
                sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, "§c§lCRASH!! §6ハンマーの溜め攻撃を叩き込んだ！");
            }
        }

        if (targetLiving instanceof Player defenderPlayer && defenderPlayer.isBlocking()) {
            Vector toAttackerVec = attacker.getLocation().toVector().subtract(defenderPlayer.getLocation().toVector())
                    .normalize();
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
                sendLog(defenderPlayer, PlayerSettingsManager.SettingType.SHOW_MITIGATION,
                        "§b盾防御！ §7軽減: §a" + Math.round(blockedDamage) + " §c(" + Math.round(finalDamage) + "被弾)");
            }
        }

        finalDamage = Math.max(0.1, finalDamage);

        e.setDamage(0.0);
        if (isProjectile)
            projectileEntity.remove();

        if (isCrit) {
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                    "§6§lクリティカル！ §c+" + Math.round(finalDamage));
            Location hitLoc = targetLiving.getLocation().add(0, 1.2, 0); // ターゲットの胸の高さ
            World world = hitLoc.getWorld();

            // --- 視覚エフェクト: 重層的なパーティクル ---

            // 1. 強烈な閃光 (中心)
            world.spawnParticle(Particle.FLASH, hitLoc, 1, 0, 0, 0, 0, Color.WHITE);

            // 2. 衝撃波 (周囲に広がる空気の歪み)
            world.spawnParticle(Particle.SONIC_BOOM, hitLoc, 1, 0, 0, 0, 0);

            // 3. 飛び散る火花と血しぶきのような演出 (LAVAとCRIT)
            world.spawnParticle(Particle.LAVA, hitLoc, 8, 0.4, 0.4, 0.4, 0.1);
            world.spawnParticle(Particle.CRIT, hitLoc, 30, 0.5, 0.5, 0.5, 0.5);

            // 4. 大きな煙の広がり
            world.spawnParticle(Particle.LARGE_SMOKE, hitLoc, 15, 0.2, 0.2, 0.2, 0.05);

            // --- 音響エフェクト: 複数の音を重ねて重厚感を出す ---

            // 通常のクリティカル音（高ピッチ）
            world.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);

            // 鈍い衝撃音（低ピッチの金床や爆発）
            world.playSound(hitLoc, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.5f);
            world.playSound(hitLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.6f);

            // 斬撃の重み（鋭い音）
            world.playSound(hitLoc, Sound.ITEM_TRIDENT_HIT, 1.0f, 0.7f);
        } else if (isProjectile) {
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE,
                    "§7遠距離命中 §c+" + Math.round(finalDamage) + " §e[" + String.format("%.0f%%", distMult * 100) + "]");
        }

        finalizeDamage(targetLiving, finalDamage, attacker, isProjectile ? DeepwitherDamageEvent.DamageType.PROJECTILE : DeepwitherDamageEvent.DamageType.PHYSICAL);
        tryTriggerOnHitSkill(attacker, targetLiving, attacker.getInventory().getItemInMainHand());

        if (!isProjectile && isSpearWeapon(attacker.getInventory().getItemInMainHand())) {
            spawnSpearThrustEffect(attacker);
            handleSpearCleave(attacker, targetLiving, finalDamage);
        }

        // --- Advanced PvPvE Buffs Logic ---

        // 1. LifeSteal (Drain)
        double lifeSteal = attackerStats.getFinal(StatType.LIFESTEAL);
        if (lifeSteal > 0) {
            double healAmount = finalDamage * (lifeSteal / 100.0);
            if (healAmount > 0) {
                statManager.heal(attacker, healAmount);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.5f, 1.2f);
            }
        }

        // 2. Bleed
        if (rollChance(attackerStats.getFinal(StatType.BLEED_CHANCE))) {
            applyBleed(targetLiving, attacker);
        }

        // 3. Freeze
        if (rollChance(attackerStats.getFinal(StatType.FREEZE_CHANCE))) {
            applyFreeze(targetLiving);
        }

        // 4. AoE (Explosive)
        if (rollChance(attackerStats.getFinal(StatType.AOE_CHANCE))) {
            applyAoE(targetLiving, attacker, finalDamage);
        }
    }

    // ----------------------------------------------------
    // --- リスナー: プレイヤー被弾 (バニラ/環境ダメージ用) ---
    // ----------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerReceivingDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player))
            return;
        if (e.isCancelled())
            return;

        // すでにスキル等でダメージ処理済み（無敵時間中）ならキャンセル
        if (isInvulnerable(player)) {
            e.setCancelled(true);
            return;
        }

        StatMap defenderStats = statManager.getTotalStats(player);
        double rawDamage = e.getDamage();
        double finalDamage;
        boolean isMagic = false;
        LivingEntity attacker = null;

        // 攻撃者特定
        if (e instanceof EntityDamageByEntityEvent ev) {
            if (ev.getDamager() instanceof LivingEntity le)
                attacker = le;
            else if (ev.getDamager() instanceof Projectile p && p.getShooter() instanceof LivingEntity le)
                attacker = le;

            if (attacker instanceof Player)
                return;

            // ★★★ 無敵モブ（幽霊）カウンター対策 ★★★
            if (attacker != null && (attacker.getHealth() <= 0 || !attacker.isValid() || attacker.isDead())) {
                attacker.remove();
                iFrameEndTimes.remove(attacker.getUniqueId());
                e.setCancelled(true);
                return;
            }
        }

        // --- ★ 特性データの取得 ★ ---
        List<String> traits = getMobTraits(attacker);

        // --- ★ 特性: BERSERK (狂化) ★ ---
        // HP50%以下ならダメージ1.5倍
        if (attacker != null && traits.contains("BERSERK")) {
            double maxHp = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (attacker.getHealth() / maxHp <= 0.5) {
                rawDamage *= 1.5;
            }
        }

        // --- ★ 特性: PIERCING (貫通) ★ ---
        // 貫通持ちなら防御効果を50%カットして計算する
        double defenseMultiplier = traits.contains("PIERCING") ? 0.5 : 1.0;

        // 1. 爆発（魔法）処理
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            double res = defenderStats.getFinal(StatType.MAGIC_RESIST) * defenseMultiplier;
            finalDamage = applyDefense(rawDamage, res, 100.0);
            isMagic = true;
        } else if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            // --- 落下耐性の処理 ---
            double fallRes = defenderStats.getFinal(StatType.DROP_RESISTANCE);
            double reduction = Math.max(0, Math.min(fallRes / 100.0, 1.0)); // 最大100%軽減
            double blocked = rawDamage * reduction;
            finalDamage = rawDamage - blocked;
            
            if (blocked > 0) {
                sendLog(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION, 
                        "§b落下耐性！ §7軽減: §a" + Math.round(blocked) + " §c(" + Math.round(finalDamage) + "被弾)");
            }
        } else {
            // 2. モブ攻撃と防御計算
            double currentDamage = (attacker instanceof Mob) ? applyMobCritLogic(attacker, rawDamage, player)
                    : rawDamage;

            double def = defenderStats.getFinal(StatType.DEFENSE) * defenseMultiplier;
            finalDamage = applyDefense(currentDamage, def, 500.0);

            // 3. 盾防御
            if (player.isBlocking() && attacker != null) {
                Vector toAttacker = attacker.getLocation().toVector().subtract(player.getLocation().toVector())
                        .normalize();
                if (toAttacker.dot(player.getLocation().getDirection().normalize()) > 0.5) {
                    double blocked = finalDamage
                            * Math.max(0, Math.min(defenderStats.getFinal(StatType.SHIELD_BLOCK_RATE), 1.0));
                    finalDamage -= blocked;
                    player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                }
            }
        }

        // --- ★ 特性: 状態異常系 (MANA_LEECH / DISRUPTIVE) ★ ---
        if (attacker != null) {
            if (traits.contains("MANA_LEECH")) {
                Deepwither.getInstance().getManaManager().get(player.getUniqueId()).consume(2);
            }
            if (traits.contains("DISRUPTIVE")) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.MINING_FATIGUE, 60, 1));
            }
        }

        e.setDamage(0.0);
        DeepwitherDamageEvent.DamageType damageType = isMagic ? DeepwitherDamageEvent.DamageType.MAGIC : DeepwitherDamageEvent.DamageType.PHYSICAL;
        if (attacker == null) {
            damageType = DeepwitherDamageEvent.DamageType.ENVIRONMENTAL;
        }
        
        finalizeDamage(player, finalDamage, attacker, damageType);
    }

    /**
     * PDCから特性リストを取得するヘルパー
     */
    private List<String> getMobTraits(LivingEntity entity) {
        if (entity == null) return Collections.emptyList();
        String data = entity.getPersistentDataContainer().get(
                new NamespacedKey(Deepwither.getInstance(), "mob_traits"),
                org.bukkit.persistence.PersistentDataType.STRING
        );
        if (data == null || data.isEmpty()) return Collections.emptyList();
        return Arrays.asList(data.split(","));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onGhostEntityCheck(EntityDamageEvent e) {
        // プレイヤー以外のモブが対象
        if (!(e.getEntity() instanceof LivingEntity target) || target instanceof Player) return;

        // 幽霊モブの条件：HPが0以下、あるいは死んでいる判定なのに、存在し続けている
        if (target.getHealth() <= 0 || target.isDead() || !target.isValid()) {

            // 当たり判定が消えている場合が多いので、このイベントが呼ばれたこと自体がチャンス
            iFrameEndTimes.remove(target.getUniqueId()); // 無敵時間マップも掃除

            // 1ティック後に生存していたら強制排除
            Bukkit.getScheduler().runTask(Deepwither.getInstance(), () -> {
                if (target.isValid()) {
                    target.remove();
                    // Bukkit.getLogger().info("[Deepwither] 幽霊モブを検知し強制排除しました: " + target.getType());
                }
            });
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        // モブが死んだら、その瞬間に無敵時間管理から外す
        iFrameEndTimes.remove(e.getEntity().getUniqueId());
    }

    public StatMap getDefenderStats(LivingEntity entity) {
        if (entity instanceof Player p) {
            return statManager.getTotalStats(p);
        }
        return new StatMap();
    }

    public double applyDefense(double damage, double defense, double divisor) {
        double reduction = defense / (defense + divisor);
        return damage * (1.0 - reduction);
    }

    public boolean rollChance(double chance) {
        return (Math.random() * 100) + 1 <= chance;
    }

    public boolean handleUndeadDamage(Player player, LivingEntity target) {
        String mobId = MythicBukkit.inst().getMobManager().getActiveMob(target.getUniqueId())
                .map(am -> am.getMobType()).orElse(null);

        if (mobId != null && UNDEAD_MOB_IDS.contains(mobId)) {
            double damage = target.getHealth() * 0.5;
            sendLog(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, "§5§l聖特攻！ §fアンデッドに§5§l50%ダメージ！");
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);

            applyCustomDamage(target, damage, player);
            return true;
        }
        return false;
    }

    public void handleLifesteal(Player player, LivingEntity target, double baseDamage) {
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
            sendLog(target, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, "§4§l敵のクリティカル！");

            Location hitLoc = target.getLocation().add(0, 1.2, 0);
            World world = hitLoc.getWorld();

            // --- 視覚エフェクト: エラーの起きにくい構成 ---

            // 1. 強烈な閃光
            world.spawnParticle(Particle.FLASH, hitLoc, 1, 0, 0, 0, 0, Color.WHITE);

            // 2. 衝撃波
            world.spawnParticle(Particle.SONIC_BOOM, hitLoc, 1, 0, 0, 0, 0);

            // 3. 演出（LAVAとCRITはColorデータ不要なので安全）
            world.spawnParticle(Particle.LAVA, hitLoc, 8, 0.4, 0.4, 0.4, 0.1);
            world.spawnParticle(Particle.CRIT, hitLoc, 30, 0.5, 0.5, 0.5, 0.5);
            // 4. ダメージの重みを出す追加エフェクト (BLOCK_MARKER等を使う場合は注意が必要)

            // 5. 煙
            world.spawnParticle(Particle.LARGE_SMOKE, hitLoc, 15, 0.2, 0.2, 0.2, 0.05);

            // --- 音響エフェクト ---
            world.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
            world.playSound(hitLoc, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.5f);
            world.playSound(hitLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.6f);
            world.playSound(hitLoc, Sound.ITEM_TRIDENT_HIT, 1.0f, 0.7f);
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
                        attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
                        sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE,
                                "§e貫通！ §c+" + Math.round(cleaveDmg));
                    }
                });
    }

    public double applyMobCritLogic(LivingEntity attacker, double damage, Player target) {
        if (rollChance(20)) {
            damage *= 1.5;
            Location loc = target.getLocation().add(0, 1.2, 0);
            loc.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
            sendLog(target, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, "§4§l敵のクリティカル！");
        }
        return damage;
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
                    0);
        }

        attacker.getWorld().playSound(
                attacker.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                0.6f,
                1.4f);
    }

    // --- Advanced Buff Private Methods ---

    private void applyBleed(LivingEntity target, Player attacker) {
        // 簡易実装: 5回、1秒ごとにダメージ
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (target.isDead() || !target.isValid() || count >= 5) {
                    this.cancel();
                    return;
                }
                // 固定ダメージ or 割合？ 一旦固定1ダメージ
                applyCustomDamage(target, 1.0, attacker);
                target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 10, 0.2, 0.5,
                        0.2, Bukkit.createBlockData(Material.REDSTONE_BLOCK));
                count++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 20L, 20L);
        sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, "§4出血付与！");
    }

    private void applyFreeze(LivingEntity target) {
        target.setFreezeTicks(140); // 7秒くらい
        // 視覚効果
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 15, 0.5, 1.0, 0.5, 0.05);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
    }

    private void applyAoE(LivingEntity mainTarget, Player attacker, double damage) {
        double splashDamage = damage * 0.5; // 50%
        mainTarget.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, mainTarget.getLocation().add(0, 1, 0), 1);
        mainTarget.getWorld().playSound(mainTarget.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);

        for (Entity e : mainTarget.getNearbyEntities(3, 3, 3)) {
            if (e instanceof LivingEntity living && !e.equals(attacker) && !e.equals(mainTarget)) {
                if (!isProcessingDamage.contains(attacker.getUniqueId())) {
                    applyCustomDamage(living, splashDamage, attacker);
                }
            }
        }
        sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, "§e拡散ヒット！");
    }

    @Deprecated
    public void finalizeDamage(LivingEntity target, double damage, LivingEntity source, boolean isMagic) {
        finalizeDamage(target, damage, source, isMagic ? DeepwitherDamageEvent.DamageType.MAGIC : DeepwitherDamageEvent.DamageType.PHYSICAL);
    }

    public void finalizeDamage(LivingEntity target, double damage, LivingEntity source, DeepwitherDamageEvent.DamageType type) {
        // 1. PvP チェック (攻撃者とターゲットが両方プレイヤーの場合)
        if (source instanceof Player attacker && target instanceof Player playerTarget) {
            if (isPvPPrevented(attacker, playerTarget)) {
                // PvPが制限されている場合は、ダメージも無敵時間付与も行わずに終了
                return;
            }
        }

        // --- カスタムイベントの呼び出し ---
        DeepwitherDamageEvent dwEvent = new DeepwitherDamageEvent(target, source, damage, type);
        Bukkit.getPluginManager().callEvent(dwEvent);
        if (dwEvent.isCancelled()) return;
        damage = dwEvent.getDamage();

        // 無敵時間の付与 (即座に設定して後続のバニライベントを弾く)
        iFrameEndTimes.put(target.getUniqueId(), System.currentTimeMillis() + DAMAGE_I_FRAME_MS);

        if (target instanceof Player player) {
            // プレイヤーへの適用
            processPlayerDamageWithAbsorption(player, damage, source != null ? source.getName() : "魔法/環境");

            // ログ送信
            String prefix = type == DeepwitherDamageEvent.DamageType.MAGIC ? "§5§l魔法被弾！" : "§c§l物理被弾！";
            sendLog(player, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, prefix + " §c" + Math.round(damage));

            // 旧カスタムイベント呼び出し (互換性維持)
            if (source != null) {
                Bukkit.getPluginManager().callEvent(new onPlayerRecevingDamageEvent(player, source, damage));
            }
        } else {
            // モブへの適用
            if (source instanceof Player playerSource) {
                applyCustomDamage(target, damage, playerSource);
            } else {
                target.damage(damage, source);
            }
        }
    }

    public boolean isInvulnerable(LivingEntity entity) {
        return System.currentTimeMillis() < iFrameEndTimes.getOrDefault(entity.getUniqueId(), 0L);
    }

    public void applyCustomDamage(LivingEntity target, double damage, Player damager) {
        if (target instanceof Player p) {
            processPlayerDamageWithAbsorption(p, damage, damager.getName());
            return;
        }

        isProcessingDamage.add(damager.getUniqueId());
        try {
            double currentHealth = target.getHealth();

            // 1. 今回のダメージで死ぬかどうかを判定
            if (currentHealth <= damage) {
                // 1. 無敵時間マップから削除（これをしないと、死亡イベント中のダメージが弾かれて処理が止まることがある）
                iFrameEndTimes.remove(target.getUniqueId());
                // トドメを刺す前にHPを最小にして、確実に次のdamage()で死ぬようにする
                target.setHealth(0.5);
                // バニラの死亡イベントを発生させる（経験値やドロップ、MythicMobsの死亡スキル用）
                target.damage(100.0, damager);

                // 2. 数ティック待っても消えていなかったら物理的に消去（保険）
                // target.isDead() が false のまま残るのが「無敵幽霊」の正体
                Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
                    if (target.isValid() && !target.isDead()) {
                        // まだ残っているなら、ドロップ等を無視して消去
                        target.remove();
                    }
                }, 3L); // 3ティック(0.15秒)程度待つ

            } else {
                // 通常のダメージ処理
                target.damage(damage, damager);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isProcessingDamage.remove(damager.getUniqueId());
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
                player.setAbsorptionAmount((float) (newAbs / 10.0));
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

        if (newHp <= 0)
            player.sendMessage("§4" + sourceName + "に倒されました。");
        else
            sendLog(player, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, "§c-" + Math.round(damage) + " HP");
        ;
    }

    public double calculateDistanceMultiplier(Player player, LivingEntity targetLiving) {
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
            com.sk89q.worldguard.protection.regions.RegionContainer container = com.sk89q.worldguard.WorldGuard
                    .getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.regions.RegionQuery query = container.createQuery();
            com.sk89q.worldguard.protection.ApplicableRegionSet set = query
                    .getApplicableRegions(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(attacker.getLocation()));

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
        if (item == null || !item.hasItemMeta())
            return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        Double chance = container.get(ItemLoader.SKILL_CHANCE_KEY, PersistentDataType.DOUBLE);
        Integer cooldown = container.get(ItemLoader.SKILL_COOLDOWN_KEY, PersistentDataType.INTEGER);
        String skillId = container.get(ItemLoader.SKILL_ID_KEY, PersistentDataType.STRING);

        if (chance == null || skillId == null)
            return;

        long lastTrigger = onHitCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = (cooldown != null) ? cooldown * 1000L : 0L;

        if (currentTime < lastTrigger + cooldownMillis) {
            return;
        }

        int roll = (int) (Math.random() * 100) + 1;

        if (roll <= chance) {
            MythicBukkit.inst().getAPIHelper().castSkill(attacker.getPlayer(), skillId);
            onHitCooldowns.put(attacker.getUniqueId(), currentTime);
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                    "§a[On-Hit] スキル「§b" + skillId + "§a」を発動！");
            ;
        }
    }

    @EventHandler
    public void onRegain(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && !e.isCancelled()) {
            double amount = e.getAmount();
            e.setCancelled(true);
            statManager.heal(p, amount);
        }
    }

    /**
     * アイテムのロアから武器カテゴリに応じたStatTypeを返します
     */
    public StatType getWeaponStatType(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore())
            return null;

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (!line.contains("§7カテゴリ:§f"))
                continue;

            if (line.contains("鎌"))
                return StatType.SCYTHE_DAMAGE;
            if (line.contains("大剣"))
                return StatType.GREATSWORD_DAMAGE;
            if (line.contains("槍"))
                return StatType.SPEAR_DAMAGE;
            if (line.contains("斧"))
                return StatType.AXE_DAMAGE;
            if (line.contains("メイス"))
                return StatType.MACE_DAMAGE;
            if (line.contains("剣"))
                return StatType.SWORD_DAMAGE;
            if (line.contains("マチェット"))
                return StatType.MACHETE_DAMAGE;
            if (line.contains("ハンマー"))
                return StatType.HAMMER_DAMAGE;
            if (line.contains("ハルバード"))
                return StatType.HALBERD_DAMAGE;
        }
        return null;
    }
}