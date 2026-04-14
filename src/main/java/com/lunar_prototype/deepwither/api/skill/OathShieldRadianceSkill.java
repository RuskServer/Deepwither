package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.skill.cc.CCEffectType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * OathShield Radiance (誓約の盾・輝き)
 * 4秒間、遠距離攻撃対策及びCC完全無効化・解除を行う。
 */
public class OathShieldRadianceSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        
        Map<String, Object> meta = new HashMap<>();
        meta.put("projectile_reduction", 1.0);
        Deepwither.getInstance().getAuraManager().addAura(caster, "oath_shield", 80, meta);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 80 || !caster.isValid()) { this.cancel(); return; }
                // 半径3.5ブロックの球体パーティクル
                for (int i = 0; i < 15; i++) {
                    double phi = Math.random() * Math.PI;
                    double theta = Math.random() * 2 * Math.PI;
                    double r = 3.5;
                    double x = r * Math.sin(phi) * Math.cos(theta);
                    double y = r * Math.cos(phi);
                    double z = r * Math.sin(phi) * Math.sin(theta);
                    caster.getWorld().spawnParticle(Particle.END_ROD, caster.getLocation().add(x, y + 1.2, z), 1, 0, 0, 0, 0);
                }
                ticks += 2;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 2L);
        return true;
    }
}
