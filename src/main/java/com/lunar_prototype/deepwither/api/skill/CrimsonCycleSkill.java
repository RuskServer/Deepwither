package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Crimson Cycle (深緋の輪)
 * キャスター自身の現在HPの10%を血の代償として消費し、
 * 半径5ブロック内の全敵に同量の魔法ダメージを与えるブラッドマジック。
 * HPが一定以下（最大HPの15%未満）の場合は安全のため発動を拒否する。
 */
public class CrimsonCycleSkill implements ISkillLogic {

    private static final double RADIUS = 5.0;
    private static final double COST_PERCENT = 0.10;       // 現在HPの10%を消費
    private static final double MIN_HP_PERCENT = 0.15;     // 最大HPの15%未満なら発動不可

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        if (!(caster instanceof Player player)) return false;

        var statManager = Deepwither.getInstance().getStatManager();

        double currentHp = statManager.getActualCurrentHealth(player);
        double maxHp = statManager.getActualMaxHealth(player);
        double minHp = maxHp * MIN_HP_PERCENT;

        // HPが少なすぎる場合は発動拒否
        if (currentHp <= minHp) {
            player.sendMessage(Component.text(">>> ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("HPが足りません！", NamedTextColor.DARK_RED))
                    .append(Component.text(" 深緋の輪の代償を支払えない...", NamedTextColor.GRAY)));
            return false;
        }

        double bloodCost = currentHp * COST_PERCENT;

        // ===== 1. キャスター自傷 (血の代償) =====
        DamageContext selfCtx = new DamageContext(null, player,
                DeepwitherDamageEvent.DamageType.MAGIC, bloodCost);
        selfCtx.setTrueDamage(true);
        Deepwither.getInstance().getDamageProcessor().process(selfCtx);

        // ===== 2. 周囲5ブロックの敵に魔法ダメージ =====
        Location center = player.getLocation();
        Collection<Entity> nearby = center.getWorld().getNearbyEntities(center, RADIUS, RADIUS, RADIUS);

        int hitCount = 0;
        for (Entity e : nearby) {
            if (!(e instanceof LivingEntity target)) continue;
            if (e.equals(player)) continue;
            // モブまたは敵プレイヤーのみ対象
            if (!(e instanceof Monster) && !(e instanceof Player)) continue;

            DamageContext ctx = new DamageContext(player, target,
                    DeepwitherDamageEvent.DamageType.MAGIC, bloodCost);
            ctx.addTag("AOE");
            Deepwither.getInstance().getDamageProcessor().process(ctx);
            hitCount++;
        }

        // ===== 3. 演出 =====
        playEffects(player, center, hitCount);

        // ===== 4. メッセージ =====
        player.sendMessage(Component.text(">>> ", NamedTextColor.DARK_GRAY)
                .append(Component.text("深緋の輪", NamedTextColor.DARK_RED))
                .append(Component.text(" — 血の代償: ", NamedTextColor.GRAY))
                .append(Component.text((int) bloodCost + " HP", NamedTextColor.RED))
                .append(Component.text(" を捧げ、", NamedTextColor.GRAY))
                .append(Component.text(hitCount + "体", NamedTextColor.WHITE))
                .append(Component.text("に呪いを送った。", NamedTextColor.GRAY)));

        return true;
    }

    private void playEffects(Player player, Location center, int hitCount) {
        // サウンド
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.4f);
        center.getWorld().playSound(center, Sound.ITEM_TRIDENT_RETURN, 0.8f, 0.3f);
        if (hitCount > 0) {
            center.getWorld().playSound(center, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.5f);
        }

        Particle.DustOptions bloodDust     = new Particle.DustOptions(Color.RED, 2.5f);
        Particle.DustOptions darkBloodDust = new Particle.DustOptions(Color.fromRGB(100, 0, 0), 1.8f);

        // 自傷演出 (キャスター自身から血が噴き出す)
        center.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0),
                60, 0.4, 0.8, 0.4, 0.1, bloodDust);
        center.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0), 10);

        // 輪状の拡散演出 (16方向へ赤いDUSTを放射)
        for (int i = 0; i < 16; i++) {
            double angle = (2 * Math.PI / 16) * i;
            double x = Math.cos(angle) * RADIUS;
            double z = Math.sin(angle) * RADIUS;
            Location edge = center.clone().add(x, 0.5, z);

            // 中心→外縁に向かって3点のパーティクルを配置
            for (int step = 1; step <= 3; step++) {
                double frac = step / 3.0;
                Location mid = center.clone().add(x * frac, 0.5, z * frac);
                center.getWorld().spawnParticle(Particle.DUST, mid, 3, 0.05, 0.05, 0.05, 0, bloodDust);
            }
            center.getWorld().spawnParticle(Particle.DUST, edge, 5, 0.1, 0.2, 0.1, 0, darkBloodDust);
        }

        // 中央の爆発的なエフェクト
        center.getWorld().spawnParticle(Particle.WAX_OFF, center.clone().add(0, 1, 0),
                40, 0.6, 0.6, 0.6, 5.0);
        center.getWorld().spawnParticle(Particle.FALLING_DUST, center.clone().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1,
                org.bukkit.Bukkit.createBlockData(org.bukkit.Material.REDSTONE_BLOCK));
    }
}
