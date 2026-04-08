package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * フロストサルボ: 氷系最上位魔法
 * ターゲット地点を包囲するように上空に多数の魔法陣を展開し、
 * そこから順次アイスボルトを降り注がせる広範囲制圧魔法。
 */
public class FrostSalvoSkill implements ISkillLogic {

    private final Random random = new Random();

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // ターゲット地点の特定 (視線方向 30ブロック先まで)
        RayTraceResult ray = caster.getWorld().rayTraceBlocks(caster.getEyeLocation(), caster.getEyeLocation().getDirection(), 30.0, FluidCollisionMode.NEVER, true);
        Location targetLoc;
        if (ray != null && ray.getHitPosition() != null) {
            targetLoc = ray.getHitPosition().toLocation(caster.getWorld());
        } else {
            targetLoc = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(24.0));
        }

        // 初期音
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 0.5f);
        caster.getWorld().playSound(targetLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);

        // 魔法陣の展開座標を算出 (半球状に 30個)
        List<Location> portalLocations = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            // 中心から少し離れた上空の半球面
            double angle = random.nextDouble() * Math.PI * 2;
            double phi = random.nextDouble() * (Math.PI / 2.5); // 真上から横にかけて
            double r = 8.0 + random.nextDouble() * 4.0; // 半径 8~12
            
            double x = r * Math.sin(phi) * Math.cos(angle);
            double z = r * Math.sin(phi) * Math.sin(angle);
            double y = r * Math.cos(phi) + 4.0; // 少し高い位置から
            
            portalLocations.add(targetLoc.clone().add(x, y, z));
        }

        // 魔法陣展開アニメーション & 射撃タスク
        new BukkitRunnable() {
            int currentIdx = 0;

            @Override
            public void run() {
                if (currentIdx >= portalLocations.size() || !caster.isValid()) {
                    this.cancel();
                    return;
                }

                Location pLoc = portalLocations.get(currentIdx);
                // 魔法陣を描画 (1tick 1個のペースで展開)
                drawAura(pLoc, targetLoc);
                caster.getWorld().playSound(pLoc, Sound.BLOCK_AMETHYST_CLUSTER_STEP, 0.5f, 1.5f);

                // 20ticks(1秒)後にその場所から射撃
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (caster.isValid()) {
                            fireIceBolt(caster, pLoc, targetLoc, level);
                        }
                    }
                }.runTaskLater(Deepwither.getInstance(), 20L);

                currentIdx++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    /**
     * ターゲットを向いた高精細な3層魔法陣を描画
     */
    private void drawAura(Location loc, Location target) {
        Vector dir = target.toVector().subtract(loc.toVector()).normalize();
        
        // 直交ベクトルの算出
        Vector v1 = (Math.abs(dir.getY()) > 0.9) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector v2 = dir.getCrossProduct(v1).normalize();
        Vector v3 = dir.getCrossProduct(v2).normalize();

        Particle.DustOptions outerDust = new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.2f);
        Particle.DustOptions innerDust = new Particle.DustOptions(Color.fromRGB(200, 240, 255), 0.8f);
        
        // 1. 外円 (r=1.0)
        for (int i = 0; i < 20; i++) {
            double angle = (Math.PI * 2 / 20) * i;
            Location point = loc.clone().add(v2.clone().multiply(Math.cos(angle))).add(v3.clone().multiply(Math.sin(angle)));
            loc.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, outerDust);
        }
        
        // 2. 内円 (r=0.5)
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2 / 12) * i;
            Location point = loc.clone().add(v2.clone().multiply(Math.cos(angle) * 0.5)).add(v3.clone().multiply(Math.sin(angle) * 0.5));
            loc.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, innerDust);
        }

        // 3. 幾何学的ライン (ひし形)
        for (double d = -0.8; d <= 0.8; d += 0.2) {
            Location p1 = loc.clone().add(v2.clone().multiply(d)).add(v3.clone().multiply(0.8 - Math.abs(d)));
            Location p2 = loc.clone().add(v2.clone().multiply(d)).add(v3.clone().multiply(-(0.8 - Math.abs(d))));
            loc.getWorld().spawnParticle(Particle.DUST, p1, 1, 0, 0, 0, 0, innerDust);
            loc.getWorld().spawnParticle(Particle.DUST, p2, 1, 0, 0, 0, 0, innerDust);
        }

        // 4. 冷気放出 (ターゲット方向への霧)
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 3, dir.getX()*0.1, dir.getY()*0.1, dir.getZ()*0.1, 0.05);
    }

    /**
     * アイスボルトの射撃
     */
    private void fireIceBolt(LivingEntity caster, Location pLoc, Location centerTarget, int level) {
        // 広範囲に散らす (半径8ブロック程度)
        double spread = 8.0;
        Location finalTarget = centerTarget.clone().add(
                (random.nextDouble() - 0.5) * spread * 2,
                (random.nextDouble() - 0.5) * 3.0, // 高低差は控えめに
                (random.nextDouble() - 0.5) * spread * 2
        );
        
        Vector direction = finalTarget.toVector().subtract(pLoc.toVector()).normalize();
        
        pLoc.getWorld().playSound(pLoc, Sound.ENTITY_SNOWBALL_THROW, 0.8f, 0.5f);
        pLoc.getWorld().playSound(pLoc, Sound.BLOCK_GLASS_BREAK, 0.5f, 2.0f);
        pLoc.getWorld().playSound(pLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.3f, 1.5f);

        new SkillProjectile(caster, pLoc, direction) {
            {
                this.speed = 1.8; // さらに高速化
                this.maxTicks = 60;
                this.hitboxRadius = 1.5;
            }

            @Override
            public void onTick() {
                currentLocation.getWorld().spawnParticle(Particle.SNOWFLAKE, currentLocation, 5, 0.1, 0.1, 0.1, 0.05);
                currentLocation.getWorld().spawnParticle(Particle.DUST, currentLocation, 8, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(Color.fromRGB(180, 230, 255), 1.2f));
                if (ticksLived % 2 == 0) {
                    currentLocation.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, currentLocation, 1, 0, 0, 0, 0.1);
                }
            }

            @Override
            public void onHitEntity(LivingEntity target) {
                explode(target.getLocation());
                this.cancel();
            }

            @Override
            public void onHitBlock(Block block) {
                explode(currentLocation);
                this.cancel();
            }

            private void explode(Location hitLoc) {
                // 地面から氷のトゲが突き出す演出 (アニメーション)
                new BukkitRunnable() {
                    int step = 0;
                    @Override
                    public void run() {
                        if (step > 5) { this.cancel(); return; }
                        double h = step * 0.5;
                        Location spikeLoc = hitLoc.clone().add(0, h, 0);
                        hitLoc.getWorld().spawnParticle(Particle.BLOCK_MARKER, spikeLoc, 10, 0.3, 0.1, 0.3, 0.02, org.bukkit.Material.ICE.createBlockData());
                        hitLoc.getWorld().spawnParticle(Particle.DUST, spikeLoc, 5, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.WHITE, 1.0f));
                        step++;
                    }
                }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

                hitLoc.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, hitLoc, 20, 0.5, 0.5, 0.5, 0.2);
                hitLoc.getWorld().playSound(hitLoc, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.5f);
                hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
                hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 2.0f);

                // ダメージ処理
                double damage = 12.0 + (level * 3.0); // 威力も少し強化
                Collection<Entity> targets = hitLoc.getWorld().getNearbyEntities(hitLoc, 4.0, 4.0, 4.0);
                for (Entity entity : targets) {
                    if (entity instanceof LivingEntity living && !entity.equals(caster)) {
                        DamageContext ctx = new DamageContext(caster, living, DeepwitherDamageEvent.DamageType.MAGIC, damage);
                        Deepwither.getInstance().getDamageProcessor().process(ctx);
                        
                        // デバフ付与
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3));
                    }
                }
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }
}
