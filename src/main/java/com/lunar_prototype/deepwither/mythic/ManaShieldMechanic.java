package com.lunar_prototype.deepwither.mythic;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ManaData;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ManaShieldMechanic implements ITargetedEntitySkill {

    public ManaShieldMechanic(MythicLineConfig config) {
        // 必要に応じてコンフィグから設定を読み込む場合はここ
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!target.isPlayer()) return SkillResult.INVALID_TARGET;

        Player player = (Player) target.getBukkitEntity();
        UUID uuid = player.getUniqueId();

        // 1. マナデータの取得
        ManaData mana = Deepwither.getInstance().getManaManager().get(uuid);
        if (mana == null) return SkillResult.ERROR;

        double currentMana = mana.getCurrentMana(); // 現在マナ
        double maxMana = mana.getMaxMana(); // 最大マナ

        // 2. マナ消費 (半分持っていく)
        double cost = currentMana / 2.0;
        mana.consume(cost);

        // 3. 衝撃吸収量の計算
        // 設定値
        double targetMaxMana = 1000.0;
        double maxAbsorption = 20.0; // 10ハート
        double minAbsorption = 2.0;  // 1ハート (最低値の追加)

        // 計算式: (maxMana / 1000.0) * 40.0
        double absorptionAmount = (maxMana / targetMaxMana) * maxAbsorption;

        // --- 範囲制限 (Clamping) ---

        // 上限 20ハート (40.0) に制限
        if (absorptionAmount > maxAbsorption) {
            absorptionAmount = maxAbsorption;
        }

        // 下限 1ハート (2.0) を保証
        if (absorptionAmount < minAbsorption) {
            absorptionAmount = minAbsorption;
        }

        // 元環境ではベース数値が0だったので20まで拡張する
        player.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(20);

        // 適用
        player.setAbsorptionAmount(absorptionAmount);

        return SkillResult.SUCCESS;
    }
}