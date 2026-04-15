package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.skill.ISkillLogic;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

import java.util.HashMap;
import java.util.Map;

@DependsOn({})
public class SkillRegistry implements IManager {

    private final Map<String, ISkillLogic> skillLogics = new HashMap<>();

    @Override
    public void init() {
        register("fireball", new com.lunar_prototype.deepwither.api.skill.FireballSkill());
        register("meteor", new com.lunar_prototype.deepwither.api.skill.MeteorSkill());
        register("black_gravity", new com.lunar_prototype.deepwither.api.skill.BlackGravitySkill());
        register("explosion_arrow", new com.lunar_prototype.deepwither.api.skill.ExplosionArrowSkill());
        register("cluster_explosion_arrow", new com.lunar_prototype.deepwither.api.skill.ClusterExplosionArrowSkill());
        register("lightning_burst", new com.lunar_prototype.deepwither.api.skill.LightningBurstSkill());
        register("blood_surge", new com.lunar_prototype.deepwither.api.skill.BloodSurgeSkill());
        register("charge_warrior", new com.lunar_prototype.deepwither.api.skill.ChargeWarriorSkill());
        register("flash_slash", new com.lunar_prototype.deepwither.api.skill.FlashSlashSkill());
        register("frost_salvo", new com.lunar_prototype.deepwither.api.skill.FrostSalvoSkill());
        register("heat_ray", new com.lunar_prototype.deepwither.api.skill.HeatRaySkill());
        register("spread_heat_ray", new com.lunar_prototype.deepwither.api.skill.SpreadHeatRaySkill());
        register("sky_cleave", new com.lunar_prototype.deepwither.api.skill.SkyCleaveSkill());
        register("collapse", new com.lunar_prototype.deepwither.api.skill.CollapseSkill());
        register("scorching_slash", new com.lunar_prototype.deepwither.api.skill.ScorchingSlashSkill());
        register("blizzard", new com.lunar_prototype.deepwither.api.skill.BlizzardSkill());
        register("hemomant_strike", new com.lunar_prototype.deepwither.api.skill.HemomantStrikeSkill());
        register("frost_armor", new com.lunar_prototype.deepwither.api.skill.FrostArmorSkill());
        register("four_consecutive_attacks", new com.lunar_prototype.deepwither.api.skill.FourConsecutiveAttacksSkill());
        register("holy_initiation", new com.lunar_prototype.deepwither.api.skill.HolyInitiationSkill());
        register("dark_star", new com.lunar_prototype.deepwither.api.skill.DarkStarSkill());
        register("oath_shield_radiance", new com.lunar_prototype.deepwither.api.skill.OathShieldRadianceSkill());
        register("luminary_veil", new com.lunar_prototype.deepwither.api.skill.LuminaryVeilSkill());
        register("ice_shot", new com.lunar_prototype.deepwither.api.skill.IceShotSkill());
        register("greater_heal", new com.lunar_prototype.deepwither.api.skill.GreaterHealSkill());
        register("abyss_slash", new com.lunar_prototype.deepwither.api.skill.AbyssSlashSkill());
        register("graviton_accelerator_cannon", new com.lunar_prototype.deepwither.api.skill.GravitonAcceleratorCannonSkill());
        register("crimson_cycle", new com.lunar_prototype.deepwither.api.skill.CrimsonCycleSkill());
        register("blood_reversal_field", new com.lunar_prototype.deepwither.api.skill.BloodReversalFieldSkill());
        register("crimson_spear", new com.lunar_prototype.deepwither.api.skill.CrimsonSpearSkill());
        register("hemorrhage_pact", new com.lunar_prototype.deepwither.api.skill.HemorrhagePactSkill());
    }

    @Override
    public void shutdown() {
        skillLogics.clear();
    }

    /**
     * 登録されている全スキルIDのセットを返します。
     */
    public java.util.Set<String> getRegisteredSkillIds() {
        return java.util.Collections.unmodifiableSet(skillLogics.keySet());
    }

    /**
     * スキルロジックを登録します。
     * @param skillId スキル定義（yaml）のIDと一致させる必要があります
     * @param logic 実行ロジック
     */
    public void register(String skillId, ISkillLogic logic) {
        skillLogics.put(skillId, logic);
    }

    /**
     * 指定されたIDのスキルロジックを取得します。
     */
    public ISkillLogic getLogic(String skillId) {
        return skillLogics.get(skillId);
    }
}
