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
        register("blood_surge", new com.lunar_prototype.deepwither.api.skill.BloodSurgeSkill());
        register("charge_warrior", new com.lunar_prototype.deepwither.api.skill.ChargeWarriorSkill());
        register("flash_slash", new com.lunar_prototype.deepwither.api.skill.FlashSlashSkill());
        register("frost_salvo", new com.lunar_prototype.deepwither.api.skill.FrostSalvoSkill());
        register("heat_ray", new com.lunar_prototype.deepwither.api.skill.HeatRaySkill());
        register("spread_heat_ray", new com.lunar_prototype.deepwither.api.skill.SpreadHeatRaySkill());
        register("sky_cleave", new com.lunar_prototype.deepwither.api.skill.SkyCleaveSkill());
        register("collapse", new com.lunar_prototype.deepwither.api.skill.CollapseSkill());
        register("scorching_slash", new com.lunar_prototype.deepwither.api.skill.ScorchingSlashSkill());
    }

    @Override
    public void shutdown() {
        skillLogics.clear();
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
