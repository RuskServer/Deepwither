package com.lunar_prototype.deepwither.core.damage;

import com.lunar_prototype.deepwither.PlayerSettingsManager;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SpecialItemEffectManager;
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
import com.lunar_prototype.deepwither.ManaData;
import com.lunar_prototype.deepwither.SkillData;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffectType;

@DependsOn({StatManager.class, PlayerSettingsManager.class, com.lunar_prototype.deepwither.core.UIManager.class, com.lunar_prototype.deepwither.party.PartyManager.class})
public class DamageProcessor implements IManager, org.bukkit.event.Listener {

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

    private final Map<UUID, Long> aerodynamicsCooldown = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> rhythmStacks = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> rhythmLastHit = new HashMap<>();
    private final Map<UUID, Integer> precisionStacks = new HashMap<>();
    private final Map<UUID, Long> lastSkillUsedTime = new HashMap<>();

    public DamageProcessor(JavaPlugin plugin, IStatManager statManager, com.lunar_prototype.deepwither.core.UIManager uiManager, DeepwitherPartyAPI partyAPI) {
        this.plugin = plugin;
        this.statManager = statManager;
        this.uiManager = uiManager;
        this.partyAPI = partyAPI;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @org.bukkit.event.EventHandler
    public void onSkillCast(com.lunar_prototype.deepwither.api.event.SkillCastEvent e) {
        lastSkillUsedTime.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

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
            if (partyAPI.isInSameParty(pAtk, pVic)) return;
        }

        // 追撃（Echo）からの再帰発動を防止
        if (context.hasTag("TRAIT_ECHO")) {
            applyDamage(context);
            return;
        }

        double damage = context.getFinalDamage();
        long currentTime = System.currentTimeMillis();

        // --- 特殊効果のフック (攻撃) ---
        SpecialItemEffectManager effectManager = Deepwither.getInstance().getSpecialItemEffectManager();
        if (effectManager != null && attacker instanceof Player) {
            com.lunar_prototype.deepwither.api.item.ISpecialItemEffect effect = effectManager.getEffect(context.getWeapon());
            if (effect != null) {
                effect.onAttack(context, context.getWeapon());
                damage = context.getFinalDamage();
            }
        }

        com.lunar_prototype.deepwither.StatMap attackerStats = null;
        com.lunar_prototype.deepwither.StatMap victimStats = (victim instanceof Player p) ? statManager.getTotalStats(p) : new com.lunar_prototype.deepwither.StatMap();
        
        if (attacker instanceof Player player) {
            attackerStats = statManager.getTotalStats(player);
            UUID attackerUUID = player.getUniqueId();
            
            // --- 特性の計算用データ準備 ---
            double currentMana = Deepwither.getInstance().getManaManager().get(attackerUUID).getCurrentMana();
            double maxMana = Deepwither.getInstance().getManaManager().get(attackerUUID).getMaxMana();
            double currentHp = statManager.getActualCurrentHealth(player);
            double maxHp = statManager.getActualMaxHealth(player);
            double baseAttack = attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.ATTACK_DAMAGE);
            double baseMagic = attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.MAGIC_DAMAGE);
            double attackerDefense = attackerStats.getFinal(com.lunar_prototype.deepwither.StatType.DEFENSE);
            double moveSpeed = player.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).getValue();

            // --- 特性 (Trait) 発動ロジック: 攻撃側 ---
            
            // 1. [魔力炉] Mana Battery: マナ -> 物理ダメージ (基礎攻撃力の10%上限)
            if (attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.TRAIT_MANA_BATTERY) > 0 && !context.isMagic()) {
                double bonus = currentMana * 0.05; // マナの5%を物理ダメージに
                double cap = baseAttack * 0.10;
                damage += Math.min(bonus, cap);
            }

            // 2. [血の盟約] Sanguine Pact: 減少HP% -> 魔法攻撃力 (+1%につき+1%、最大+50%)
            if (attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.TRAIT_SANGUINE_PACT) > 0 && context.isMagic()) {
                double hpRatio = currentHp / maxHp;
                double missingHpPct = Math.max(0, (1.0 - hpRatio) * 100.0);
                double bonusMult = 1.0 + (Math.min(missingHpPct, 50.0) / 100.0);
                damage *= bonusMult;
            }

            // 3. [硬質な力] Iron Will: 防御力 -> 最終ダメージ (防御力の5%加算)
            if (attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.TRAIT_IRON_WILL) > 0) {
                damage += attackerDefense * 0.05;
            }

            // 4. [韋駄天] Aerodynamics: 初撃強化 (CD15秒)
            if (attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.TRAIT_AERODYNAMICS) > 0) {
                long lastAero = aerodynamicsCooldown.getOrDefault(attackerUUID, 0L);
                if (currentTime - lastAero >= 15000L) {
                    damage += (baseAttack * 0.30) * (moveSpeed / 0.1); // 移動速度が高いほど強化
                    aerodynamicsCooldown.put(attackerUUID, currentTime);
                    uiManager.of(player).message(PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, 
                            Component.text("★ [韋駄天] 疾風の初撃！", NamedTextColor.YELLOW));
                }
            }

            // 5. [激流] Rhythm: 同一対象ヒットでダメージ加速 (最大5スタック、維持0.5秒)
            if (attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.TRAIT_RHYTHM) > 0) {
                Map<UUID, Integer> stacks = rhythmStacks.computeIfAbsent(attackerUUID, k -> new HashMap<>());
                Map<UUID, Long> lastHits = rhythmLastHit.computeIfAbsent(attackerUUID, k -> new HashMap<>());
                UUID victimUUID = victim.getUniqueId();
                
                long lastHitTime = lastHits.getOrDefault(victimUUID, 0L);
                int currentStacks = (currentTime - lastHitTime <= 500L) ? stacks.getOrDefault(victimUUID, 0) : 0;
                
                damage *= (1.0 + (currentStacks * 0.05)); // 1スタックにつき5%
                
                stacks.put(victimUUID, Math.min(currentStacks + 1, 5));
                lastHits.put(victimUUID, currentTime);
            }

            // 6. [均衡] Dual Core: 物理・魔法差に基づくダメージUP (最大30%)
            if (attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.TRAIT_DUAL_CORE) > 0) {
                double diff = Math.abs(baseAttack - baseMagic);
                // 漸近式: 30% * (diff / (diff + 100))
                double bonusPct = 30.0 * (diff / (diff + 100.0));
                damage *= (1.0 + (bonusPct / 100.0));
            }

            // 8. [蓄積] Precision: 非会心時に次回会心強化 (最大3スタック)
            if (attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.TRAIT_PRECISION) > 0) {
                int pStacks = precisionStacks.getOrDefault(attackerUUID, 0);
                if (context.isCrit()) {
                    damage *= (1.0 + (pStacks * 0.15)); // 1スタックにつき会心ダメージ15%加算
                    precisionStacks.put(attackerUUID, 0);
                } else {
                    precisionStacks.put(attackerUUID, Math.min(pStacks + 1, 3));
                }
            }
            
            // 基礎攻撃力加算 (物理/魔法)
            if (context.isMagic()) {
                double magicDmg = attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.MAGIC_DAMAGE);

                if (context.hasTag("AOE")) {
                    damage += magicDmg * 0.6;
                    // AoEの%ボーナス乗算
                    double aoePct = attackerStats.getFinal(com.lunar_prototype.deepwither.StatType.MAGIC_AOE_BONUS);
                    if (aoePct > 0) damage *= (1.0 + aoePct / 100.0);
                } else if (context.hasTag("BURST")) {
                    damage += magicDmg * 0.4;
                    // バーストの%ボーナス乗算
                    double burstPct = attackerStats.getFinal(com.lunar_prototype.deepwither.StatType.MAGIC_BURST_BONUS);
                    if (burstPct > 0) damage *= (1.0 + burstPct / 100.0);
                } else {
                    // 通常の魔法攻撃は100%加算
                    damage += magicDmg;
                }
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
            
            // 9. [福音] Aegis: バフ数 -> 被ダメージ軽減 (最大20%)
            if (vStats.getFlat(com.lunar_prototype.deepwither.StatType.TRAIT_AEGIS) > 0) {
                int buffCount = victim.getActivePotionEffects().size();
                double reduction = Math.min(buffCount * 0.04, 0.20); // 1つにつき4%
                damage *= (1.0 - reduction);
            }

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
        Boolean isFullChargeMeta = context.get("is_full_charge");
        boolean isFullCharge = (isFullChargeMeta == null) || isFullChargeMeta;

        DeepwitherDamageEvent dwEvent = new DeepwitherDamageEvent(
                victim, attacker, context.getFinalDamage(), context.getDamageType(), isFullCharge);
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

        float cooldown = attacker.getAttackCooldown();
        boolean isFullCharge = cooldown >= 0.9f;

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
            
            // クールダウンが不十分な場合はコンボをカウントしない (増加させない)
            if (isFullCharge) {
                comboCounts.put(attacker.getUniqueId(), Math.min(currentCombo + 1, 5));
                lastComboHitTimes.put(attacker.getUniqueId(), currentTime);
            }
        }

        long lastAttack = lastSpecialAttackTime.getOrDefault(attacker.getUniqueId(), 0L);
        boolean ignoreCooldown = currentTime < lastAttack + COOLDOWN_IGNORE_MS;
        lastSpecialAttackTime.put(attacker.getUniqueId(), currentTime);

        if (!ignoreCooldown) {
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
        UUID attackerUUID = attacker.getUniqueId();
        
        // 10. [理力循環] Mana Well: 与ダメージ -> マナ還元 (最大マナの25%を上限)
        if (attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.TRAIT_MANA_WELL) > 0) {
            double manaGain = damage * 0.03; // 与ダメの3%還元
            ManaData manaData = Deepwither.getInstance().getManaManager().get(attackerUUID);
            double gainCap = manaData.getMaxMana() * 0.25;
            manaData.regen(Math.min(manaGain, gainCap));
        }

        // 7. [残響] Arcane Echo: スキル使用後 -> 魔法追撃
        long lastSkill = lastSkillUsedTime.getOrDefault(attackerUUID, 0L);
        if (attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.TRAIT_ARCANE_ECHO) > 0 && (System.currentTimeMillis() - lastSkill <= 5000L)) {
            double echoDamage = damage * 0.20;
            double baseMagic = attackerStats.getFlat(com.lunar_prototype.deepwither.StatType.MAGIC_DAMAGE);
            echoDamage = Math.min(echoDamage, baseMagic);
            
            if (echoDamage > 0.1) {
                // 追撃は別のDamageContextで処理
                DamageContext echoCtx = new DamageContext(attacker, victim, DeepwitherDamageEvent.DamageType.MAGIC, echoDamage);
                echoCtx.addTag("TRAIT_ECHO");
                process(echoCtx);
            }
        }

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

        com.lunar_prototype.deepwither.StatManager sm = (com.lunar_prototype.deepwither.StatManager) statManager;
        
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
                sm.applyDamage(victim, damage, attacker);
            }
        }

        // --- 被弾演出のブロードキャスト (PacketEvents + Sound + Knockback) ---
        playDamageFeedback(context);
    }

    private void playDamageFeedback(DamageContext context) {
        LivingEntity victim = context.getVictim();
        LivingEntity attacker = context.getAttacker();

        // 1. 被弾アニメーション (赤色フラッシュ)
        victim.playHurtAnimation(0);

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
            com.lunar_prototype.deepwither.StatManager sm = (com.lunar_prototype.deepwither.StatManager) statManager;
            sm.applyDamage(target, damage, damager);
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
