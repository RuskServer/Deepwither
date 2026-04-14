package com.lunar_prototype.deepwither.core.damage;

import com.lunar_prototype.deepwither.PlayerSettingsManager;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.api.DeepwitherPartyAPI;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.event.onPlayerRecevingDamageEvent;
import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.ItemLoader;
import com.lunar_prototype.deepwither.SkillData;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffectType;

@DependsOn({StatManager.class, PlayerSettingsManager.class, com.lunar_prototype.deepwither.core.UIManager.class, com.lunar_prototype.deepwither.party.PartyManager.class})
public class DamageProcessor implements IManager {

    private final JavaPlugin plugin;
    private final IStatManager statManager;
    private final com.lunar_prototype.deepwither.core.UIManager uiManager;
    private final DeepwitherPartyAPI partyAPI;
    private final Set<UUID> isProcessingDamage = new HashSet<>();

    private final Map<UUID, Long> onHitCooldowns = new HashMap<>();
    private static final Set<String> UNDEAD_MOB_IDS = Set.of("melee_skeleton", "ranged_skeleton", "melee_zombi");
    private final Map<UUID, Integer> comboCounts = new HashMap<>();
    private final Map<UUID, Long> lastComboHitTimes = new HashMap<>();
    private static final long COMBO_TIMEOUT_MS = 1000;
    private static final long COOLDOWN_IGNORE_MS = 300;
    private final Map<UUID, Long> lastSpecialAttackTime = new HashMap<>();

    public DamageProcessor(JavaPlugin plugin, IStatManager statManager, com.lunar_prototype.deepwither.core.UIManager uiManager, DeepwitherPartyAPI partyAPI) {
        this.plugin = plugin;
        this.statManager = statManager;
        this.uiManager = uiManager;
        this.partyAPI = partyAPI;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    /**
     * ダメージ処理のメインエントリポイント
     */
    public void process(DamageContext context) {
        LivingEntity victim = context.getVictim();
        LivingEntity attacker = context.getAttacker();

        // パーティー内FF（フレンドリーファイア）防止
        if (attacker instanceof Player pAtk && victim instanceof Player pVic) {
            // 同じパーティーに所属している場合はダメージをキャンセル
            if (partyAPI.isInSameParty(pAtk, pVic)) {
                return;
            }
        }

        double damage = context.getFinalDamage();
        com.lunar_prototype.deepwither.StatMap attackerStats = null;
        
        if (attacker instanceof Player player) {
            attackerStats = statManager.getTotalStats(player);
            
            // 基礎攻撃力加算 (物理/魔法)
            if (context.isMagic()) {
                damage += attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.MAGIC_DAMAGE);
            } else {
                damage += attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.ATTACK_DAMAGE);
            }

            // 武器種別の追加ダメージ加算
            com.lunar_prototype.deepwither.StatType weaponType = context.getWeaponStatType();
            if (weaponType != null) {
                damage += attackerStats.getFlat(weaponType);
            }

            // クリティカル倍率適用
            if (context.isCrit()) {
                double critMultiplier = attackerStats.getFinal(com.lunar_prototype.deepwither.StatType.CRIT_DAMAGE) / 100.0;
                damage *= Math.max(1.0, critMultiplier);
            }

            // 距離倍率適用 (遠距離武器)
            if (context.getDistanceMultiplier() > 0) {
                damage *= context.getDistanceMultiplier();
            }
            
            // コンボとクールダウン (近接物理ダメージのみ)
            if (!context.isProjectile() && !context.isMagic()) {
                damage = applyComboAndCooldown(player, damage, context);

                // 槍（SPEAR）の距離減衰ロジック: 3ブロック未満の至近距離では最終ダメージ50%減少
                // すべての倍率（クリティカル、コンボ等）が乗った後の最終値に対して適用する
                if (weaponType == com.lunar_prototype.deepwither.StatType.SPEAR_DAMAGE) {
                    double distance = player.getLocation().distance(victim.getLocation());
                    if (distance < 3.0) {
                        damage *= 0.5;
                        uiManager.of(player).message(PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                                Component.text("間合いが近すぎる！ (ダメージ減少)", NamedTextColor.GRAY));
                    }
                }
            }
        }

