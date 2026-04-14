package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.skill.cc.CCEffectType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Luminary Veil (輝ける帳)
 * 6秒間移動不可となる代わりに、CC無効化・解除及び遠距離・魔法ダメージを無効化する。
 */
public class LuminaryVeilSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 発動音・演出
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.8f);
        caster.getWorld().spawnParticle(Particle.GLOW, caster.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1);

        // 1. CC解除
        caster.removePotionEffect(PotionEffectType.SLOWNESS);
        caster.removePotionEffect(PotionEffectType.BLINDNESS);

        // 2. 移動不可付与 (6秒 = 120 tick)
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 255));

        // 3. オーラ付与 (6秒 = 120 tick)
        Map<String, Object> meta = new HashMap<>();
        meta.put("immunity_types", EnumSet.allOf(CCEffectType.class));
        meta.put("projectile_reduction", 1.0); // 遠距離・魔法ダメージ無効
        
        Deepwither.getInstance().getAuraManager().addAura(caster, "oath_shield", 120, meta);

        return true;
    }
}
