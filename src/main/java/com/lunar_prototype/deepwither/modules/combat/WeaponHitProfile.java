package com.lunar_prototype.deepwither.modules.combat;

public class WeaponHitProfile {
    public final HitShape shape;
    public final double baseReach;
    public final HitDetectionManager.VisualType visualType;

    WeaponHitProfile(HitShape shape, double baseReach, HitDetectionManager.VisualType visualType) {
        this.shape = shape;
        this.baseReach = baseReach;
        this.visualType = visualType;
    }
}
