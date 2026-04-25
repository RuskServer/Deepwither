package com.lunar_prototype.deepwither.modules.mob.framework;

import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.skill.ISkillLogic;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.Random;
import org.bukkit.inventory.ItemStack;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.Deepwither;

/**
 * カスタムモブのロジックを定義する基底クラス
 */
public abstract class CustomMob {

    protected LivingEntity entity;
    protected UUID uuid;
    protected String mobId;
    protected int ticksLived = 0;
    protected static final Random random = new Random();

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
        return com.lunar_prototype.deepwither.Deepwither.getInstance().getStatManager().getMobHealth(entity);
    }

    public double getMaxHealth() {
        return com.lunar_prototype.deepwither.Deepwither.getInstance().getStatManager().getMobMaxHealth(entity);
    }

    public void setMaxHealth(double health) {
        com.lunar_prototype.deepwither.Deepwither.getInstance().getStatManager().setMobMaxHealth(entity, health);
        com.lunar_prototype.deepwither.Deepwither.getInstance().getStatManager().setMobHealth(entity, health);
    }

    /**
     * スキルを発動します
     */
    protected boolean castSkill(ISkillLogic skill, int level) {
        return skill.cast(entity, null, level);
    }

    /**
     * キャッシュを利用して防具・武器を装備します。
     * キャッシュにない場合は生成してキャッシュに保存されます。
     */
    protected boolean equipIfPresent(String itemId, java.util.function.Consumer<ItemStack> slotSetter) {
        ItemStack item = DW.get(CustomMobManager.class).getCachedItem(itemId);
        if (item == null) return false;
        slotSetter.accept(item);
        return true;
    }

    /**
     * ランダムなステータスを持つ可能性があるため、キャッシュを使用せずにその都度生成してドロップします。
     */
    protected void dropIfPresent(String itemId, double chance, Location loc) {
        if (random.nextDouble() >= chance) return;
        ItemStack item = DW.items().getItem(itemId);
        if (item != null) {
            loc.getWorld().dropItemNaturally(loc, item);
        }
    }
}
