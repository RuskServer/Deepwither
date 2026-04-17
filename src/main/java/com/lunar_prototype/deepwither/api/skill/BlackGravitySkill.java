package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.skill.utils.SkillParticleUtil;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;

public class BlackGravitySkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // チャージ開始時の視線方向で着弾予定地点を固定
        Location eyeLoc = caster.getEyeLocation();
        Vector eyeDir = eyeLoc.getDirection();
        Location initialTargetLoc = eyeLoc.clone().add(eyeDir.clone().multiply(10.0));
        
        org.bukkit.util.RayTraceResult ray = caster.getWorld().rayTraceBlocks(eyeLoc, eyeDir, 10.0, org.bukkit.FluidCollisionMode.NEVER, true);
        if (ray != null && ray.getHitPosition() != null) {
            initialTargetLoc = ray.getHitPosition().toLocation(caster.getWorld());
        }
        
        final Location fixedTargetLoc = initialTargetLoc;

        // チャージ開始音
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.2f, 0.5f);

        new BukkitRunnable() {
            int chargeTicks = 0;
            final int maxCharge = 60; // 3秒

            @Override
            public void run() {
                if (!caster.isValid()) {
                    this.cancel();
                    return;
                }

                // --- チャージ演出 ---
                // 1. 手元の収束粒子
                Location currentEye = caster.getEyeLocation();
                Location chargeLoc = currentEye.add(currentEye.getDirection().multiply(1.0));
                caster.getWorld().spawnParticle(Particle.PORTAL, chargeLoc, 5, 0.1, 0.1, 0.1, 0.05);
                
                // 2. 着弾予定地の予兆円 (最初に固定した地点に表示)
                Particle.DustOptions indicatorDust = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 0, 0), 1.0f);
                SkillParticleUtil.drawCircleFlat(fixedTargetLoc, 5.0, 40, indicatorDust, 0.1);

                // 3. チャージ音 (徐々にピッチを上げる)
                if (chargeTicks % 5 == 0) {
                    float pitch = 0.5f + ((float) chargeTicks / maxCharge);
                    caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.6f, pitch);
                }

                if (chargeTicks >= maxCharge) {
                    this.cancel();
                    launchProjectile(caster, fixedTargetLoc, level);
                }
                chargeTicks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);

        return true;
    }

    private void launchProjectile(LivingEntity caster, Location targetLoc, int level) {
        // 発射音
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.5f);

        Location spawnLoc = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(1.0));
        Vector shootDir = targetLoc.toVector().subtract(spawnLoc.toVector());
        if (shootDir.lengthSquared() < 0.01) {
            shootDir = caster.getEyeLocation().getDirection();
        } else {
            shootDir.normalize();
        }
        
        new SkillProjectile(caster, spawnLoc, shootDir) {
            {
                this.speed = 2.0; // 弾速を少し落として視認性向上
                this.hitboxRadius = 1.0;
                this.maxTicks = 10; 
            }

            @Override
            public void onTick() {
                currentLocation.getWorld().spawnParticle(Particle.PORTAL, currentLocation, 8, 0.2, 0.2, 0.2, 0.1);
                currentLocation.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, currentLocation, 2, 0.1, 0.1, 0.1, 0.02);
            }

            @Override
            public void onHitEntity(LivingEntity target) { triggerBlackHole(); }
            @Override
            public void onHitBlock(Block block) { triggerBlackHole(); }
            
            @Override
            public void run() {
                super.run();
                if (this.isCancelled() && ticksLived >= maxTicks) {
                    triggerBlackHole();
                }
            }

            private void triggerBlackHole() {
                Location center = currentLocation.clone();
                startBlackHole(caster, center, level);
                this.cancel();
            }

        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }

    private void startBlackHole(LivingEntity caster, Location center, int level) {
        final java.util.UUID blackHoleId = java.util.UUID.randomUUID();

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 40; // 80 -> 40 (2秒) に短縮
            final java.util.Set<java.util.UUID> centeredEntities = new java.util.HashSet<>();

            @Override
            public void run() {
                if (ticks >= maxTicks || !caster.isValid()) {
                    this.cancel();
                    center.getWorld().spawnParticle(Particle.SONIC_BOOM, center, 1, 0, 0, 0, 0, Color.WHITE);
                    return;
                }

                // --- 1. パーティクル演出 ---
                center.getWorld().spawnParticle(Particle.FLASH, center, 2, 0.2, 0.2, 0.2, 0, Color.WHITE);

                for (int i = 0; i < 3; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double r = 3.5; 
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    Location edge = center.clone().add(x, (Math.random() - 0.5) * 2, z);
                    SkillParticleUtil.drawSuckParticle(center, edge, Particle.PORTAL, 0.3);
                }

                // 二重リング
                Particle.DustOptions outerDust = new Particle.DustOptions(Color.fromRGB(40, 10, 80), 2.0f);
                Particle.DustOptions innerDust = new Particle.DustOptions(Color.fromRGB(80, 20, 130), 1.2f);
                SkillParticleUtil.drawCircleFlatRotating(center, 5.0, 64, outerDust, 0.0, ticks);
                SkillParticleUtil.drawCircleFlatRotating(center, 3.0, 32, innerDust, 0.0, -ticks);

                // --- 2. ダメージ判定 (tick 0 のみ) ---
                if (ticks == 0) {
                    center.getWorld().playSound(center, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.5f, 0.5f);
                    
                    Collection<Entity> damageTargets = center.getWorld().getNearbyEntities(center, 5.0, 5.0, 5.0);
                    for (Entity e : damageTargets) {
                        if (e instanceof LivingEntity vic && !e.equals(caster)) {
                            DamageContext ctx = new DamageContext(caster, vic, DeepwitherDamageEvent.DamageType.MAGIC, 25.0 + (level * 5.0));
                            ctx.addTag("AOE");
                            Deepwither.getInstance().getDamageProcessor().process(ctx);
                        }
                    }
                }

                // --- 3. 吸引処理 ---
                Collection<Entity> pullTargets = center.getWorld().getNearbyEntities(center, 5.0, 5.0, 5.0);
                
                double pullPower = (ticks < 10) ? 0.15 : (ticks < 25) ? 0.08 : 0.03;

                com.lunar_prototype.deepwither.api.skill.aura.AuraManager auraManager = Deepwither.getInstance().getAuraManager();

                for (Entity entity : pullTargets) {
                    if (entity instanceof LivingEntity living && !entity.getUniqueId().equals(caster.getUniqueId())) {
                        
                        // シールドチェック
                        boolean protectedByShield = false;
                        for (Player shieldUser : center.getWorld().getPlayers()) {
                            if (auraManager.hasAura(shieldUser, "oath_shield")) {
                                if (shieldUser.equals(living) || shieldUser.getLocation().distanceSquared(living.getLocation()) <= 25.0) {
                                    protectedByShield = true;
                                    break;
                                }
                            }
                        }
                        if (protectedByShield) continue;

                        // --- 永久拘束ハメ対策（重力耐性） ---
                        if (living.hasMetadata("gravity_hole_id") && living.hasMetadata("gravity_immunity_end")) {
                            String lastHoleId = living.getMetadata("gravity_hole_id").get(0).asString();
                            long immuneEnd = living.getMetadata("gravity_immunity_end").get(0).asLong();
                            
                            // 別のブラックホールによる吸引、かつ免疫時間(3秒)が残っている場合は無視
                            if (!lastHoleId.equals(blackHoleId.toString()) && System.currentTimeMillis() < immuneEnd) {
                                continue;
                            }
                        }
                        
                        // 今回のブラックホールの所有権と効果時間(現在時刻+3秒)を更新
                        living.setMetadata("gravity_hole_id", new org.bukkit.metadata.FixedMetadataValue(Deepwither.getInstance(), blackHoleId.toString()));
                        living.setMetadata("gravity_immunity_end", new org.bukkit.metadata.FixedMetadataValue(Deepwither.getInstance(), System.currentTimeMillis() + 3000));

                        // 一度中心付近に吸い込まれたエンティティは、それ以上吸引しない（耐性時間は上記で更新する）
                        if (centeredEntities.contains(living.getUniqueId())) continue;

                        Vector toCenter = center.toVector().subtract(entity.getLocation().toVector());
                        double distSq = toCenter.lengthSquared();

                        // 中心(半径1.5以内)に到達したら、吸引リストから除外(centeredEntitiesに追加)
                        if (distSq < 2.25) {
                            centeredEntities.add(living.getUniqueId());
                            continue;
                        }

                        Vector currentVel = entity.getVelocity();
                        if (currentVel.lengthSquared() > 0.3) continue;

                        // --- 距離による吸引力減衰 ---
                        // 距離(0.0 ~ 5.0) に応じて、遠いほど吸引力を弱める (中心付近は1.0倍、外縁は0.3倍)
                        double distance = Math.sqrt(distSq);
                        double distanceRatio = Math.min(1.0, distance / 5.0);
                        double scaledPullPower = pullPower * (1.0 - (distanceRatio * 0.7));

                        toCenter.normalize().multiply(scaledPullPower);
                        toCenter.setY(toCenter.getY() * 0.5);
                        entity.setVelocity(currentVel.add(toCenter));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }
}
