package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * BloodSurge (ブラッドサージ) スキル
 * 発動後10秒間、攻撃時に自身のHPを削りつつ、ターゲットに強力な追加ダメージと生命吸収（Lifesteal）を与える。
 */
public class BloodSurgeSkill implements ISkillLogic {
    @Override
    public boolean cast(LivingEntity caster, SkillDefinition definition, int level) {
        if (!(caster instanceof Player player)) {
            return false;
        }

        // --- 1. 発動時自傷 (最大HPの20%) ---
        double maxHp = Deepwither.getInstance().getStatManager().getActualMaxHealth(player);
        double selfCost = maxHp * 0.2;
        
        // 自傷ダメージを適用 (True Damage)
        com.lunar_prototype.deepwither.core.damage.DamageContext selfCtx = 
            new com.lunar_prototype.deepwither.core.damage.DamageContext(null, player, com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent.DamageType.MAGIC, selfCost);
        selfCtx.setTrueDamage(true);
        Deepwither.getInstance().getDamageProcessor().process(selfCtx);

        // --- 2. オーラ付与 (10秒 = 200 tick, 最大3回発動) ---
        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("remaining_hits", 3);
        Deepwither.getInstance().getAuraManager().addAura(player, "blood_surge", 200, meta);

        // --- 演出 ---
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.2f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_HORSE_DEATH, 0.8f, 0.5f);
        
        // 大量の血が噴き出すような演出
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 80, 0.4, 0.8, 0.4, 0.1, new Particle.DustOptions(Color.RED, 2.5f));
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0), 15);

        player.sendMessage(Component.text(">>> ", NamedTextColor.GRAY)
                .append(Component.text("ブラッドサージ", NamedTextColor.RED))
                .append(Component.text("を発動！ HPを犠牲に生命吸収能力を極限まで高めた...", NamedTextColor.GRAY)));
        
        return true;
    }
}
