package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.skill.cc.CCEffectType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

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

        // 継続的な球体パーティクル演出
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 120 || !caster.isValid()) { this.cancel(); return; }
                
                // 半径3.0の球体表面にパーティクルを生成
                for (int i = 0; i < 12; i++) {
                    double phi = Math.random() * Math.PI;
                    double theta = Math.random() * 2 * Math.PI;
                    double r = 3.0;
                    double x = r * Math.sin(phi) * Math.cos(theta);
                    double y = r * Math.cos(phi);
                    double z = r * Math.sin(phi) * Math.sin(theta);
                    
                    caster.getWorld().spawnParticle(Particle.END_ROD, caster.getLocation().add(x, y + 1.2, z), 1, 0, 0, 0, 0);
                }
                ticks += 2;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 2L);

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
