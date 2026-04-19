package com.lunar_prototype.deepwither.modules.combat;

public class WeaponHitProfile {
    public final HitShape shape;
    public final double baseReach;

    WeaponHitProfile(HitShape shape, double baseReach) {
        this.shape = shape;
        this.baseReach = baseReach;
    }
}
