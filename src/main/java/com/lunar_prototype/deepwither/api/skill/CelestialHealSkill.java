package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.skill.utils.TargetingUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * 聖なる回復魔法: Celestial Heal
 * ターゲット(または自分自身)の体力を最大値の5%回復させる。
 * 最新の支援用ターゲッティングユーティリティを使用。
 */
public class CelestialHealSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        if (!(caster instanceof Player player)) return false;

        // 1. ターゲット選定 (支援ターゲット優先、いなければ自分)
        Player target = TargetingUtil.getSupportTarget(player, 15.0, 3.0);
        if (target == null) {
            target = player; // パーティーメンバーがいなければ自分を回復
        }

        // 2. 回復実行 (最大HPの5%)
        double maxHp = Deepwither.getInstance().getStatManager().getActualMaxHealth(target);
        double healAmount = maxHp * 0.05;
        Deepwither.getInstance().getStatManager().heal(target, healAmount);

        // 3. 演出
        executeCelestialHealEffect(target);

        return true;
    }

    /**
     * 神聖な回復エフェクトを実行
     */
    private void executeCelestialHealEffect(Player target) {
        Location loc = target.getLocation();
        
        // サウンド (清らかな音と光の共鳴)
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.5f);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.8f);

        // 演出用スレッド (数ミリ秒間の継続エフェクト)
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 20) {
                    this.cancel();
                    return;
                }

                // 1. 足元のリング魔法陣 (WAX_OFF)
                if (ticks < 10) {
                    double radius = 1.2;
                    int count = 16;
                    for (int i = 0; i < count; i++) {
                        double theta = (Math.PI * 2 / count) * i;
                        double x = Math.cos(theta) * radius;
                        double z = Math.sin(theta) * radius;
                        Location circleLoc = loc.clone().add(x, 0.1, z);
                        circleLoc.getWorld().spawnParticle(Particle.WAX_OFF, circleLoc, 1, 0, 0.1, 0, 0);
                    }
                }

                // 2. 昇天する光の柱 (END_ROD + FLASH)
                for (int i = 0; i < 5; i++) {
                    double rx = (Math.random() - 0.5) * 1.5;
                    double rz = (Math.random() - 0.5) * 1.5;
                    Location pLoc = loc.clone().add(rx, ticks * 0.2, rz);
                    pLoc.getWorld().spawnParticle(Particle.END_ROD, pLoc, 1, 0, 0.1, 0, 0.02);
                    
                    if (Math.random() > 0.8) {
                        pLoc.getWorld().spawnParticle(Particle.FLASH, pLoc, 1, 0, 0, 0, 0);
                    }
                }

                // 中心部の輝き
                loc.getWorld().spawnParticle(Particle.FIREWORK, loc.clone().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.02);

                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }
}
