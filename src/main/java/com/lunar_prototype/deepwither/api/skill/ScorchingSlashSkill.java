package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 焦熱の斬撃スキル: ScorchingSlash
 * 武器を赤熱させ、次の一撃で爆炎と共に強力な継続ダメージを与える。
 */
public class ScorchingSlashSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        if (!(caster instanceof Player player)) return false;

        // オーラ付与 (10秒)
        Deepwither.getInstance().getAuraManager().addAura(player, "scorching_slash_aura", 200);

        // 発動音 (火が燃え上がるような音)
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.5f, 1.2f);

        // オーラ演出 (赤熱・火の粉)
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !Deepwither.getInstance().getAuraManager().hasAura(player, "scorching_slash_aura")) {
                    this.cancel();
                    return;
                }

                // 武器や手元に火の粉と煙
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1.2, 0), 3, 0.3, 0.4, 0.3, 0.02);
                player.getWorld().spawnParticle(Particle.LAVA, player.getLocation().add(0, 1.2, 0), 1, 0.2, 0.2, 0.2, 0);
                
                if (ticks % 5 == 0) {
                    player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1.5, 0), 1, 0.1, 0.1, 0.1, 0.01);
                }

                ticks++;
                if (ticks > 200) this.cancel();
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 2L);

        return true;
    }
}
