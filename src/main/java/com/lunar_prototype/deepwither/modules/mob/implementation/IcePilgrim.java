package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.skill.utils.SkillParticleUtil;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMob;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Random;

/**
 * ボス: 残氷の巡礼者 (Ice Pilgrim)
 */
public class IcePilgrim extends CustomMob {

    private BossBar bossBar;
    private final Random random = new Random();
    private boolean isPhase2 = false;
    private int skillCooldown = 0;
    private double beamAngle = 0;

    @Override
    public void onSpawn() {
        setMaxHealth(12000.0);
        entity.setCustomName("§b§l残氷の巡礼者");
        entity.setCustomNameVisible(true);

        // 装備設定
        entity.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        entity.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        entity.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        entity.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        entity.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));

        // ボスバー生成
        bossBar = Bukkit.createBossBar("§b§l残氷の巡礼者", BarColor.BLUE, BarStyle.SEGMENTED_20);
        
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.5f);
    }

    @Override
    public void onTick() {
        updateBossBar();

        if (getHealth() < getMaxHealth() * 0.5 && !isPhase2) {
            isPhase2 = true;
            broadcastMessage("§b§l残氷の巡礼者: §f「この氷の静寂こそが、汝らへの救済である……」");
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_DEATH, 2.0f, 0.8f);
            
            // フェーズ2は移動停止 (固定砲台化)
            entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0);
        }

        if (skillCooldown > 0) {
            skillCooldown--;
        } else {
            selectAndExecuteSkill();
        }

        // フェーズ2常時発動: クアッド・ビーム
        if (isPhase2) {
            executeQuadBeam();
        }

        // 常に体に冷気のパーティクルを纏う
        if (ticksLived % 2 == 0) {
            entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation().add(0, 1, 0), 5, 0.5, 1.0, 0.5, 0.02);
            entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1, 0), 2, 0.4, 0.8, 0.4, 0);
        }
    }

    private void updateBossBar() {
        if (bossBar == null) return;
        bossBar.setProgress(Math.max(0, Math.min(1.0, getHealth() / getMaxHealth())));
        
        // 近くのプレイヤーを表示対象に追加
        Collection<Player> nearby = entity.getWorld().getNearbyPlayers(entity.getLocation(), 40);
        bossBar.getPlayers().forEach(p -> {
            if (!nearby.contains(p)) bossBar.removePlayer(p);
        });
        nearby.forEach(p -> {
            if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
        });
    }

    private void selectAndExecuteSkill() {
        double roll = random.nextDouble();
        
        if (isPhase2 && roll < 0.3) {
            executeSwordBarrage();
            skillCooldown = 150;
        } else if (roll < 0.4) {
            executeHolySalvo();
            skillCooldown = 100;
        } else if (roll < 0.7) {
            executeJudgement();
            skillCooldown = 200;
        } else {
            executeGroundSlam();
            skillCooldown = 120;
        }
    }

    /**
     * ① 聖なる氷雨
     */
    private void executeHolySalvo() {
        broadcastMessage("§b§l残氷の巡礼者: §e「天より降り注げ、清浄なる光よ！」");
        Location center = entity.getLocation();
        
        for (int i = 0; i < 20; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location pLoc = center.clone().add((random.nextDouble()-0.5)*20, 15, (random.nextDouble()-0.5)*20);
                    Vector dir = new Vector(0, -1, 0);
                    
                    pLoc.getWorld().spawnParticle(Particle.FLASH, pLoc, 1, 0, 0, 0, 0, Color.WHITE);
                    pLoc.getWorld().playSound(pLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);

                    // 独自のアイスボルト（聖属性エフェクト）
                    spawnHolyBolt(pLoc, dir);
                }
            }.runTaskLater(Deepwither.getInstance(), i * 2L);
        }
    }

    private void spawnHolyBolt(Location loc, Vector dir) {
        new BukkitRunnable() {
            int life = 0;
            @Override
            public void run() {
                if (life > 40 || loc.getBlock().getType().isSolid()) {
                    loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 10, 0.3, 0.3, 0.3, 0.1);
                    loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
                    applyRadiusDamage(loc, 4.0, 45.0, DeepwitherDamageEvent.DamageType.MAGIC);
                    this.cancel();
                    return;
                }
                loc.add(dir.clone().multiply(0.8));
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 2, 0.1, 0.1, 0.1, 0.02);
                loc.getWorld().spawnParticle(Particle.DUST, loc, 5, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.YELLOW, 1.0f));
                life++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }

    /**
     * ② 第七黙示録 -ジャッジメント-
     */
    private void executeJudgement() {
        Player target = null;
        double lowestHpRatio = 1.0;

        for (Player p : entity.getWorld().getNearbyPlayers(entity.getLocation(), 30)) {
            double ratio = p.getHealth() / p.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (ratio < 0.5 && ratio < lowestHpRatio) {
                target = p;
                lowestHpRatio = ratio;
            }
        }

        if (target == null) return;

        broadcastMessage("§b§l残氷の巡礼者: §c「死の淵にある者よ。その魂、我に献上せよ…… §4§l第七黙示録 -ジャッジメント-§c」");
        final Player finalTarget = target;
        Location swordLoc = finalTarget.getLocation().add(0, 20, 0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 40 || !finalTarget.isValid()) {
                    // 巨大な剣の着弾
                    Location hitLoc = finalTarget.getLocation();
                    hitLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, hitLoc, 5);
                    hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2.0f, 0.5f);
                    hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.5f);
                    
                    applyRadiusDamage(hitLoc, 5.0, 150.0, DeepwitherDamageEvent.DamageType.MAGIC);
                    this.cancel();
                    return;
                }
                
                // 剣の予兆エフェクト
                Location currentSword = swordLoc.clone().subtract(0, ticks * 0.5, 0);
                finalTarget.getWorld().spawnParticle(Particle.FLASH, currentSword, 5, 0.5, 2.0, 0.5, 0.1, Color.WHITE);
                finalTarget.getWorld().spawnParticle(Particle.DUST, currentSword, 20, 0.3, 3.0, 0.3, 0, new Particle.DustOptions(Color.WHITE, 2.0f));
                
                if (ticks % 5 == 0) {
                    finalTarget.getWorld().playSound(finalTarget.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.5f + (ticks * 0.02f));
                }
                ticks++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }

    /**
     * ③ グランドスラム
     */
    private void executeGroundSlam() {
        broadcastMessage("§b§l残氷の巡礼者: §f「地に伏せよ！」");
        entity.setVelocity(new Vector(0, 1.5, 0));
        
        new BukkitRunnable() {
            boolean falling = false;
            @Override
            public void run() {
                if (!falling && entity.getVelocity().getY() < 0) {
                    falling = true;
                    entity.setVelocity(new Vector(0, -3.0, 0));
                }
                
                if (falling && entity.isOnGround()) {
                    Location land = entity.getLocation();
                    land.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, land, 3);
                    land.getWorld().spawnParticle(Particle.CLOUD, land, 50, 5.0, 0.5, 5.0, 0.2);
                    land.getWorld().playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
                    land.getWorld().playSound(land, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.5f, 0.5f);
                    
                    applyRadiusDamage(land, 6.0, 80.0, DeepwitherDamageEvent.DamageType.PHYSICAL);
                    this.cancel();
                    return;
                }
                
                if (falling) {
                    entity.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, entity.getLocation(), 5, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }.runTaskTimer(Deepwither.getInstance(), 10L, 1L);
    }

    /**
     * ④ フェーズ2: クアッド・ビーム
     */
    private void executeQuadBeam() {
        beamAngle += 0.05; // 回転速度
        Location center = entity.getEyeLocation();
        
        for (int i = 0; i < 4; i++) {
            double angle = beamAngle + (i * Math.PI / 2);
            Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
            
            // ビームの描画
            for (double d = 0; d < 15; d += 0.5) {
                Location point = center.clone().add(dir.clone().multiply(d));
                center.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.AQUA, 0.8f));
                // END_ROD 削除 (視認性改善のため)
            }

            // 当たり判定 (RayTrace)
            RayTraceResult ray = center.getWorld().rayTraceEntities(center, dir, 15, entity -> entity instanceof Player);
            if (ray != null && ray.getHitEntity() instanceof Player p) {
                DamageContext ctx = new DamageContext(entity, p, DeepwitherDamageEvent.DamageType.MAGIC, 15.0);
                Deepwither.getInstance().getDamageProcessor().process(ctx);
                p.setNoDamageTicks(0); // 連続ヒット
            }
        }
        
        if (ticksLived % 20 == 0) {
            center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.5f);
        }
    }

    /**
     * ⑤ フェーズ2: ソード・バラージ
     */
    private void executeSwordBarrage() {
        broadcastMessage("§b§l残氷の巡礼者: §f「終焉の時は近い……」");
        Location center = entity.getEyeLocation().add(0, 2, 0);
        
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2 / 12) * i;
            Location swordSpawn = center.clone().add(Math.cos(angle)*3, 0, Math.sin(angle)*3);
            swordSpawn.getWorld().spawnParticle(Particle.FLASH, swordSpawn, 1, 0, 0, 0, 0, Color.WHITE);
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Player target = findRandomTarget();
                    if (target != null) {
                        Vector dir = target.getLocation().toVector().subtract(swordSpawn.toVector()).normalize();
                        spawnHolyBolt(swordSpawn, dir); // 氷雨と同じ弾丸を転用
                    }
                }
            }.runTaskLater(Deepwither.getInstance(), 20L + (i * 2L));
        }
    }

    private Player findRandomTarget() {
        Collection<Player> players = entity.getWorld().getNearbyPlayers(entity.getLocation(), 30);
        if (players.isEmpty()) return null;
        return players.stream().skip(random.nextInt(players.size())).findFirst().orElse(null);
    }

    private void applyRadiusDamage(Location loc, double radius, double damage, DeepwitherDamageEvent.DamageType type) {
        Collection<Entity> targets = loc.getWorld().getNearbyEntities(loc, radius, radius, radius);
        for (Entity e : targets) {
            if (e instanceof Player p && !e.equals(entity)) {
                DamageContext ctx = new DamageContext(entity, p, type, damage);
                Deepwither.getInstance().getDamageProcessor().process(ctx);
            }
        }
    }

    private void broadcastMessage(String msg) {
        for (Player p : entity.getWorld().getNearbyPlayers(entity.getLocation(), 40)) {
            p.sendMessage(msg);
        }
    }

    @Override
    public void onDeath() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        broadcastMessage("§b§l残氷の巡礼者: §f「この氷も……いつかは溶け、光に還るのだな……」");
    }
}
