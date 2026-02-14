package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.PlayerSettingsManager;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.event.onPlayerRecevingDamageEvent;
import com.lunar_prototype.deepwither.api.stat.IStatManager;
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

    private static final Set<String> UNDEAD_MOB_IDS = Set.of("melee_skeleton", "ranged_skeleton", "melee_zombi");
    private static final double MAGIC_DEFENSE_DIVISOR = 100.0;
    private static final double DEFENSE_DIVISOR = 100.0;
    private static final double HEAVY_DEFENSE_DIVISOR = 500.0;

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

    private final Map<UUID, Map<UUID, MagicHitInfo>> magicHitMap = new HashMap<>();

    private static class MagicHitInfo {
        int hitCount;
        long lastHitTime;

        MagicHitInfo(int hitCount, long lastHitTime) {
            this.hitCount = hitCount;
            this.lastHitTime = lastHitTime;
        }
    }

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

        if (isInvulnerable(targetLiving)) {
            e.setCancelled(true);
            return;
        }

        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;

        if (targetLiving instanceof Player && isPvPPrevented(attacker, targetLiving)) {
            e.setCancelled(true);
            return;
        }

        StatMap attackerStats = statManager.getTotalStats(attacker);
        StatMap defenderStats = getDefenderStats(targetLiving);

        StatType weaponType = getWeaponStatType(attacker.getInventory().getItemInMainHand());

        double attackPowerFlat = attackerStats.getFlat(isProjectile ? StatType.PROJECTILE_DAMAGE : StatType.ATTACK_DAMAGE);
        double attackPowerPercent = attackerStats.getPercent(isProjectile ? StatType.PROJECTILE_DAMAGE : StatType.ATTACK_DAMAGE);

        double weaponFlat = (weaponType != null) ? attackerStats.getFlat(weaponType) : 0;
        double weaponPercent = (weaponType != null) ? attackerStats.getPercent(weaponType) : 0;

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

        SkilltreeManager.SkillData skillData = Deepwither.getInstance().getSkilltreeManager().load(attacker.getUniqueId());
        if (skillData.hasSpecialEffect("COMBO_DAMAGE")) {
            double comboValue = skillData.getSpecialEffectValue("COMBO_DAMAGE");
            long lastHit = lastComboHitTimes.getOrDefault(attacker.getUniqueId(), 0L);
            int currentCombo = comboCounts.getOrDefault(attacker.getUniqueId(), 0);

            if (currentTime - lastHit > COMBO_TIMEOUT_MS) currentCombo = 0;

            double comboMultiplier = 1.0 + (currentCombo * (comboValue / 100.0));
            finalDamage *= comboMultiplier;

            if (currentCombo > 0) {
                sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                        Component.text("コンボ継続中! x" + currentCombo, NamedTextColor.YELLOW)
                                .append(Component.text(" (+" + Math.round((comboMultiplier - 1.0) * 100) + "%)", NamedTextColor.GOLD)));
            }

            comboCounts.put(attacker.getUniqueId(), Math.min(currentCombo + 1, 5));
            lastComboHitTimes.put(attacker.getUniqueId(), currentTime);
        }

        boolean ignoreAttackCooldown = false;
        if (!isProjectile) {
            long lastAttack = lastSpecialAttackTime.getOrDefault(attacker.getUniqueId(), 0L);
            if (currentTime < lastAttack + COOLDOWN_IGNORE_MS) ignoreAttackCooldown = true;
            lastSpecialAttackTime.put(attacker.getUniqueId(), currentTime);
        }

        if (!isProjectile && !ignoreAttackCooldown) {
            float cooldown = attacker.getAttackCooldown();
            if (cooldown < 1.0f) {
                double reduced = finalDamage * (1.0 - cooldown);
                finalDamage *= cooldown;
                sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                        Component.text("攻撃クールダウン！ ", NamedTextColor.RED)
                                .append(Component.text("-" + Math.round(reduced), NamedTextColor.RED))
                                .append(Component.text(" ダメージ ", NamedTextColor.GRAY))
                                .append(Component.text("(" + Math.round(finalDamage) + ")", NamedTextColor.GREEN)));
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
                        applyCustomDamage(livingTarget, sweepDamage, attacker);
                        livingTarget.setVelocity(attacker.getLocation().getDirection().multiply(0.3).setY(0.1));
                    }
                }
            }
        }

        ChargeManager chargeManager = Deepwither.getInstance().getChargeManager();
        String chargedType = chargeManager.consumeCharge(attacker.getUniqueId());
        ItemStack item = attacker.getInventory().getItemInMainHand();
        String type = item.getItemMeta().getPersistentDataContainer().get(ItemLoader.CHARGE_ATTACK_KEY, PersistentDataType.STRING);
        if (type != null && chargedType != null && chargedType.equals("hammer")) {
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
                    if (!nearby.equals(targetLiving)) applyCustomDamage(living, areaDamage, attacker);
                    Vector kb = living.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(1.5).setY(0.6);
                    living.setVelocity(kb);
                }
            }
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Component.text("CRASH!! ", NamedTextColor.RED, TextDecoration.BOLD).append(Component.text("ハンマーの溜め攻撃を叩き込んだ！", NamedTextColor.GOLD)));
        }

        if (targetLiving instanceof Player defenderPlayer && defenderPlayer.isBlocking()) {
            Vector toAttackerVec = attacker.getLocation().toVector().subtract(defenderPlayer.getLocation().toVector()).normalize();
            Vector defenderLookVec = defenderPlayer.getLocation().getDirection().normalize();
            if (toAttackerVec.dot(defenderLookVec) > 0.5) {
                if (e.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) e.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0);
                double blockRate = defenderStats.getFinal(StatType.SHIELD_BLOCK_RATE);
                blockRate = Math.max(0.0, Math.min(blockRate, 1.0));
                double blockedDamage = finalDamage * blockRate;
                finalDamage -= blockedDamage;
                defenderPlayer.getWorld().playSound(defenderPlayer.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                sendLog(defenderPlayer, PlayerSettingsManager.SettingType.SHOW_MITIGATION,
                        Component.text("盾防御！ ", NamedTextColor.AQUA)
                                .append(Component.text("軽減: ", NamedTextColor.GRAY))
                                .append(Component.text(Math.round(blockedDamage), NamedTextColor.GREEN))
                                .append(Component.text(" (" + Math.round(finalDamage) + "被弾)", NamedTextColor.RED)));
            }
        }

        finalDamage = Math.max(0.1, finalDamage);
        e.setDamage(0.0);
        if (isProjectile) projectileEntity.remove();

        if (isCrit) {
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Component.text("クリティカル！ ", NamedTextColor.GOLD, TextDecoration.BOLD).append(Component.text("+" + Math.round(finalDamage), NamedTextColor.RED)));
            Location hitLoc = targetLiving.getLocation().add(0, 1.2, 0);
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
        } else if (isProjectile) {
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE,
                    Component.text("遠距離命中 ", NamedTextColor.GRAY)
                            .append(Component.text("+" + Math.round(finalDamage), NamedTextColor.RED))
                            .append(Component.text(" [" + String.format("%.0f%%", distMult * 100) + "]", NamedTextColor.YELLOW)));
        }

        finalizeDamage(targetLiving, finalDamage, attacker, isProjectile ? DeepwitherDamageEvent.DamageType.PROJECTILE : DeepwitherDamageEvent.DamageType.PHYSICAL);
        tryTriggerOnHitSkill(attacker, targetLiving, attacker.getInventory().getItemInMainHand());

        if (!isProjectile && isSpearWeapon(attacker.getInventory().getItemInMainHand())) {
            spawnSpearThrustEffect(attacker);
            handleSpearCleave(attacker, targetLiving, finalDamage);
        }

        double lifeSteal = attackerStats.getFinal(StatType.LIFESTEAL);
        if (lifeSteal > 0) {
            double healAmount = finalDamage * (lifeSteal / 100.0);
            if (healAmount > 0) {
                statManager.heal(attacker, healAmount);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.5f, 1.2f);
            }
        }

        if (rollChance(attackerStats.getFinal(StatType.BLEED_CHANCE))) applyBleed(targetLiving, attacker);
        if (rollChance(attackerStats.getFinal(StatType.FREEZE_CHANCE))) applyFreeze(targetLiving);
        if (rollChance(attackerStats.getFinal(StatType.AOE_CHANCE))) applyAoE(targetLiving, attacker, finalDamage);
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
        double finalDamage;
        boolean isMagic = false;
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

        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            double res = defenderStats.getFinal(StatType.MAGIC_RESIST) * defenseMultiplier;
            finalDamage = applyDefense(rawDamage, res, 100.0);
            isMagic = true;
        } else if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            double fallRes = defenderStats.getFinal(StatType.DROP_RESISTANCE);
            double reduction = Math.max(0, Math.min(fallRes / 100.0, 1.0));
            double blocked = rawDamage * reduction;
            finalDamage = rawDamage - blocked;
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
            finalDamage = applyDefense(currentDamage, def, 500.0);

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
        DeepwitherDamageEvent.DamageType damageType = isMagic ? DeepwitherDamageEvent.DamageType.MAGIC : DeepwitherDamageEvent.DamageType.PHYSICAL;
        if (attacker == null) damageType = DeepwitherDamageEvent.DamageType.ENVIRONMENTAL;
        
        finalizeDamage(player, finalDamage, attacker, damageType);
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

    public double applyDefense(double damage, double defense, double divisor) {
        double reduction = defense / (defense + divisor);
        return damage * (1.0 - reduction);
    }

    public boolean rollChance(double chance) {
        return (Math.random() * 100) + 1 <= chance;
    }

    public boolean handleUndeadDamage(Player player, LivingEntity target) {
        String mobId = MythicBukkit.inst().getMobManager().getActiveMob(target.getUniqueId()).get().getType().getInternalName();
        if (mobId != null && UNDEAD_MOB_IDS.contains(mobId)) {
            double damage = target.getHealth() * 0.5;
            sendLog(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Component.text("聖特攻！ ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD).append(Component.text("アンデッドに50%ダメージ！", NamedTextColor.WHITE)));
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
        sendLog(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Component.text("LS！ ", NamedTextColor.GREEN, TextDecoration.BOLD).append(Component.text(String.format("%.1f", heal) + " 回復", NamedTextColor.DARK_GREEN)));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
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
                        sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, Component.text("貫通！ ", NamedTextColor.YELLOW).append(Component.text("+" + Math.round(cleaveDmg), NamedTextColor.RED)));
                    }
                });
    }

    public double applyMobCritLogic(LivingEntity attacker, double damage, Player target) {
        if (rollChance(20)) {
            damage *= 1.5;
            Location loc = target.getLocation().add(0, 1.2, 0);
            loc.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
            sendLog(target, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, Component.text("敵のクリティカル！", NamedTextColor.DARK_RED, TextDecoration.BOLD));
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
            attacker.getWorld().spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
        }
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 1.4f);
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
                applyCustomDamage(target, 1.0, attacker);
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
                if (!isProcessingDamage.contains(attacker.getUniqueId())) applyCustomDamage(living, splashDamage, attacker);
            }
        }
        sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, Component.text("拡散ヒット！", NamedTextColor.YELLOW));
    }

    public void finalizeDamage(LivingEntity target, double damage, LivingEntity source, DeepwitherDamageEvent.DamageType type) {
        if (source instanceof Player attacker && target instanceof Player playerTarget) {
            if (isPvPPrevented(attacker, playerTarget)) return;
        }

        DeepwitherDamageEvent dwEvent = new DeepwitherDamageEvent(target, source, damage, type);
        Bukkit.getPluginManager().callEvent(dwEvent);
        if (dwEvent.isCancelled()) return;
        damage = dwEvent.getDamage();

        iFrameEndTimes.put(target.getUniqueId(), System.currentTimeMillis() + DAMAGE_I_FRAME_MS);

        if (target instanceof Player player) {
            processPlayerDamageWithAbsorption(player, damage, source != null ? source.getName() : "魔法/環境");
            NamedTextColor prefixColor = type == DeepwitherDamageEvent.DamageType.MAGIC ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.RED;
            String prefixText = type == DeepwitherDamageEvent.DamageType.MAGIC ? "魔法被弾！" : "物理被弾！";
            sendLog(player, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, Component.text(prefixText, prefixColor, TextDecoration.BOLD).append(Component.text(" " + Math.round(damage), NamedTextColor.RED)));
            if (source != null) Bukkit.getPluginManager().callEvent(new onPlayerRecevingDamageEvent(player, source, damage));
        } else {
            if (source instanceof Player playerSource) applyCustomDamage(target, damage, playerSource);
            else target.damage(damage, source);
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
            if (currentHealth <= damage) {
                iFrameEndTimes.remove(target.getUniqueId());
                target.setHealth(0.5);
                target.damage(100.0, damager);
                Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
                    if (target.isValid() && !target.isDead()) target.remove();
                }, 3L);
            } else {
                target.damage(damage, damager);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isProcessingDamage.remove(damager.getUniqueId());
        }
    }

    private void processPlayerDamageWithAbsorption(Player player, double damage, String sourceName) {
        if (statManager.getActualCurrentHealth(player) <= 0) return;
        double absorption = player.getAbsorptionAmount() * 10.0;
        if (absorption > 0) {
            if (damage <= absorption) {
                double newAbs = absorption - damage;
                player.setAbsorptionAmount((float) (newAbs / 10.0));
                sendLog(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION, Component.text("シールド防御: ", NamedTextColor.YELLOW).append(Component.text("-" + Math.round(damage), NamedTextColor.YELLOW)));
                return;
            } else {
                player.setAbsorptionAmount(0f);
                sendLog(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION, Component.text("シールドブレイク！", NamedTextColor.RED));
                return;
            }
        }
        double currentHp = statManager.getActualCurrentHealth(player);
        double newHp = currentHp - damage;
        statManager.setActualCurrentHealth(player, newHp);
        if (newHp <= 0) player.sendMessage(Component.text(sourceName + "に倒されました。", NamedTextColor.DARK_RED));
        else sendLog(player, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, Component.text("-" + Math.round(damage) + " HP", NamedTextColor.RED));
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
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            List<Component> lore = meta.lore();
            if (lore != null) {
                for (Component line : lore) {
                    if (PlainTextComponentSerializer.plainText().serialize(line).contains("カテゴリ:槍")) return true;
                }
            }
        }
        return false;
    }

    private boolean isSwordWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            List<Component> lore = meta.lore();
            if (lore != null) {
                for (Component line : lore) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(line);
                    if (plain.contains("カテゴリ:剣") || plain.contains("カテゴリ:大剣")) return true;
                }
            }
        }
        return false;
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
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
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
}
