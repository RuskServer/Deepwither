package com.lunar_prototype.deepwither.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Deepwitherのカスタムダメージ計算が行われる際に呼び出されるイベント。
 */
public class DeepwitherDamageEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;

    private final LivingEntity victim;
    private final LivingEntity attacker;
    private double damage;
    private final boolean isMagic;
    private final DamageType type;

    public enum DamageType {
        PHYSICAL,
        MAGIC,
        PROJECTILE,
        ENVIRONMENTAL
    }

    public DeepwitherDamageEvent(@NotNull LivingEntity victim, @Nullable LivingEntity attacker, double damage, DamageType type) {
        this.victim = victim;
        this.attacker = attacker;
        this.damage = damage;
        this.type = type;
        this.isMagic = (type == DamageType.MAGIC);
    }

    @NotNull
    public LivingEntity getVictim() {
        return victim;
    }

    @Nullable
    public LivingEntity getAttacker() {
        return attacker;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public DamageType getType() {
        return type;
    }

    public boolean isMagic() {
        return isMagic;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