        // ターゲット側の防御ステータス反映 (プレイヤー/モブ共通)
        if (victim != null && !context.isTrueDamage()) {
            com.lunar_prototype.deepwither.StatMap vStats = (victim instanceof Player p) ? statManager.getTotalStats(p) : new com.lunar_prototype.deepwither.StatMap();
            
            // 盾ブロック計算
            if (victim instanceof Player pVictim && pVictim.isBlocking() && attacker != null) {
                Vector toAttackerVec = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).normalize();
                Vector defenderLookVec = victim.getLocation().getDirection().normalize();
                if (toAttackerVec.dot(defenderLookVec) > 0.5) {
                    double blockRate = Math.max(0.0, Math.min(vStats.getFinal(com.lunar_prototype.deepwither.StatType.SHIELD_BLOCK_RATE), 1.0));
                    double blockedDamage = damage * blockRate;
                    damage -= blockedDamage;
                    victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                    uiManager.of(pVictim).combatAction("SHIELD BLOCK!!", NamedTextColor.AQUA);
                    uiManager.of(pVictim).message(PlayerSettingsManager.SettingType.SHOW_MITIGATION,
                            Component.text("盾防御！ ", NamedTextColor.AQUA)
                                    .append(Component.text("軽減: ", NamedTextColor.GRAY))
                                    .append(Component.text(Math.round(blockedDamage), NamedTextColor.GREEN))
                                    .append(Component.text(" (" + Math.round(damage) + "被弾)", NamedTextColor.RED)));
                }
            }
            
