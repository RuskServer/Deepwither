package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.core.damage.DamageCalculator;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.core.damage.DamageProcessor;
import com.lunar_prototype.deepwither.loot.RouteLootChestManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
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

@DependsOn({StatManager.class, PlayerSettingsManager.class, ChargeManager.class, ManaManager.class, DamageProcessor.class, WeaponMechanicManager.class, com.lunar_prototype.deepwither.core.UIManager.class})
public class DamageManager implements Listener, IManager {

    private final IStatManager statManager;
    private final com.lunar_prototype.deepwither.core.UIManager uiManager;
    private final JavaPlugin plugin;

    private static final double DEFENSE_DIVISOR = 250.0;
    private static final double PLAYER_DEFENSE_DIVISOR = 250.0;

    private final Map<UUID, Long> iFrameEndTimes = new HashMap<>();
    private static final long DAMAGE_I_FRAME_MS = 300;

    private final PlayerSettingsManager settingsManager;

    public DamageManager(JavaPlugin plugin, IStatManager statManager, PlayerSettingsManager settingsManager, com.lunar_prototype.deepwither.core.UIManager uiManager) {
        this.plugin = plugin;
        this.statManager = statManager;
        this.settingsManager = settingsManager;
        this.uiManager = uiManager;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler(priority = EventPriority.HIGH)
    public void onPhysicalDamage(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;

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

        // 内部呼び出し（DamageProcessor からの再呼び出し）の場合はスキップ
        if (Deepwither.getInstance().getDamageProcessor().isProcessing(attacker.getUniqueId())) return;

        RouteLootChestManager routeLootChestManager = Deepwither.getInstance().getRouteLootChestManager();
        if (routeLootChestManager != null) {
            routeLootChestManager.recordActivity(attacker, 3.0);
        }
        
        Deepwither dw = Deepwither.getInstance();
        DamageProcessor processor = dw.getDamageProcessor();
        
        if (!(e.getEntity() instanceof LivingEntity targetLiving)) return;

        if (targetLiving instanceof Player pTarget && statManager.getActualCurrentHealth(pTarget) <= 0) {
            e.setCancelled(true);
            return;
        }

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(ItemLoader.IS_WAND, PersistentDataType.BOOLEAN)) {
            e.setCancelled(true);
            return;
        }

        if (isInvulnerable(targetLiving)) {
            e.setCancelled(true);
            return;
        }

        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;

        if (targetLiving instanceof Player && isPvPPrevented(attacker, targetLiving)) {
            e.setCancelled(true);
            return;
        }

        // 1. ダメージコンテキストの作成
        StatMap attackerStats = statManager.getTotalStats(attacker);
        StatType weaponType = getWeaponStatType(item);
        
        DeepwitherDamageEvent.DamageType damageType = isProjectile ? DeepwitherDamageEvent.DamageType.PROJECTILE : DeepwitherDamageEvent.DamageType.PHYSICAL;
        
        // クールダウン（溜め）状態の取得 (1.0で最大)
        float attackCooldown = isProjectile ? 1.0f : attacker.getAttackCooldown();
        boolean isFullCharge = attackCooldown >= 0.9f; // 若干の猶予を持たせる

        // ベースダメージはバニラ（武器属性）の威力のみ。ステータス加算は Processor で一本化される。
        double baseDamage = e.getDamage();

        DamageContext context = new DamageContext(attacker, targetLiving, damageType, baseDamage);
        context.setProjectile(isProjectile);
        context.setWeapon(item);
        context.setWeaponStatType(weaponType);
        context.put("is_full_charge", isFullCharge); // メタデータに溜め状態を保存

        // 2. クリティカル判定 (疑似乱数分布 PRD を使用)
        // クールダウンが不十分な場合はクリティカル判定（およびカウント）を行わない
        if (isFullCharge && DamageCalculator.rollPseudoChance(attacker, attackerStats.getFinal(StatType.CRIT_CHANCE))) {
            context.setCrit(true);
        }

        Deepwither.getInstance().getArtifactManager().handleArtifactSetTrigger(context, ItemFactory.ArtifactSetTrigger.ATTACK_HIT);
        if (context.isCrit()) {
            Deepwither.getInstance().getArtifactManager().handleArtifactSetTrigger(context, ItemFactory.ArtifactSetTrigger.CRIT);
        }

        // 3. 距離補正 (倍率の取得のみ)
        if (isProjectile) {
            double distMult = DamageCalculator.calculateDistanceMultiplier(attacker.getLocation(), targetLiving.getLocation());
            context.setDistanceMultiplier(distMult);
        }

        // 4. 武器メカニクス
        dw.getWeaponMechanicManager().handleWeaponMechanics(context, processor);

        e.setDamage(0.0);
        if (isProjectile && projectileEntity != null) projectileEntity.remove();

        // 5. プロセッサへ委譲
        iFrameEndTimes.put(targetLiving.getUniqueId(), System.currentTimeMillis() + DAMAGE_I_FRAME_MS);
        processor.process(context);

        Double lunarBonusMagic = context.get("lunar_echo_bonus_magic");
        if (lunarBonusMagic != null && lunarBonusMagic > 0) {
            DamageContext lunarExtra = new DamageContext(attacker, targetLiving, DeepwitherDamageEvent.DamageType.MAGIC, lunarBonusMagic);
            processor.process(lunarExtra);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeepwitherDamage(DeepwitherDamageEvent e) {
        if (e.getAttacker() instanceof Player attacker && e.getVictim() instanceof Player victim) {
            if (isPvPPrevented(attacker, victim)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFrostArmorDamageReduction(DeepwitherDamageEvent e) {
        if (e.getVictim() == null) return;

        com.lunar_prototype.deepwither.api.skill.aura.AuraManager auraManager = Deepwither.getInstance().getAuraManager();
        
        // 1. Frost Armor (40%軽減)
        com.lunar_prototype.deepwither.api.skill.aura.AuraManager.AuraInstance frostArmor = auraManager.getAuraInstance(e.getVictim(), "frost_armor");
        if (frostArmor != null) {
            Double reduction = (Double) frostArmor.getMetadata("damage_reduction");
            if (reduction != null) {
                e.setDamage(e.getDamage() * (1.0 - reduction));
            }
        }
        // 2. Oath Shield / Luminary Veil (球体シールド内での遠距離・魔法攻撃無効化)
        // 近くのプレイヤーが "oath_shield" オーラを持っており、被害者がその範囲内にいるか確認
        Collection<Player> shieldUsers = e.getVictim().getWorld().getPlayers();
        for (Player user : shieldUsers) {
            com.lunar_prototype.deepwither.api.skill.aura.AuraManager.AuraInstance oathShield = auraManager.getAuraInstance(user, "oath_shield");
            if (oathShield != null) {
                // 自分自身なら常に範囲内、他人なら距離判定 (半径5ブロック = distanceSquared 25)
                boolean inRange = user.equals(e.getVictim()) || user.getLocation().distanceSquared(e.getVictim().getLocation()) <= 25.0;
                
                if (inRange && (e.getType() == DeepwitherDamageEvent.DamageType.PROJECTILE || e.getType() == DeepwitherDamageEvent.DamageType.MAGIC)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBloodSurgeHit(DeepwitherDamageEvent e) {
        if (!(e.getAttacker() instanceof Player attacker)) return;
        
        // クールダウンが不十分な場合は発動しない
        if (!e.isFullCharge()) return;

        // 物理または遠距離攻撃（プロジェクタイル）のヒット時のみ発動
        if (e.getType() != DeepwitherDamageEvent.DamageType.PHYSICAL &&
            e.getType() != DeepwitherDamageEvent.DamageType.PROJECTILE) return;

        com.lunar_prototype.deepwither.api.skill.aura.AuraManager auraManager = Deepwither.getInstance().getAuraManager();
        com.lunar_prototype.deepwither.api.skill.aura.AuraManager.AuraInstance aura = auraManager.getAuraInstance(attacker, "blood_surge");
        if (aura == null) return;

        // --- 回数制限チェック ---
        Integer remaining = aura.getMetadata("remaining_hits");
        if (remaining == null || remaining <= 0) {
            auraManager.removeAura(attacker, "blood_surge");
            return;
        }

        LivingEntity victim = e.getVictim();
        if (victim == null || victim.isDead()) return;

        // --- 回数消費 ---
        int nextValue = remaining - 1;
        aura.getAllMetadata().put("remaining_hits", nextValue);
        if (nextValue <= 0) {
            auraManager.removeAura(attacker, "blood_surge");
        }

        // [FourConsecutiveAttacksSkill]
        com.lunar_prototype.deepwither.api.skill.aura.AuraManager.AuraInstance combo = auraManager.getAuraInstance(attacker, "four_consecutive_attacks");
        if (combo != null) {
            com.lunar_prototype.deepwither.api.skill.FourConsecutiveAttacksSkill.triggerHit(attacker, e.getVictim());
        }
        double healAmount = e.getDamage() * 0.3;
        Deepwither.getInstance().getStatManager().heal(attacker, healAmount);

        // --- 2. 演出 ---
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
        
        victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.2);
        victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1.2, 0), 5, 0.2, 0.2, 0.2, 0.1, Bukkit.createBlockData(Material.REDSTONE_BLOCK));
    }

    /**
     * Blood Reversal Field (反転血界) デバフ処理。
     * 攻撃者に blood_reversal_field オーラがある場合、
     * 対象との距離が半径以内なら攻撃者自身の最大HPの20%を自傷する。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBloodReversalField(DeepwitherDamageEvent e) {
        if (!(e.getAttacker() instanceof Player attacker)) return;
        LivingEntity victim = e.getVictim();
        if (victim == null || victim.isDead()) return;
        // 自傷ダメージは対象外
        if (victim.equals(attacker)) return;

        com.lunar_prototype.deepwither.api.skill.aura.AuraManager auraManager = Deepwither.getInstance().getAuraManager();
        com.lunar_prototype.deepwither.api.skill.aura.AuraManager.AuraInstance aura =
                auraManager.getAuraInstance(attacker, "blood_reversal_field");
        if (aura == null) return;

        // 距離チェック
        Double radius = aura.getMetadata("target_radius");
        if (radius == null) radius = com.lunar_prototype.deepwither.api.skill.BloodReversalFieldSkill.TARGET_RADIUS;
        if (attacker.getLocation().distanceSquared(victim.getLocation()) > radius * radius) return;

        // 最大HPの20%自傷 (True Damage)
        Double selfDmgPct = aura.getMetadata("self_damage_percent");
        if (selfDmgPct == null) selfDmgPct = com.lunar_prototype.deepwither.api.skill.BloodReversalFieldSkill.SELF_DAMAGE_PERCENT;

        double maxHp = Deepwither.getInstance().getStatManager().getActualMaxHealth(attacker);
        double selfDamage = maxHp * selfDmgPct;

        com.lunar_prototype.deepwither.core.damage.DamageContext selfCtx =
                new com.lunar_prototype.deepwither.core.damage.DamageContext(
                        null, attacker, com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent.DamageType.MAGIC, selfDamage);
        selfCtx.setTrueDamage(true);
        Deepwither.getInstance().getDamageProcessor().process(selfCtx);

        // 演出
        attacker.getWorld().spawnParticle(Particle.DUST, attacker.getLocation().add(0, 1, 0),
                12, 0.3, 0.5, 0.3, 0,
                new Particle.DustOptions(org.bukkit.Color.RED, 1.8f));
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 0.5f);
    }




    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWitherEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (e.getModifiedType() != PotionEffectType.WITHER) return;
        
        // モブの攻撃（Wither Skeleton等）によるウィザー付与のみを防止
        if (e.getCause() == EntityPotionEffectEvent.Cause.ATTACK) {
            e.setCancelled(true);
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerReceivingDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (e.isCancelled()) return;
        if (isInvulnerable(player)) {
            e.setCancelled(true);
            return;
        }

        StatMap defenderStats = statManager.getTotalStats(player);
        double rawDamage = e.getDamage();
        LivingEntity attacker = null;

        if (e instanceof EntityDamageByEntityEvent ev) {
            if (ev.getDamager() instanceof LivingEntity le) attacker = le;
            else if (ev.getDamager() instanceof Projectile p && p.getShooter() instanceof LivingEntity le) attacker = le;
            if (attacker instanceof Player) return;
            if (attacker != null && (attacker.getHealth() <= 0 || !attacker.isValid() || attacker.isDead())) {
                attacker.remove();
                iFrameEndTimes.remove(attacker.getUniqueId());
                e.setCancelled(true);
                return;
            }
        }

        List<String> traits = getMobTraits(attacker);
        if (attacker != null && traits.contains("BERSERK")) {
            double maxHp = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (attacker.getHealth() / maxHp <= 0.5) rawDamage *= 1.5;
        }

        double finalDamage = rawDamage;
        DeepwitherDamageEvent.DamageType damageType = DeepwitherDamageEvent.DamageType.PHYSICAL;
        boolean isEnvironmental = false;

        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            damageType = DeepwitherDamageEvent.DamageType.MAGIC;
        } else if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            damageType = DeepwitherDamageEvent.DamageType.ENVIRONMENTAL;
            isEnvironmental = true;
            double fallRes = defenderStats.getFinal(StatType.DROP_RESISTANCE);
            double reduction = Math.max(0, Math.min(fallRes / 100.0, 1.0));
            double blocked = rawDamage * reduction;
            finalDamage = rawDamage - blocked;
            if (blocked > 0) {
                uiManager.of(player).message(PlayerSettingsManager.SettingType.SHOW_MITIGATION,
                        Component.text("落下耐性！ ", NamedTextColor.AQUA)
                                .append(Component.text("軽減: ", NamedTextColor.GRAY))
                                .append(Component.text(Math.round(blocked), NamedTextColor.GREEN))
                                .append(Component.text(" (" + Math.round(finalDamage) + "被弾)", NamedTextColor.RED)));
            }
        } else {
            finalDamage = (attacker instanceof Mob) ? applyMobCritLogic(attacker, rawDamage, player) : rawDamage;
        }

        if (attacker != null) {
            if (traits.contains("MANA_LEECH")) Deepwither.getInstance().getManaManager().get(player.getUniqueId()).consume(2);
            if (traits.contains("DISRUPTIVE")) player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 1));
        }

        e.setDamage(0.0);
        iFrameEndTimes.put(player.getUniqueId(), System.currentTimeMillis() + DAMAGE_I_FRAME_MS);
        
        DamageContext context = new DamageContext(attacker, player, damageType, finalDamage);
        if (isEnvironmental) context.setTrueDamage(true);
        if (traits.contains("PIERCING")) context.setDefenseBypassPercent(50.0);
        Deepwither.getInstance().getDamageProcessor().process(context);
    }

    private List<String> getMobTraits(LivingEntity entity) {
        if (entity == null) return Collections.emptyList();
        String data = entity.getPersistentDataContainer().get(new NamespacedKey(Deepwither.getInstance(), "mob_traits"), PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return Collections.emptyList();
        return Arrays.asList(data.split(","));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onGhostEntityCheck(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity target) || target instanceof Player) return;
        if (target.getHealth() <= 0 || target.isDead() || !target.isValid()) {
            iFrameEndTimes.remove(target.getUniqueId());
            Bukkit.getScheduler().runTask(Deepwither.getInstance(), () -> {
                if (target.isValid()) target.remove();
            });
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        iFrameEndTimes.remove(e.getEntity().getUniqueId());
    }

    public StatMap getDefenderStats(LivingEntity entity) {
        if (entity instanceof Player p) return statManager.getTotalStats(p);
        return new StatMap();
    }

    public boolean isInvulnerable(LivingEntity entity) {
        return System.currentTimeMillis() < iFrameEndTimes.getOrDefault(entity.getUniqueId(), 0L);
    }

    public double applyMobCritLogic(LivingEntity attacker, double damage, Player target) {
        if (DamageCalculator.rollChance(10)) {
            damage *= 1.5;
            Location loc = target.getLocation().add(0, 1.2, 0);
            loc.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
            uiManager.of(target).combatAction("ENEMY CRITICAL!!", NamedTextColor.DARK_RED);
            uiManager.of(target).message(PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, Component.text("敵のクリティカル！", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        }
        return damage;
    }



    private boolean isPvPPrevented(Player attacker, LivingEntity target) {
        if (!target.getWorld().getName().equals(attacker.getWorld().getName())) return false;
        if (!(target instanceof Player)) return false;

        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(attacker.getLocation()));
            
            // WorldGuardのPVPフラグがDENY（不許可）に設定されている場合のみ阻止する
            if (!set.testState(null, com.sk89q.worldguard.protection.flags.Flags.PVP)) {
                attacker.sendMessage(Component.text("この区域ではPvPが禁止されています。", NamedTextColor.RED));
                return true;
            }
            return false;
        } catch (NoClassDefFoundError ex) { return false; }
    }



    @EventHandler
    public void onRegain(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && !e.isCancelled()) {
            double amount = e.getAmount();
            e.setCancelled(true);
            statManager.heal(p, amount);
        }
    }

    public StatType getWeaponStatType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(ItemFactory.ITEM_TYPE_KEY, PersistentDataType.STRING)) return null;

        String category = pdc.get(ItemFactory.ITEM_TYPE_KEY, PersistentDataType.STRING);
        if (category == null) return null;

        if (category.contains("鎌")) return StatType.SCYTHE_DAMAGE;
        if (category.contains("大剣")) return StatType.GREATSWORD_DAMAGE;
        if (category.contains("槍")) return StatType.SPEAR_DAMAGE;
        if (category.contains("斧")) return StatType.AXE_DAMAGE;
        if (category.contains("メイス")) return StatType.MACE_DAMAGE;
        if (category.contains("剣")) return StatType.SWORD_DAMAGE;
        if (category.contains("マチェット")) return StatType.MACHETE_DAMAGE;
        if (category.contains("ハンマー")) return StatType.HAMMER_DAMAGE;
        if (category.contains("ハルバード")) return StatType.HALBERD_DAMAGE;
        
        return null;
    }
    
    // Legacy support for other classes that might still call these
    @Deprecated public double applyDefense(double damage, double defense, double divisor) { return DamageCalculator.applyDefense(damage, defense, divisor); }
    @Deprecated public boolean rollChance(double chance) { return DamageCalculator.rollChance(chance); }
    @Deprecated public double calculateDistanceMultiplier(Player player, LivingEntity target) { return DamageCalculator.calculateDistanceMultiplier(player.getLocation(), target.getLocation()); }
    @Deprecated public void finalizeDamage(LivingEntity target, double damage, LivingEntity source, DeepwitherDamageEvent.DamageType type) {
        DamageContext ctx = new DamageContext(source, target, type, damage);
        Deepwither.getInstance().getDamageProcessor().process(ctx);
    }
    @Deprecated public void applyCustomDamage(LivingEntity target, double damage, Player damager) {
        DamageContext ctx = new DamageContext(damager, target, DeepwitherDamageEvent.DamageType.PHYSICAL, damage);
        Deepwither.getInstance().getDamageProcessor().process(ctx);
    }
}
