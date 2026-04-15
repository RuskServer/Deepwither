package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;

public class BlizzardSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // 発動音
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 0.0f);

        // ターゲット地点の決定 (最大10ブロック先)
        Location start = caster.getEyeLocation();
        Vector direction = start.getDirection();
        RayTraceResult result = caster.getWorld().rayTraceBlocks(start, direction, 10.0, FluidCollisionMode.NEVER, false);

        Location targetLocation;
        if (result != null && result.getHitBlock() != null) {
            targetLocation = result.getHitPosition().toLocation(caster.getWorld());
        } else {
            targetLocation = start.clone().add(direction.multiply(10.0));
        }

        // ブリザード開始
        startBlizzard(caster, targetLocation, level);

        return true;
    }

    private void startBlizzard(LivingEntity caster, Location center, int level) {
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 80; // 4秒間 (repeat=80)

            @Override
            public void run() {
                if (ticks >= maxTicks || !caster.isValid()) {
                    this.cancel();
                    return;
                }

                // --- 1. パーティクル演出 (Blizzard_Hit_tick) ---
                World world = center.getWorld();
                
                // ICEのブロックパーティクルリング (半径5と3)
                drawBlockRing(center, 5.0, 30, Material.ICE);
                drawBlockRing(center, 3.0, 30, Material.ICE);

                // Cloudパーティクル (ランダム位置)
                for (int i = 0; i < 2; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double r = Math.random() * 5.0;
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    Location cloudLoc = center.clone().add(x, 1.5, z);
                    world.spawnParticle(Particle.CLOUD, cloudLoc, 2, 0.1, 0.1, 0.1, 0.1);
                }

                // --- 2. 効果付与 (SLOW II) ---
                // 範囲 1〜5 ブロックのエンティティ
                Collection<Entity> targets = world.getNearbyEntities(center, 5.0, 5.0, 5.0);
                for (Entity entity : targets) {
                    if (entity instanceof LivingEntity target && !entity.equals(caster)) {
                        double distSq = entity.getLocation().distanceSquared(center);
                        if (distSq >= 1.0 * 1.0 && distSq <= 5.0 * 5.0) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2, 1));
                        }
                    }
                }

                // --- 3. 音演出 ---
                if (ticks % 2 == 0) { // 毎tickだと少しうるさすぎるかもしれないので、2tickに1回に調整 (MM準拠ならticks++毎)
                    world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 1.0f);
                }

                // --- 4. ダメージ判定 (10tickおきに計6回) ---
                if (ticks % 10 == 0 && ticks < 60) {
                    applyBlizzardDamage(caster, center, level);
                }

                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }

    private void applyBlizzardDamage(LivingEntity caster, Location center, int level) {
        // a=2; m=0.5 (基礎2 + レベル * 0.5 と推測)
        double damage = 2.0 + (level * 0.5);
        
        Collection<Entity> targets = center.getWorld().getNearbyEntities(center, 5.0, 5.0, 5.0);
        for (Entity entity : targets) {
            if (entity instanceof LivingEntity target && !entity.equals(caster)) {
                double distSq = entity.getLocation().distanceSquared(center);
                // entitiesInRingNearOrigin{min=1;max=5}
                if (distSq >= 1.0 * 1.0 && distSq <= 5.0 * 5.0) {
                    DamageContext ctx = new DamageContext(caster, target, DeepwitherDamageEvent.DamageType.MAGIC, damage);
                    ctx.addTag("AOE");
                    Deepwither.getInstance().getDamageProcessor().process(ctx);
                }
            }
        }
    }

    private void drawBlockRing(Location center, double radius, int points, Material material) {
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = center.clone().add(x, 0.1, z);
            world.spawnParticle(Particle.BLOCK, p, 3, 0.1, 0, 0.1, 0, Bukkit.createBlockData(material));
        }
    }
}
