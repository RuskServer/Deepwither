package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageCalculator;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.core.damage.DamageProcessor;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;

public class CustomDamageMechanics implements ITargetedEntitySkill {
    protected final double basePower;
    protected final double multiplier;
    protected final String type;
    protected final List<String> tags;
    protected final boolean canCrit;
    protected final boolean isProjectile;

    public CustomDamageMechanics(MythicLineConfig config) {
        this.basePower = config.getDouble(new String[]{"damage", "d"}, 10.0);
        this.multiplier = config.getDouble(new String[]{"multiplier", "m"}, 1.0);
        this.type = config.getString(new String[]{"type", "t"}, "PHYSICAL").toUpperCase();
        this.tags = Arrays.asList(config.getString(new String[]{"tags", "tg"}, "").split(","));
        this.canCrit = config.getBoolean(new String[]{"canCrit", "crit"}, true);
        this.isProjectile = config.getBoolean(new String[]{"projectile", "p"}, false);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        LivingEntity caster = (LivingEntity) data.getCaster().getEntity().getBukkitEntity();
        if (!(BukkitAdapter.adapt(target) instanceof LivingEntity bukkitTarget)) return SkillResult.INVALID_TARGET;

        Deepwither plugin = Deepwither.getInstance();
        DamageManager damageManager = plugin.getDamageManager();
        DamageProcessor damageProcessor = plugin.getDamageProcessor();

        if (damageManager.isInvulnerable(bukkitTarget)) return SkillResult.CONDITION_FAILED;

        boolean isMagic = type.equals("MAGIC");
        DeepwitherDamageEvent.DamageType damageType = isMagic ? DeepwitherDamageEvent.DamageType.MAGIC : 
                                                     (isProjectile ? DeepwitherDamageEvent.DamageType.PROJECTILE : DeepwitherDamageEvent.DamageType.PHYSICAL);

        // ベースダメージの設定 (MythicMobsの設定値)
        double baseDamage = basePower * multiplier;
        
        DamageContext context = new DamageContext(caster, bukkitTarget, damageType, baseDamage);
        context.setProjectile(isProjectile);
        tags.forEach(context::addTag);

        if (caster instanceof Player player) {
            StatMap attackerStats = StatManager.getTotalStatsFromEquipment(player);

            // 武器種の特定 (武器種ボーナスのために Processor に渡す)
            StatType weaponType = damageManager.getWeaponStatType(player.getInventory().getItemInMainHand());
            context.setWeaponStatType(weaponType);

            // 距離補正 (計算は Processor)
            if (isProjectile) {
                double distMult = DamageCalculator.calculateDistanceMultiplier(player.getLocation(), bukkitTarget.getLocation());
                context.setDistanceMultiplier(distMult);
            }

            // クリティカル判定 (フラグのみ。効果や倍率計算は Processor)
            if (canCrit && DamageCalculator.rollChance(attackerStats.getFinal(StatType.CRIT_CHANCE))) {
                context.setCrit(true);
            }

            Deepwither.getInstance().getArtifactManager().handleArtifactSetTrigger(context, ItemFactory.ArtifactSetTrigger.ATTACK_HIT);
            if (context.isCrit()) {
                Deepwither.getInstance().getArtifactManager().handleArtifactSetTrigger(context, ItemFactory.ArtifactSetTrigger.CRIT);
            }
        } else {
            // キャスターがモンスターの場合のクリティカル判定などは必要に応じて簡略化して Processor へ
            if (bukkitTarget instanceof Player pTarget) {
                // Mobクリ判定のみ維持、最終計算は Processor
            }
        }

        // --- アンデッド特効やドレインなどの特殊タグ処理 ---
        if (tags.contains("UNDEAD") && caster instanceof Player p) {
            if (handleUndeadDamage(p, bukkitTarget)) return SkillResult.SUCCESS;
        }

        // 統合されたダメージエンジンで処理を実行
        damageProcessor.process(context);

        // 事後処理 (スキル専用Lifesteal)
        if (tags.contains("LIFESTEAL") && caster instanceof Player p) {
            handleLifesteal(p, bukkitTarget, context.getFinalDamage());
        }

        if (caster instanceof Player playerCaster) {
            StatManager sm = (StatManager) plugin.getStatManager();
            Double celestialTrueDamage = context.get("celestial_true_damage");
            if (celestialTrueDamage != null && celestialTrueDamage > 0) {
                sm.applyDamage(bukkitTarget, celestialTrueDamage, caster);
            }

            Boolean celestialReplay = context.get("celestial_burst_repeat");
            if (Boolean.TRUE.equals(celestialReplay)) {
                DamageContext burstReplay = new DamageContext(caster, bukkitTarget, damageType, context.getFinalDamage());
                burstReplay.addTag("ARTIFACT_CELESTIAL_BURST_REPLAY");
                damageProcessor.process(burstReplay);
            }

            Double lunarBonusMagic = context.get("lunar_echo_bonus_magic");
            if (lunarBonusMagic != null && lunarBonusMagic > 0) {
                DamageContext lunarExtra = new DamageContext(caster, bukkitTarget, DeepwitherDamageEvent.DamageType.MAGIC, lunarBonusMagic);
                damageProcessor.process(lunarExtra);
            }
        }

        return SkillResult.SUCCESS;
    }

    private static final java.util.Set<String> UNDEAD_MOB_IDS = java.util.Set.of("melee_skeleton", "ranged_skeleton", "melee_zombi");

    private boolean handleUndeadDamage(Player player, LivingEntity target) {
        var activeMobOptional = io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().getActiveMob(target.getUniqueId());
        if (activeMobOptional.isPresent()) {
            String mobId = activeMobOptional.get().getType().getInternalName();
            if (mobId != null && UNDEAD_MOB_IDS.contains(mobId)) {
                StatManager sm = (StatManager) Deepwither.getInstance().getStatManager();
                double damage = sm.getMobHealth(target) * 0.5;
                Deepwither.getInstance().getUIManager().of(player).combatAction("HOLY SMITE!!", NamedTextColor.LIGHT_PURPLE);
                Deepwither.getInstance().getUIManager().of(player).message(PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Component.text("聖特攻！ ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD).append(Component.text("アンデッドに50%ダメージ！", NamedTextColor.WHITE)));
                target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
                DamageContext holyContext = new DamageContext(player, target, DeepwitherDamageEvent.DamageType.MAGIC, damage);
                Deepwither.getInstance().getDamageProcessor().process(holyContext);
                return true;
            }
        }
        return false;
    }

    private void handleLifesteal(Player player, LivingEntity target, double finalDamage) {
        StatManager sm = (StatManager) Deepwither.getInstance().getStatManager();
        double heal = sm.getMobMaxHealth(target) * (finalDamage / 100.0 / 100.0);
        double maxPlayerHp = sm.getActualMaxHealth(player);
        heal = Math.min(heal, maxPlayerHp * 0.20);
        Deepwither.getInstance().getStatManager().heal(player, heal);
        Deepwither.getInstance().getUIManager().of(player).message(PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Component.text("LS！ ", NamedTextColor.GREEN, TextDecoration.BOLD).append(Component.text(String.format("%.1f", heal) + " 回復", NamedTextColor.DARK_GREEN)));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
    }
}
