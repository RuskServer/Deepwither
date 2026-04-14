package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Frost Armor (氷の鎧)
 * 7.5秒間 (150tick)、ダメージを40%軽減するオーラを纏う。
 */
public class FrostArmorSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 発動音
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.0f);

        // オーラの登録 (150tick = 7.5秒)
        Map<String, Object> meta = new HashMap<>();
        meta.put("damage_reduction", 0.40); // 40%軽減
        Deepwither.getInstance().getAuraManager().addAura(caster, "frost_armor", 150, meta);

        // 持続エフェクト (150tick)
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 150 || !caster.isValid()) {
                    this.cancel();
                    return;
                }
                
                // パーティクル演出
                caster.getWorld().spawnParticle(Particle.BLOCK, caster.getLocation().add(0, 1.2, 0), 3, 0.3, 0.3, 0.3, 0, org.bukkit.Material.ICE.createBlockData());
                
                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }
}
