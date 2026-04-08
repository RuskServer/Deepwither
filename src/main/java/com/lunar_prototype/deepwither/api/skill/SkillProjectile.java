package com.lunar_prototype.deepwither.api.skill;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public abstract class SkillProjectile extends BukkitRunnable {

    protected final LivingEntity caster;
    protected Location currentLocation;
    protected Vector direction;

    protected double speed = 0.8; 
    protected double hitboxRadius = 1.0;
    protected int maxTicks = 100;
    protected int ticksLived = 0;

    public SkillProjectile(LivingEntity caster, Location spawnLoc, Vector direction) {
        this.caster = caster;
        this.currentLocation = spawnLoc.clone();
        this.direction = direction.clone().normalize();
    }

    @Override
    public void run() {
        if (ticksLived >= maxTicks || !caster.isValid()) {
            this.cancel();
            return;
        }

        Vector velocity = direction.clone().multiply(speed);

        // RayTrace (当たり判定) を行う
        RayTraceResult result = currentLocation.getWorld().rayTrace(
                currentLocation,
                direction,
                speed,
                FluidCollisionMode.NEVER,
                true,
                hitboxRadius,
                entity -> entity instanceof LivingEntity && !entity.getUniqueId().equals(caster.getUniqueId())
        );

        if (result != null) {
            if (result.getHitEntity() != null && result.getHitEntity() instanceof LivingEntity) {
                // エンティティにヒット
                LivingEntity hitEntity = (LivingEntity) result.getHitEntity();
                currentLocation = result.getHitPosition().toLocation(currentLocation.getWorld());
                onHitEntity(hitEntity);
                return;
            } else if (result.getHitBlock() != null) {
                // ブロックにヒット
                currentLocation = result.getHitPosition().toLocation(currentLocation.getWorld());
                onHitBlock(result.getHitBlock());
                return;
            }
        }

        // 何もヒットしなければ移動して onTick()
        currentLocation.add(velocity);
        onTick();
        ticksLived++;
    }

    /**
     * 毎tick呼び出されます（パーティクルの描画などに使用）
     */
    public abstract void onTick();

    /**
     * エンティティ衝突時に呼び出されます
     * 必要なら this.cancel() を呼び出して弾を消す事
     */
    public abstract void onHitEntity(LivingEntity target);

    /**
     * ブロック衝突時に呼び出されます
     * 必要なら this.cancel() を呼び出して弾を消す事
     */
    public abstract void onHitBlock(Block block);
}
