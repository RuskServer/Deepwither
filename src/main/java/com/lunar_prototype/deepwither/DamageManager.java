package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.core.damage.DamageCalculator;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.core.damage.DamageProcessor;
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
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

@DependsOn({StatManager.class, PlayerSettingsManager.class, ChargeManager.class, ManaManager.class, DamageProcessor.class, WeaponMechanicManager.class})
public class DamageManager implements Listener, IManager {

    private final IStatManager statManager;
    private final Map<UUID, Long> onHitCooldowns = new HashMap<>();
    private final JavaPlugin plugin;

    private static final Set<String> UNDEAD_MOB_IDS = Set.of("melee_skeleton", "ranged_skeleton", "melee_zombi");
    private static final double DEFENSE_DIVISOR = 100.0;
    private static final double PLAYER_DEFENSE_DIVISOR = 500.0;

    private final Map<UUID, Long> iFrameEndTimes = new HashMap<>();
    private static final long DAMAGE_I_FRAME_MS = 300;

    private final Map<UUID, Integer> comboCounts = new HashMap<>();
    private final Map<UUID, Long> lastComboHitTimes = new HashMap<>();
    private static final long COMBO_TIMEOUT_MS = 2000;

    private static final long COOLDOWN_IGNORE_MS = 300;
    private final Map<UUID, Long> lastSpecialAttackTime = new HashMap<>();

    private final PlayerSettingsManager settingsManager;

    public DamageManager(JavaPlugin plugin, IStatManager statManager, PlayerSettingsManager settingsManager) {
        this.plugin = plugin;
        this.statManager = statManager;
        this.settingsManager = settingsManager;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    public void sendLog(Player player, PlayerSettingsManager.SettingType type, Component message) {
        if (settingsManager.isEnabled(player, type)) {
            player.sendMessage(message);
        }
    }

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
        
        Deepwither dw = Deepwither.getInstance();
        DamageProcessor processor = dw.getDamageProcessor();
        if (processor.isProcessing(attacker.getUniqueId())) return;
        
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
        StatMap defenderStats = getDefenderStats(targetLiving);
        StatType weaponType = getWeaponStatType(item);
        
        DeepwitherDamageEvent.DamageType damageType = isProjectile ? DeepwitherDamageEvent.DamageType.PROJECTILE : DeepwitherDamageEvent.DamageType.PHYSICAL;
        
        double attackPowerFlat = attackerStats.getFlat(isProjectile ? StatType.PROJECTILE_DAMAGE : StatType.ATTACK_DAMAGE);
        double attackPowerPercent = attackerStats.getPercent(isProjectile ? StatType.PROJECTILE_DAMAGE : StatType.ATTACK_DAMAGE);
        double weaponFlat = (weaponType != null) ? attackerStats.getFlat(weaponType) : 0;
        double weaponPercent = (weaponType != null) ? attackerStats.getPercent(weaponType) : 0;

        double baseDamage = (e.getDamage() + attackPowerFlat + weaponFlat);
        double totalMultiplier = 1.0 + ((attackPowerPercent + weaponPercent) / 100.0);
        baseDamage *= totalMultiplier;

        DamageContext context = new DamageContext(attacker, targetLiving, damageType, baseDamage);
        context.setProjectile(isProjectile);
        context.setWeapon(item);
        context.setWeaponStatType(weaponType);

        // 2. クリティカル判定
        if (DamageCalculator.rollChance(attackerStats.getFinal(StatType.CRIT_CHANCE))) {
            context.setCrit(true);
            context.setFinalDamage(context.getFinalDamage() * (attackerStats.getFinal(StatType.CRIT_DAMAGE) / 100.0));
        }

        // 3. 距離補正 (遠距離)
        if (isProjectile) {
            double distMult = DamageCalculator.calculateDistanceMultiplier(attacker.getLocation(), targetLiving.getLocation());
            context.setDistanceMultiplier(distMult);
            context.setFinalDamage(context.getFinalDamage() * distMult);
        }

        // 4. 防御力計算
        double defenseDivisor = (targetLiving instanceof Player) ? PLAYER_DEFENSE_DIVISOR : DEFENSE_DIVISOR;
        context.setFinalDamage(DamageCalculator.applyDefense(context.getFinalDamage(), defenderStats.getFinal(StatType.DEFENSE), defenseDivisor));

        // 5. 特殊効果 (コンボ、クールダウン)
        applyComboAndCooldown(attacker, context);

        // 6. 盾防御
        if (targetLiving instanceof Player playerVictim && playerVictim.isBlocking()) {
            handleShieldBlock(attacker, playerVictim, context, defenderStats);
        }

        // 7. 最終ダメージ確定とエフェクト
        double finalDamageValue = Math.max(0.1, context.getFinalDamage());
        context.setFinalDamage(finalDamageValue);
        
        e.setDamage(0.0);
        if (isProjectile && projectileEntity != null) projectileEntity.remove();

        playHitEffects(attacker, targetLiving, context);

        // 8. ダメージ適用
        iFrameEndTimes.put(targetLiving.getUniqueId(), System.currentTimeMillis() + DAMAGE_I_FRAME_MS);
        processor.process(context);

        // 9. 武器メカニクスとOn-Hit
        dw.getWeaponMechanicManager().handleWeaponMechanics(context, processor);
        tryTriggerOnHitSkill(attacker, targetLiving, item);

        // 10. 吸収・状態異常
        handlePostDamageEffects(attacker, targetLiving, attackerStats, context.getFinalDamage());
    }

