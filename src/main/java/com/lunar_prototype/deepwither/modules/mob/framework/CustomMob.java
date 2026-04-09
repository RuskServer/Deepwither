package com.lunar_prototype.deepwither.modules.mob.framework;

import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.skill.ISkillLogic;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * カスタムモブのロジックを定義する基底クラス
 */
public abstract class CustomMob {

    protected LivingEntity entity;
    protected UUID uuid;
    protected String mobId;
    protected int ticksLived = 0;

    /**
     * モブが初期化される際に呼び出されます
     */
    public void init(LivingEntity entity) {
        this.entity = entity;
        this.uuid = entity.getUniqueId();
        onSpawn();
    }

    public String getMobId() {
        return mobId;
    }

    public void setMobId(String mobId) {
        this.mobId = mobId;
    }

    /**
     * ロジックの更新 (1tickごと)
     */
    public void tick() {
        if (entity == null || !entity.isValid()) return;
        ticksLived++;
        onTick();
    }

    // --- フックメソッド (サブクラスでオーバーライド) ---

    public void onSpawn() {}
    public void onTick() {}
    public void onDeath() {}

    /**
     * 攻撃を与えた時の処理
     */
    public void onAttack(LivingEntity victim, DeepwitherDamageEvent event) {}

    /**
     * ダメージを受けた時の処理
     */
    public void onDamaged(LivingEntity attacker, DeepwitherDamageEvent event) {}

    // --- ユーティリティ ---

    public LivingEntity getEntity() {
        return entity;
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public Location getLocation() {
        return entity.getLocation();
    }

    public int getTicksLived() {
        return ticksLived;
    }

    public double getHealth() {
        return entity.getHealth();
    }

    public double getMaxHealth() {
        return entity.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    public void setMaxHealth(double health) {
        entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        entity.setHealth(health);
    }

    /**
     * スキルを発動します
     */
    protected boolean castSkill(ISkillLogic skill, int level) {
        return skill.cast(entity, null, level);
    }
}
