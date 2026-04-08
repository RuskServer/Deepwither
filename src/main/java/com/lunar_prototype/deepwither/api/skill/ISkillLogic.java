package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.SkillDefinition;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 独自実装スキルのロジックを定義するインターフェース
 */
public interface ISkillLogic {
    /**
     * スキルを発動します。
     * @param caster 発動するエンティティ (プレイヤーまたはモブ)
     * @param definition スキルの基本定義 (マナ, CDなど)
     * @param level 現在のスキル取得レベル (威力計算等に使用)
     * @return 正常に発動できた場合は true (false を返すとマナやCDを消費しない)
     */
    boolean cast(LivingEntity caster, SkillDefinition definition, int level);
}