            // 防御力計算
            double defenseValue;
            double divisor = 250.0; // 統一除数
            if (context.isMagic()) {
                defenseValue = vStats.getFlat(com.lunar_prototype.deepwither.StatType.MAGIC_RESIST);
            } else {
                defenseValue = vStats.getFlat(com.lunar_prototype.deepwither.StatType.DEFENSE);
            }
            damage = DamageCalculator.applyDefense(damage, defenseValue, divisor);
        }

        context.setFinalDamage(Math.max(0.1, damage));

        // 1. イベント発火 (DeepwitherDamageEvent)
        DeepwitherDamageEvent dwEvent = new DeepwitherDamageEvent(
                victim, attacker, context.getFinalDamage(), context.getDamageType());
        Bukkit.getPluginManager().callEvent(dwEvent);

        if (dwEvent.isCancelled()) return;
        context.setFinalDamage(dwEvent.getDamage());

        // ダメージ適用前エフェクトの再生
        if (attacker instanceof Player pAtk && victim != null) {
            playHitEffects(pAtk, victim, context);
        }

        // 2. ダメージ適用
        applyDamage(context);

        // 事後処理 (Lifesteal, 異常状態付与など)
        if (attacker instanceof Player pAtk && victim != null && attackerStats != null) {
            handlePostDamageEffects(pAtk, victim, attackerStats, context.getFinalDamage());
            if (context.getWeapon() != null) {
                tryTriggerOnHitSkill(pAtk, victim, context.getWeapon());
            }
        }

        // 3. プレイヤーへの追加イベント (onPlayerRecevingDamageEvent)
        if (victim instanceof Player playerVictim && attacker != null) {
            Bukkit.getPluginManager().callEvent(new onPlayerRecevingDamageEvent(playerVictim, attacker, context.getFinalDamage()));
        }
    }

    private double applyComboAndCooldown(Player attacker, double damage, DamageContext context) {
        long currentTime = System.currentTimeMillis();
        SkillData skillData = Deepwither.getInstance().getSkilltreeManager().load(attacker.getUniqueId());
        
        if (skillData.hasSpecialEffect("COMBO_DAMAGE")) {
            double comboValue = skillData.getSpecialEffectValue("COMBO_DAMAGE");
            long lastHit = lastComboHitTimes.getOrDefault(attacker.getUniqueId(), 0L);
            int currentCombo = comboCounts.getOrDefault(attacker.getUniqueId(), 0);

            if (currentTime - lastHit > COMBO_TIMEOUT_MS) currentCombo = 0;

            double comboMultiplier = 1.0 + (currentCombo * (comboValue / 100.0));
            // 最大60%ボーナス (倍率1.6) に制限
            comboMultiplier = Math.min(comboMultiplier, 1.6);
            damage *= comboMultiplier;

            if (currentCombo > 0) {
                uiManager.of(attacker).message(PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                        Component.text("コンボ継続中! x" + currentCombo, NamedTextColor.YELLOW)
                                .append(Component.text(" (+" + Math.round((comboMultiplier - 1.0) * 100) + "%)", NamedTextColor.GOLD)));
            }
            comboCounts.put(attacker.getUniqueId(), Math.min(currentCombo + 1, 5));
            lastComboHitTimes.put(attacker.getUniqueId(), currentTime);
        }

        long lastAttack = lastSpecialAttackTime.getOrDefault(attacker.getUniqueId(), 0L);
        boolean ignoreCooldown = currentTime < lastAttack + COOLDOWN_IGNORE_MS;
        lastSpecialAttackTime.put(attacker.getUniqueId(), currentTime);

        if (!ignoreCooldown) {
            float cooldown = attacker.getAttackCooldown();
            if (cooldown < 1.0f) {
                double reduced = damage * (1.0 - cooldown);
                damage *= cooldown;
                uiManager.of(attacker).message(PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                        Component.text("攻撃クールダウン！ ", NamedTextColor.RED)
                                .append(Component.text("-" + Math.round(reduced), NamedTextColor.RED))
                                .append(Component.text(" ダメージ ", NamedTextColor.GRAY))
                                .append(Component.text("(" + Math.round(damage) + ")", NamedTextColor.GREEN)));
            }
        }
        return damage;
    }

    private void playHitEffects(Player attacker, LivingEntity victim, DamageContext context) {
        if (context.isCrit()) {
            uiManager.of(attacker).combatAction("CRITICAL!!", NamedTextColor.GOLD);
            uiManager.of(attacker).message(PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, 
                    Component.text("クリティカル！ ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("+" + Math.round(context.getFinalDamage()), NamedTextColor.RED)));
            
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
            uiManager.of(attacker).message(PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE,
                    Component.text("遠距離命中 ", NamedTextColor.GRAY)
                            .append(Component.text("+" + Math.round(context.getFinalDamage()), NamedTextColor.RED))
                            .append(Component.text(" [" + String.format("%.0f%%", context.getDistanceMultiplier() * 100) + "]", NamedTextColor.YELLOW)));
        }
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
            uiManager.of(attacker).message(PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG,
                    Component.text("[On-Hit] スキル「", NamedTextColor.GREEN)
                            .append(Component.text(skillId, NamedTextColor.AQUA))
                            .append(Component.text("」を発動！", NamedTextColor.GREEN)));
        }
    }

    private void handlePostDamageEffects(Player attacker, LivingEntity victim, com.lunar_prototype.deepwither.StatMap attackerStats, double damage) {
        double lifeSteal = attackerStats.getFinal(com.lunar_prototype.deepwither.StatType.LIFESTEAL);
        if (lifeSteal > 0) {
            double healAmount = damage * (lifeSteal / 100.0);
            if (healAmount > 0) {
                statManager.heal(attacker, healAmount);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.5f, 1.2f);
            }
        }
        if (DamageCalculator.rollChance(attackerStats.getFinal(com.lunar_prototype.deepwither.StatType.BLEED_CHANCE))) applyBleed(victim, attacker);
        if (DamageCalculator.rollChance(attackerStats.getFinal(com.lunar_prototype.deepwither.StatType.FREEZE_CHANCE))) applyFreeze(victim);
        if (DamageCalculator.rollChance(attackerStats.getFinal(com.lunar_prototype.deepwither.StatType.AOE_CHANCE))) applyAoE(victim, attacker, damage);
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
                bleedContext.setTrueDamage(true);
                process(bleedContext);
                target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 10, 0.2, 0.5, 0.2, Bukkit.createBlockData(Material.REDSTONE_BLOCK));
                count++;
            }
        }.runTaskTimer(plugin, 20L, 20L);
        uiManager.of(attacker).message(PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, Component.text("出血付与！", NamedTextColor.DARK_RED));
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
                process(aoeContext);
            }
        }
        uiManager.of(attacker).message(PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, Component.text("拡散ヒット！", NamedTextColor.YELLOW));
    }

    private void applyDamage(DamageContext context) {
        LivingEntity victim = context.getVictim();
        double damage = context.getFinalDamage();
        LivingEntity attacker = context.getAttacker();

        if (victim instanceof Player player) {
            processPlayerDamageWithAbsorption(player, damage, attacker != null ? attacker.getName() : "魔法/環境");
            
            // ログ送信
            NamedTextColor prefixColor = context.isMagic() ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.RED;
            String prefixText = context.isMagic() ? "魔法被弾！" : "物理被弾！";
            uiManager.of(player).message(PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, 
                    Component.text(prefixText, prefixColor, TextDecoration.BOLD)
                            .append(Component.text(" " + Math.round(damage), NamedTextColor.RED)));
        } else {
            // モブへのダメージ
            if (attacker instanceof Player playerAttacker) {
                executeCustomMobDamage(victim, damage, playerAttacker);
            } else {
                victim.damage(damage, attacker);
            }
        }

        // --- 被弾演出のブロードキャスト (PacketEvents + Sound + Knockback) ---
        playDamageFeedback(context);
    }

    private void playDamageFeedback(DamageContext context) {
        LivingEntity victim = context.getVictim();
        LivingEntity attacker = context.getAttacker();

        // 1. PacketEvents による被弾アニメーション (赤色フラッシュ)
        // PacketEvents APIが利用可能かチェックしてNPEを回避
        if (Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            try {
                if (PacketEvents.getAPI() != null && PacketEvents.getAPI().isLoaded()) {
                    WrapperPlayServerEntityAnimation hurtPacket = new WrapperPlayServerEntityAnimation(victim.getEntityId(), WrapperPlayServerEntityAnimation.EntityAnimationType.HURT);
                    victim.getWorld().getNearbyPlayers(victim.getLocation(), 40).forEach(p -> {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(p, hurtPacket);
                    });
                }
            } catch (Exception e) {
                // API取得中などの予期せぬエラーをキャッチして処理の停止を防ぐ
            }
        }

        // 2. 音の再生 (被害者の種類に応じて)
        Sound hurtSound = (victim instanceof Player) ? Sound.ENTITY_PLAYER_HURT : Sound.ENTITY_GENERIC_HURT;
        victim.getWorld().playSound(victim.getLocation(), hurtSound, 1.0f, 1.0f);

        // 3. ノックバックの適用
        if (attacker != null) {
            Vector dir = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize();
            dir.setY(0).normalize().multiply(0.4).setY(0.2); // 軽く横に弾き、少し浮かせる
            
            // プレイヤーかつシールドなどで防御していない場合、またはモブに適用
            victim.setVelocity(dir);
        }
    }

    private void executeCustomMobDamage(LivingEntity target, double damage, Player damager) {
        isProcessingDamage.add(damager.getUniqueId());
        try {
            double currentHealth = target.getHealth();
            if (currentHealth <= damage) {
                // 即死/トドメの処理
                target.setHealth(0.5);
                target.damage(100.0, damager);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (target.isValid() && !target.isDead()) target.remove();
                }, 3L);
            } else {
                target.damage(damage, damager);
            }
        } finally {
            isProcessingDamage.remove(damager.getUniqueId());
        }
    }

    private void processPlayerDamageWithAbsorption(Player player, double damage, String sourceName) {
        double currentHealth = statManager.getActualCurrentHealth(player);
        if (currentHealth <= 0) return;

        double absorption = player.getAbsorptionAmount() * 10.0;
        if (absorption > 0) {
            if (damage <= absorption) {
                double newAbs = absorption - damage;
                player.setAbsorptionAmount((float) (newAbs / 10.0));
                uiManager.of(player).message(PlayerSettingsManager.SettingType.SHOW_MITIGATION, 
                        Component.text("シールド防御: ", NamedTextColor.YELLOW)
                                .append(Component.text("-" + Math.round(damage), NamedTextColor.YELLOW)));
                return;
            } else {
                player.setAbsorptionAmount(0f);
                uiManager.of(player).message(PlayerSettingsManager.SettingType.SHOW_MITIGATION, 
                        Component.text("シールドブレイク！", NamedTextColor.RED));
                // 残りのダメージは通常通り通るが、ここでは吸収分を差し引いた処理を継続させても良い(現状の仕様に合わせる)
            }
        }

        double newHp = currentHealth - damage;
        statManager.setActualCurrentHealth(player, newHp);
        
        if (newHp <= 0) {
            player.sendMessage(Component.text(sourceName + "に倒されました。", NamedTextColor.DARK_RED));
        } else {
            uiManager.of(player).message(PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, 
                    Component.text("-" + Math.round(damage) + " HP", NamedTextColor.RED));
        }
    }

    public void setProcessing(UUID uuid, boolean processing) {
        if (processing) isProcessingDamage.add(uuid);
        else isProcessingDamage.remove(uuid);
    }

    public boolean isProcessing(UUID uuid) {
        return isProcessingDamage.contains(uuid);
    }
}
