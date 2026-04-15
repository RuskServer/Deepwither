package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Blood Reversal Field (反転血界)
 * 発動後6秒間(120tick)、プレイヤーが半径5ブロック内の敵を攻撃するたびに
 * 自身の最大HPの20%を自傷するデバフオーラを付与するブラッドマジック。
 *
 * デバフの実際の発動は DamageManager#onBloodReversalField から行われる。
 */
public class BloodReversalFieldSkill implements ISkillLogic {

    /** デバフの持続時間 (6秒 = 120tick) */
    public static final int DURATION_TICKS = 120;

    /** 自傷割合 (最大HPの20%) */
    public static final double SELF_DAMAGE_PERCENT = 0.20;

    /** 発動対象とみなす半径 (ブロック) */
    public static final double TARGET_RADIUS = 5.0;

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        if (!(caster instanceof Player player)) return false;

        // オーラ付与 (120tick = 6秒)
        Map<String, Object> meta = new HashMap<>();
        meta.put("self_damage_percent", SELF_DAMAGE_PERCENT);
        meta.put("target_radius", TARGET_RADIUS);
        Deepwither.getInstance().getAuraManager().addAura(caster, "blood_reversal_field", DURATION_TICKS, meta);

        // ===== 演出 =====
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.6f, 0.7f);

        // 赤い輪状パーティクル
        Particle.DustOptions bloodRed  = new Particle.DustOptions(Color.RED, 2.0f);
        Particle.DustOptions darkPurple = new Particle.DustOptions(Color.fromRGB(80, 0, 80), 1.5f);

        for (int i = 0; i < 20; i++) {
            double angle = (2 * Math.PI / 20) * i;
            double x = Math.cos(angle) * 2.5;
            double z = Math.sin(angle) * 2.5;
            player.getWorld().spawnParticle(Particle.DUST,
                    player.getLocation().add(x, 0.1, z), 3, 0.05, 0.1, 0.05, 0, bloodRed);
        }
        player.getWorld().spawnParticle(Particle.WAX_OFF, player.getLocation().add(0, 1.5, 0),
                30, 0.4, 0.6, 0.4, 3.0);
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0), 8);

        // 持続中のパーティクル演出 (120tick)
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= DURATION_TICKS || !player.isValid()
                        || !Deepwither.getInstance().getAuraManager().hasAura(player, "blood_reversal_field")) {
                    this.cancel();
                    return;
                }
                // 足元に暗い血のDUST
                if (ticks % 4 == 0) {
                    double a = Math.random() * 2 * Math.PI;
                    double r = Math.random() * 1.2;
                    player.getWorld().spawnParticle(Particle.DUST,
                            player.getLocation().add(Math.cos(a) * r, 0.05, Math.sin(a) * r),
                            1, 0.02, 0.02, 0.02, 0, darkPurple);
                }
                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        player.sendMessage(Component.text(">>> ", NamedTextColor.DARK_GRAY)
                .append(Component.text("反転血界", NamedTextColor.DARK_RED))
                .append(Component.text(" — 攻撃のたびに血が逆流する...", NamedTextColor.GRAY)));

        return true;
    }
}
