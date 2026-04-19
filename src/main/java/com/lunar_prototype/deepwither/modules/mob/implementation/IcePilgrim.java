package com.lunar_prototype.deepwither.modules.mob.implementation;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
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
 * 演出大幅強化版
 */
public class IcePilgrim extends CustomMob {

    private BossBar bossBar;
    private final Random random = new Random();
    private boolean isPhase2 = false;
    private int skillCooldown = 0;
    private double beamAngle = 0;

    // 回転攻撃管理
    private int spinDurationLeft = 0;
    private int spinCooldown = 0;

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
            
            // フェーズ2 突入バースト演出
            Location loc = entity.getLocation();
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5);
            loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 100, 5, 2, 5, 0.1);
            loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.5f);

            // フェーズ2は移動停止 (固定砲台化)
            entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0);
        }

        // CD管理
        if (skillCooldown > 0) skillCooldown--;
        if (spinCooldown > 0) spinCooldown--;

        // 回転攻撃の更新
        if (spinDurationLeft > 0) {
            updateSpinAttack();
        } else if (skillCooldown <= 0) {
            selectAndExecuteSkill();
        }

        // 環境演出 (吹雪・オーラ)
        updateEnvironmentalEffects();
    }

    private void updateEnvironmentalEffects() {
        Location loc = entity.getLocation().add(0, 1, 0);
        World world = entity.getWorld();

        // 共通オーラ
        if (ticksLived % 2 == 0) {
            world.spawnParticle(Particle.CLOUD, loc, 5, 0.5, 1.0, 0.5, 0.02);
            world.spawnParticle(Particle.SNOWFLAKE, loc, 2, 0.5, 1.0, 0.5, 0);
        }

        if (isPhase2) {
            // フェーズ2: ブリザード
            if (ticksLived % 3 == 0) {
                // 広範囲の吹雪
                for (Player p : world.getNearbyPlayers(entity.getLocation(), 30)) {
                    p.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 2, 0), 20, 10, 5, 10, 0.05);
                    p.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 2, 0), 10, 10, 5, 10, 0.02);
                }
            }
            
            // ボスの背後の冷気の翼/輪
            double angle = (ticksLived * 0.1) % (Math.PI * 2);
            for (int i = 0; i < 2; i++) {
                double side = (i == 0 ? 1 : -1);
                Vector offset = new Vector(Math.cos(angle) * side, 1.5 + Math.sin(angle * 2) * 0.2, Math.sin(angle) * side);
                world.spawnParticle(Particle.DUST, loc.clone().add(offset), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.AQUA, 1.0f));
            }

            // 足元の霜
            if (ticksLived % 5 == 0) {
                world.spawnParticle(Particle.ITEM_SNOWBALL, entity.getLocation(), 10, 3, 0.1, 3, 0.02);
            }
        }
    }

    private void updateBossBar() {
        if (bossBar == null) return;
        bossBar.setProgress(Math.max(0, Math.min(1.0, getHealth() / getMaxHealth())));
        
        Collection<Player> nearby = entity.getWorld().getNearbyPlayers(entity.getLocation(), 40);
        bossBar.getPlayers().forEach(p -> {
            if (!nearby.contains(p)) bossBar.removePlayer(p);
        });
        nearby.forEach(p -> {
            if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
        });
    }

    private void selectAndExecuteSkill() {
        // 回転攻撃 (フェーズ2限定, CD優先)
        if (isPhase2 && spinCooldown <= 0) {
            startSpinAttack();
            skillCooldown = 300; // 発動中は他のスキルを抑える
            return;
        }

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

    private void startSpinAttack() {
        broadcastMessage("§b§l残氷の巡礼者: §3「旋れ、極限の旋律よ！ 万物は凍てつき沈黙する！」");
        spinDurationLeft = 300; // 15秒
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.5f);
    }

    private void updateSpinAttack() {
        beamAngle += 0.05;
        Location center = entity.getEyeLocation();
        
        // 環境音 (突風)
        if (spinDurationLeft % 20 == 0) {
            center.getWorld().playSound(center, Sound.ITEM_ELYTRA_FLYING, 1.5f, 0.5f);
            center.getWorld().playSound(center, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.5f);
        }

        for (int i = 0; i < 4; i++) {
            double angle = beamAngle + (i * Math.PI / 2);
            Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
            
            for (double d = 0; d < 15; d += 0.5) {
                Location point = center.clone().add(dir.clone().multiply(d));
                center.getWorld().spawnParticle(Particle.DUST, point, 3, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(Color.AQUA, 1.2f));
                if (d % 2 == 0) center.getWorld().spawnParticle(Particle.SNOWFLAKE, point, 1, 0, 0, 0, 0);
            }

            RayTraceResult ray = center.getWorld().rayTraceEntities(center, dir, 15, e -> e instanceof Player);
            if (ray != null && ray.getHitEntity() instanceof Player p) {
                DamageContext ctx = new DamageContext(entity, p, DeepwitherDamageEvent.DamageType.MAGIC, 15.0);
                Deepwither.getInstance().getDamageProcessor().process(ctx);
                p.setNoDamageTicks(0); 
            }
        }
        
        spinDurationLeft--;
        if (spinDurationLeft <= 0) {
            spinCooldown = 1200; // 60秒
        }
    }

    /**
     * ① 聖なる氷雨
     */
    private void executeHolySalvo() {
        broadcastMessage("§b§l残氷の巡礼者: §e「天より降り注げ、清浄なる光よ！」");
        Location center = entity.getLocation();
        
        // 天空の亀裂演出
        for(int i=0; i<5; i++) {
            Location crack = center.clone().add((random.nextDouble()-0.5)*15, 15, (random.nextDouble()-0.5)*15);
            center.getWorld().spawnParticle(Particle.FLASH, crack, 10, 2, 0.1, 2, 0, Color.WHITE);
        }

        for (int i = 0; i < 20; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location pLoc = center.clone().add((random.nextDouble()-0.5)*20, 15, (random.nextDouble()-0.5)*20);
                    Vector dir = new Vector(0, -1, 0);
                    
                    pLoc.getWorld().spawnParticle(Particle.FLASH, pLoc, 1, 0, 0, 0, 0, Color.WHITE);
                    pLoc.getWorld().playSound(pLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
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
                    loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 0.2, 0.2, 0.2, 0.05);
                    loc.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, loc, 15, 0.5, 0.5, 0.5, 0.1);
                    loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.8f);
                    applyRadiusDamage(loc, 4.0, 45.0, DeepwitherDamageEvent.DamageType.MAGIC);
                    this.cancel();
                    return;
                }
                loc.add(dir.clone().multiply(0.8));
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 2, 0.1, 0.1, 0.1, 0.02);
                loc.getWorld().spawnParticle(Particle.DUST, loc, 5, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.YELLOW, 1.2f));
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
            double currentHp = DW.stats().getMobHealth(p);
            double maxHp = DW.stats().getMobMaxHealth(p);
            double ratio = currentHp / maxHp;
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
                    Location hitLoc = finalTarget.getLocation();
                    hitLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, hitLoc, 8);
                    hitLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, hitLoc, 50, 2, 2, 2, 0.2);
                    hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2.0f, 0.5f);
                    hitLoc.getWorld().playSound(hitLoc, Sound.BLOCK_ANVIL_LAND, 2.0f, 0.5f);
                    
                    applyRadiusDamage(hitLoc, 5.0, 150.0, DeepwitherDamageEvent.DamageType.MAGIC);
                    this.cancel();
                    return;
                }
                
                // ターゲット拘束演出
                if (ticks < 20) {
                    finalTarget.spawnParticle(Particle.INSTANT_EFFECT, finalTarget.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.02);
                }

                Location currentSword = swordLoc.clone().subtract(0, ticks * 0.5, 0);
                finalTarget.getWorld().spawnParticle(Particle.FLASH, currentSword, 8, 0.5, 2.0, 0.5, 0.1, Color.WHITE);
                finalTarget.getWorld().spawnParticle(Particle.DUST, currentSword, 30, 0.3, 3.0, 0.3, 0, new Particle.DustOptions(Color.WHITE, 2.5f));
                
                if (ticks % 5 == 0) {
                    finalTarget.getWorld().playSound(finalTarget.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.2f, 0.5f + (ticks * 0.02f));
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
        // 飛び上がりエフェクト
        entity.getWorld().spawnParticle(Particle.EXPLOSION, entity.getLocation(), 10, 1.0, 0.1, 1.0, 0.1);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_HORSE_JUMP, 2.0f, 0.5f);
        
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
                    land.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, land, 5);
                    land.getWorld().spawnParticle(Particle.CLOUD, land, 80, 6.0, 0.5, 6.0, 0.3);
                    land.getWorld().spawnParticle(Particle.SNOWFLAKE, land, 100, 6.0, 1.0, 6.0, 0.1);
                    land.getWorld().playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                    land.getWorld().playSound(land, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.6f);
                    
                    applyRadiusDamage(land, 7.0, 80.0, DeepwitherDamageEvent.DamageType.PHYSICAL);
                    this.cancel();
                    return;
                }
                
                if (falling) {
                    entity.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, entity.getLocation(), 10, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }.runTaskTimer(Deepwither.getInstance(), 10L, 1L);
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
                        spawnHolyBolt(swordSpawn, dir);
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

        int amount = random.nextInt(4) + 5;
        ItemStack ingot = DW.items().getItem("holy_iron_ingot", amount);
        ItemStack eye = DW.items().getItem("abyssal_eye", amount);

        if (ingot != null) entity.getWorld().dropItemNaturally(entity.getLocation(), ingot);
        if (eye != null) entity.getWorld().dropItemNaturally(entity.getLocation(), eye);
    }
}
