package com.lunar_prototype.deepwither.listeners;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.StatType;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * オーラ系スキルの命中時効果を処理するリスナー
 */
public class SkillAuraListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAuraHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        // --- Collapse (崩落) ---
        if (Deepwither.getInstance().getAuraManager().hasAura(attacker, "collapse_aura")) {
            handleCollapse(attacker, victim, e);
        }
        // --- Scorching Slash (焦熱) ---
        else if (Deepwither.getInstance().getAuraManager().hasAura(attacker, "scorching_slash_aura")) {
            handleScorching(attacker, victim, e);
        }
    }

    private void handleCollapse(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        Deepwither.getInstance().getAuraManager().removeAura(attacker, "collapse_aura");
        executeCollapseEffect(attacker, victim);

        double baseAttack = Deepwither.getInstance().getStatManager().getTotalStats(attacker).getFinal(StatType.ATTACK_DAMAGE);
        double skillBaseDamage = (baseAttack + 30.0) * 2.0;

        DamageContext context = new DamageContext(attacker, victim, DeepwitherDamageEvent.DamageType.PHYSICAL, skillBaseDamage);
        context.addTag("SKILL_COLLAPSE");
        
        if (victim instanceof Player pVictim && pVictim.isBlocking()) {
            pVictim.setCooldown(Material.SHIELD, 200);
            pVictim.getWorld().playSound(pVictim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.2f, 0.8f);
        }

        Deepwither.getInstance().getDamageProcessor().process(context);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 0));
        e.setDamage(0);
    }

    private void handleScorching(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        Deepwither.getInstance().getAuraManager().removeAura(attacker, "scorching_slash_aura");
        executeScorchingEffect(attacker, victim);

        double baseAttack = Deepwither.getInstance().getStatManager().getTotalStats(attacker).getFinal(StatType.ATTACK_DAMAGE);
        
        // 即時ダメージ (攻撃力+10) * 1.5
        double instantDamage = (baseAttack + 10.0) * 1.5;
        DamageContext context = new DamageContext(attacker, victim, DeepwitherDamageEvent.DamageType.PHYSICAL, instantDamage);
        context.addTag("SKILL_SCORCHING_SLASH");
        Deepwither.getInstance().getDamageProcessor().process(context);

        // 継続ダメージ (DoT) タスク開始
        startScorchingBurnTask(attacker, victim, baseAttack);

        e.setDamage(0);
    }

    private void executeCollapseEffect(Player attacker, LivingEntity victim) {
        Location loc = victim.getLocation();
        attacker.getWorld().playSound(loc, Sound.BLOCK_LANTERN_BREAK, 1.5f, 0.5f);
        attacker.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        attacker.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.5f);

        // 1. ゴールド・ショックウェーブ (地面を這う円)
        for (int ring = 1; ring <= 3; ring++) {
            double radius = ring * 1.5;
            int count = ring * 15;
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (int i = 0; i < count; i++) {
                        double angle = (Math.PI * 2 / count) * i;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        Location pLoc = loc.clone().add(x, 0.1, z);
                        pLoc.getWorld().spawnParticle(Particle.WAX_OFF, pLoc, 1, 0, 0, 0, 0.05);
                        if (randomChance(0.2)) {
                            pLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, pLoc, 1, 0.1, 0.1, 0.1, 0);
                        }
                    }
                }
            }.runTaskLater(Deepwither.getInstance(), (ring - 1) * 2L);
        }

        // 2. 崩落 (地形破砕)
        for (int i = 0; i < 30; i++) {
            double rx = (Math.random() - 0.5) * 4;
            double rz = (Math.random() - 0.5) * 4;
            Location spikeLoc = loc.clone().add(rx, 0, rz);
            loc.getWorld().spawnParticle(Particle.BLOCK_MARKER, spikeLoc, 1, 0, 0, 0, 0.1, Material.STONE.createBlockData());
            if (randomChance(0.3)) {
                loc.getWorld().spawnParticle(Particle.DUST, spikeLoc.add(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(org.bukkit.Color.ORANGE, 1.5f));
            }
        }
        
        // 衝撃の閃光
        loc.getWorld().spawnParticle(Particle.FLASH, loc.add(0, 1, 0), 2, 0, 0, 0, 0, Color.WHITE);
    }

    private void executeScorchingEffect(Player attacker, LivingEntity victim) {
        Location loc = victim.getLocation().add(0, 1, 0);
        attacker.getWorld().playSound(loc, Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.7f);
        attacker.getWorld().playSound(loc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.2f, 1.0f);

        // 爆炎の斬撃 (一閃)
        for (int i = 0; i < 20; i++) {
            double angle = (Math.PI * 2 / 20) * i;
            Vector v = new Vector(Math.cos(angle), (Math.random() - 0.5) * 0.5, Math.sin(angle)).multiply(2.5);
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 0, v.getX(), v.getY(), v.getZ(), 0.2);
        }
        loc.getWorld().spawnParticle(Particle.LAVA, loc, 15, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0, 0, 0, 0);
        loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 10, 0.5, 0.5, 0.5, 0.05);
    }

    private void startScorchingBurnTask(Player attacker, LivingEntity victim, double attackerPower) {
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (victim.isDead() || !victim.isValid() || count >= 8) {
                    this.cancel();
                    return;
                }

                // 継続ダメージ計算: ターゲット最大HP 2% + 攻撃力 10%
                double dotDamage = (victim.getMaxHealth() * 0.02) + (attackerPower * 0.1);
                
                DamageContext dotContext = new DamageContext(attacker, victim, DeepwitherDamageEvent.DamageType.MAGIC, dotDamage);
                dotContext.setTrueDamage(true); // 燃焼なので防御無視の真ダメージ扱い
                dotContext.addTag("DOT_BURN");
                
                Deepwither.getInstance().getDamageProcessor().process(dotContext);

                // 炎上エフェクト
                victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0.05);
                victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.5f);

                count++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 10L, 10L); // 0.5秒おき
    }

    private boolean randomChance(double chance) {
        return Math.random() < chance;
    }
}