    private void applyComboAndCooldown(Player attacker, DamageContext context) {
        long currentTime = System.currentTimeMillis();
        SkillData skillData = Deepwither.getInstance().getSkilltreeManager().load(attacker.getUniqueId());
        
        if (skillData.hasSpecialEffect("COMBO_DAMAGE")) {
            double comboValue = skillData.getSpecialEffectValue("COMBO_DAMAGE");
            long lastHit = lastComboHitTimes.getOrDefault(attacker.getUniqueId(), 0L);
            int currentCombo = comboCounts.getOrDefault(attacker.getUniqueId(), 0);

            if (currentTime - lastHit > COMBO_TIMEOUT_MS) currentCombo = 0;

            double comboMultiplier = 1.0 + (currentCombo * (comboValue / 100.0));
            context.setFinalDamage(context.getFinalDamage() * comboMultiplier);

            if (currentCombo > 0) {
                sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                        Component.text("コンボ継続中! x" + currentCombo, NamedTextColor.YELLOW)
                                .append(Component.text(" (+" + Math.round((comboMultiplier - 1.0) * 100) + "%)", NamedTextColor.GOLD)));
            }
            comboCounts.put(attacker.getUniqueId(), Math.min(currentCombo + 1, 5));
            lastComboHitTimes.put(attacker.getUniqueId(), currentTime);
        }

