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

            // クリティカル判定 (フラグのみ。計算は Processor)
            if (canCrit && DamageCalculator.rollChance(attackerStats.getFinal(StatType.CRIT_CHANCE))) {
                context.setCrit(true);
                playCriticalEffect(bukkitTarget);
                Deepwither.getInstance().getUIManager().of(player).combatAction("CRITICAL!!", NamedTextColor.GOLD);
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

        // --- アンデッド特効やドレインなどの特殊タグ処理 (Processor の前または後でトリガー) ---
        if (tags.contains("UNDEAD") && caster instanceof Player p) {
            if (damageManager.handleUndeadDamage(p, bukkitTarget)) return SkillResult.SUCCESS;
        }

        // 統合されたダメージエンジンで処理を実行
        damageProcessor.process(context);

        // 事後処理 (Lifestealなど)
        if (tags.contains("LIFESTEAL") && caster instanceof Player p) {
            damageManager.handleLifesteal(p, bukkitTarget, context.getFinalDamage());
        }

        if (caster instanceof Player playerCaster) {
            Double celestialTrueDamage = context.get("celestial_true_damage");
            if (celestialTrueDamage != null && celestialTrueDamage > 0) {
                if (bukkitTarget instanceof Player playerTarget) {
                    double currentHp = Deepwither.getInstance().getStatManager().getActualCurrentHealth(playerTarget);
                    Deepwither.getInstance().getStatManager().setActualCurrentHealth(playerTarget, currentHp - celestialTrueDamage);
                } else {
                    bukkitTarget.damage(celestialTrueDamage, caster);
                }
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

    private void playCriticalEffect(LivingEntity target) {
        Location hitLoc = target.getLocation().add(0, 1.2, 0);
        World world = hitLoc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.FLASH, hitLoc, 1, 0, 0, 0, 0, Color.WHITE);
        world.spawnParticle(Particle.SONIC_BOOM, hitLoc, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.LAVA, hitLoc, 8, 0.4, 0.4, 0.4, 0.1);
        world.spawnParticle(Particle.CRIT, hitLoc, 30, 0.5, 0.5, 0.5, 0.5);
        world.spawnParticle(Particle.LARGE_SMOKE, hitLoc, 15, 0.2, 0.2, 0.2, 0.05);

        world.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
        world.playSound(hitLoc, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.5f);
        world.playSound(hitLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.6f);
        world.playSound(hitLoc, Sound.ITEM_TRIDENT_HIT, 1.0f, 0.7f);
    }
}
