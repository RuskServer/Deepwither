package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 崩落スキル: Collapse
 * アクティブ発動でオーラをまとい、次の攻撃で地割れを伴う重厚な一撃を放つ。
 */
public class CollapseSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        if (!(caster instanceof Player player)) return false;

        // オーラ付与 (10秒 -> 6秒へ短縮)
        Deepwither.getInstance().getAuraManager().addAura(player, "collapse_aura", 120);

        // 発動音
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LANTERN_BREAK, 1.2f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);

        // オーラ演出 (金色の粒子をまとわせる)
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !Deepwither.getInstance().getAuraManager().hasAura(player, "collapse_aura")) {
                    this.cancel();
                    return;
                }

                // 武器や手元に金色の粒子
                player.getWorld().spawnParticle(Particle.WAX_OFF, player.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.02);
                
                if (ticks % 10 == 0) {
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 1, 0.2, 0.2, 0.2, 0);
                }

                ticks++;
                if (ticks > 120) this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 2L);

        return true;
    }
}