        if (!context.isProjectile()) {
            long lastAttack = lastSpecialAttackTime.getOrDefault(attacker.getUniqueId(), 0L);
            boolean ignoreCooldown = currentTime < lastAttack + COOLDOWN_IGNORE_MS;
            lastSpecialAttackTime.put(attacker.getUniqueId(), currentTime);

            if (!ignoreCooldown) {
                float cooldown = attacker.getAttackCooldown();
                if (cooldown < 1.0f) {
                    double reduced = context.getFinalDamage() * (1.0 - cooldown);
                    context.setFinalDamage(context.getFinalDamage() * cooldown);
                    sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                            Component.text("攻撃クールダウン！ ", NamedTextColor.RED)
                                    .append(Component.text("-" + Math.round(reduced), NamedTextColor.RED))
                                    .append(Component.text(" ダメージ ", NamedTextColor.GRAY))
                                    .append(Component.text("(" + Math.round(context.getFinalDamage()) + ")", NamedTextColor.GREEN)));
                }
            }
        }
    }

    private void handleShieldBlock(Player attacker, Player victim, DamageContext context, StatMap defenderStats) {
        Vector toAttackerVec = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).normalize();
        Vector defenderLookVec = victim.getLocation().getDirection().normalize();
        if (toAttackerVec.dot(defenderLookVec) > 0.5) {
            double blockRate = Math.max(0.0, Math.min(defenderStats.getFinal(StatType.SHIELD_BLOCK_RATE), 1.0));
            double blockedDamage = context.getFinalDamage() * blockRate;
            context.setFinalDamage(context.getFinalDamage() - blockedDamage);
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
            sendLog(victim, PlayerSettingsManager.SettingType.SHOW_MITIGATION,
                    Component.text("盾防御！ ", NamedTextColor.AQUA)
                            .append(Component.text("軽減: ", NamedTextColor.GRAY))
                            .append(Component.text(Math.round(blockedDamage), NamedTextColor.GREEN))
                            .append(Component.text(" (" + Math.round(context.getFinalDamage()) + "被弾)", NamedTextColor.RED)));
        }
    }

    private void playHitEffects(Player attacker, LivingEntity victim, DamageContext context) {
        if (context.isCrit()) {
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Component.text("クリティカル！ ", NamedTextColor.GOLD, TextDecoration.BOLD).append(Component.text("+" + Math.round(context.getFinalDamage()), NamedTextColor.RED)));
            Location hitLoc = victim.getLocation().add(0, 1.2, 0);
            World world = hitLoc.getWorld();
            world.spawnParticle(Particle.FLASH, hitLoc, 1, 0, 0, 0, 0, Color.WHITE);
            world.spawnParticle(Particle.SONIC_BOOM, hitLoc, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.LAVA, hitLoc, 8, 0.4, 0.4, 0.4, 0.1);
            world.spawnParticle(Particle.CRIT, hitLoc, 30, 0.5, 0.5, 0.5, 0.5);
            world.spawnParticle(Particle.LARGE_SMOKE, hitLoc, 15, 0.2, 0.2, 0.2, 0.05);
            world.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
            world.playSound(hitLoc, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.5f);
            world.playSound(hitLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.6f);
            world.playSound(hitLoc, Sound.ITEM_TRIDENT_HIT, 1.0f, 0.7f);
        } else if (context.isProjectile()) {
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE,
                    Component.text("遠距離命中 ", NamedTextColor.GRAY)
                            .append(Component.text("+" + Math.round(context.getFinalDamage()), NamedTextColor.RED))
                            .append(Component.text(" [" + String.format("%.0f%%", context.getDistanceMultiplier() * 100) + "]", NamedTextColor.YELLOW)));
        }
    }

    private void handlePostDamageEffects(Player attacker, LivingEntity victim, StatMap attackerStats, double damage) {
        double lifeSteal = attackerStats.getFinal(StatType.LIFESTEAL);
        if (lifeSteal > 0) {
            double healAmount = damage * (lifeSteal / 100.0);
            if (healAmount > 0) {
                statManager.heal(attacker, healAmount);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.5f, 1.2f);
            }
        }

        if (DamageCalculator.rollChance(attackerStats.getFinal(StatType.BLEED_CHANCE))) applyBleed(victim, attacker);
        if (DamageCalculator.rollChance(attackerStats.getFinal(StatType.FREEZE_CHANCE))) applyFreeze(victim);
        if (DamageCalculator.rollChance(attackerStats.getFinal(StatType.AOE_CHANCE))) applyAoE(victim, attacker, damage);
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

        double defenseMultiplier = traits.contains("PIERCING") ? 0.5 : 1.0;
        double finalDamage;
        DeepwitherDamageEvent.DamageType damageType = DeepwitherDamageEvent.DamageType.PHYSICAL;

        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            double res = defenderStats.getFinal(StatType.MAGIC_RESIST) * defenseMultiplier;
            finalDamage = DamageCalculator.applyDefense(rawDamage, res, 100.0);
            damageType = DeepwitherDamageEvent.DamageType.MAGIC;
        } else if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            double fallRes = defenderStats.getFinal(StatType.DROP_RESISTANCE);
            double reduction = Math.max(0, Math.min(fallRes / 100.0, 1.0));
            double blocked = rawDamage * reduction;
            finalDamage = rawDamage - blocked;
            damageType = DeepwitherDamageEvent.DamageType.ENVIRONMENTAL;
            if (blocked > 0) {
                sendLog(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION,
                        Component.text("落下耐性！ ", NamedTextColor.AQUA)
                                .append(Component.text("軽減: ", NamedTextColor.GRAY))
                                .append(Component.text(Math.round(blocked), NamedTextColor.GREEN))
                                .append(Component.text(" (" + Math.round(finalDamage) + "被弾)", NamedTextColor.RED)));
            }
        } else {
            double currentDamage = (attacker instanceof Mob) ? applyMobCritLogic(attacker, rawDamage, player) : rawDamage;
            double def = defenderStats.getFinal(StatType.DEFENSE) * defenseMultiplier;
            finalDamage = DamageCalculator.applyDefense(currentDamage, def, PLAYER_DEFENSE_DIVISOR);

            if (player.isBlocking() && attacker != null) {
                Vector toAttacker = attacker.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                if (toAttacker.dot(player.getLocation().getDirection().normalize()) > 0.5) {
                    double blocked = finalDamage * Math.max(0, Math.min(defenderStats.getFinal(StatType.SHIELD_BLOCK_RATE), 1.0));
                    finalDamage -= blocked;
                    player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                }
            }
        }

        if (attacker != null) {
            if (traits.contains("MANA_LEECH")) Deepwither.getInstance().getManaManager().get(player.getUniqueId()).consume(2);
            if (traits.contains("DISRUPTIVE")) player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 1));
        }

        e.setDamage(0.0);
        iFrameEndTimes.put(player.getUniqueId(), System.currentTimeMillis() + DAMAGE_I_FRAME_MS);
        
        DamageContext context = new DamageContext(attacker, player, damageType, finalDamage);
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
        if (DamageCalculator.rollChance(20)) {
            damage *= 1.5;
            Location loc = target.getLocation().add(0, 1.2, 0);
            loc.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
            sendLog(target, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, Component.text("敵のクリティカル！", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        }
        return damage;
    }

    private void applyBleed(LivingEntity target, Player attacker) {
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (target.isDead() || !target.isValid() || count >= 5) {
                    this.cancel();
                    return;
                }
                DamageContext bleedContext = new DamageContext(attacker, target, DeepwitherDamageEvent.DamageType.PHYSICAL, 1.0);
                Deepwither.getInstance().getDamageProcessor().process(bleedContext);
                target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 10, 0.2, 0.5, 0.2, Bukkit.createBlockData(Material.REDSTONE_BLOCK));
                count++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 20L, 20L);
        sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, Component.text("出血付与！", NamedTextColor.DARK_RED));
    }

    private void applyFreeze(LivingEntity target) {
        target.setFreezeTicks(140);
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 15, 0.5, 1.0, 0.5, 0.05);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
    }

    private void applyAoE(LivingEntity mainTarget, Player attacker, double damage) {
        double splashDamage = damage * 0.5;
        mainTarget.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, mainTarget.getLocation().add(0, 1, 0), 1);
        mainTarget.getWorld().playSound(mainTarget.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        for (Entity e : mainTarget.getNearbyEntities(3, 3, 3)) {
            if (e instanceof LivingEntity living && !e.equals(attacker) && !e.equals(mainTarget)) {
                DamageContext aoeContext = new DamageContext(attacker, living, DeepwitherDamageEvent.DamageType.PHYSICAL, splashDamage);
                Deepwither.getInstance().getDamageProcessor().process(aoeContext);
            }
        }
        sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, Component.text("拡散ヒット！", NamedTextColor.YELLOW));
    }

    public boolean handleUndeadDamage(Player player, LivingEntity target) {
        String mobId = MythicBukkit.inst().getMobManager().getActiveMob(target.getUniqueId()).get().getType().getInternalName();
        if (mobId != null && UNDEAD_MOB_IDS.contains(mobId)) {
            double damage = target.getHealth() * 0.5;
            sendLog(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Component.text("聖特攻！ ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD).append(Component.text("アンデッドに50%ダメージ！", NamedTextColor.WHITE)));
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
            DamageContext holyContext = new DamageContext(player, target, DeepwitherDamageEvent.DamageType.MAGIC, damage);
            Deepwither.getInstance().getDamageProcessor().process(holyContext);
            return true;
        }
        return false;
    }

    public void handleLifesteal(Player player, LivingEntity target, double finalDamage) {
        double heal = target.getMaxHealth() * (finalDamage / 100.0 / 100.0);
        heal = Math.min(heal, player.getMaxHealth() * 0.20);
        statManager.heal(player, heal);
        sendLog(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Component.text("LS！ ", NamedTextColor.GREEN, TextDecoration.BOLD).append(Component.text(String.format("%.1f", heal) + " 回復", NamedTextColor.DARK_GREEN)));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
    }

    private boolean isPvPPrevented(Player attacker, LivingEntity target) {
        if (!target.getWorld().getName().equals(attacker.getWorld().getName())) return false;
        if (!(target instanceof Player)) return false;
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(attacker.getLocation()));
            if (!set.testState(null, com.sk89q.worldguard.protection.flags.Flags.PVP)) {
                attacker.sendMessage(Component.text("この区域ではPvPが禁止されています。", NamedTextColor.RED));
                return true;
            }
            return false;
        } catch (NoClassDefFoundError ex) { return false; }
    }

    private void tryTriggerOnHitSkill(Player attacker, LivingEntity target, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        Double chance = container.get(ItemLoader.SKILL_CHANCE_KEY, PersistentDataType.DOUBLE);
        Integer cooldown = container.get(ItemLoader.SKILL_COOLDOWN_KEY, PersistentDataType.INTEGER);
        String skillId = container.get(ItemLoader.SKILL_ID_KEY, PersistentDataType.STRING);
        if (chance == null || skillId == null) return;
        long lastTrigger = onHitCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = (cooldown != null) ? cooldown * 1000L : 0L;
        if (currentTime < lastTrigger + cooldownMillis) return;
        if (Math.random() * 100 <= chance) {
            MythicBukkit.inst().getAPIHelper().castSkill(attacker.getPlayer(), skillId);
            onHitCooldowns.put(attacker.getUniqueId(), currentTime);
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                    Component.text("[On-Hit] スキル「", NamedTextColor.GREEN)
                            .append(Component.text(skillId, NamedTextColor.AQUA))
                            .append(Component.text("」を発動！", NamedTextColor.GREEN)));
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

    public StatType getWeaponStatType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        List<Component> lore = item.lore();
        if (lore == null) return null;
        for (Component lineComp : lore) {
            String line = PlainTextComponentSerializer.plainText().serialize(lineComp);
            if (!line.contains("カテゴリ:")) continue;
            if (line.contains("鎌")) return StatType.SCYTHE_DAMAGE;
            if (line.contains("大剣")) return StatType.GREATSWORD_DAMAGE;
            if (line.contains("槍")) return StatType.SPEAR_DAMAGE;
            if (line.contains("斧")) return StatType.AXE_DAMAGE;
            if (line.contains("メイス")) return StatType.MACE_DAMAGE;
            if (line.contains("剣")) return StatType.SWORD_DAMAGE;
            if (line.contains("マチェット")) return StatType.MACHETE_DAMAGE;
            if (line.contains("ハンマー")) return StatType.HAMMER_DAMAGE;
            if (line.contains("ハルバード")) return StatType.HALBERD_DAMAGE;
        }
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