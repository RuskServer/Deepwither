package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Hemomant Strike (血魔術師の強襲)
 * 自己強化と最大HPの30%を消費するスキル。
 */
public class HemomantStrikeSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        // サウンドとパーティクル
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.0f);
        caster.getWorld().spawnParticle(Particle.WAX_OFF, caster.getLocation().add(0, 1.5, 0), 18, 0.5, 0.5, 0.5, 4.0);

        // ポーション効果 (元のレベル3を1段階下げてレベル2にする)
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 1));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 400, 1)); // INCREASE_DAMAGE = Strength

        // 最大体力の30%を自傷 (True Damage)
        double maxHealth = caster.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healthToLose = maxHealth * 0.3;

        DamageContext selfCtx = new DamageContext(null, caster, DeepwitherDamageEvent.DamageType.MAGIC, healthToLose);
        selfCtx.setTrueDamage(true);
        Deepwither.getInstance().getDamageProcessor().process(selfCtx);

        return true;
    }
}
