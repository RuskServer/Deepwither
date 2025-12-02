package com.lunar_prototype.deepwither;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;

public class CustomHPDamageMechanic implements ITargetedEntitySkill {

    // ダメージのパーセンテージ (例: 0.10 は 10% ダメージ)
    protected final double percentDamage;

    public CustomHPDamageMechanic(MythicLineConfig config) {
        // 設定値 'amount' (a) を取得。デフォルトは 0.10 (10%)
        // 設定ファイルでは 10% と書いてもらう想定で、内部で 0.10 に変換します
        double configValue = config.getDouble(new String[] {"amount", "a"}, 10.0);

        // 10.0 -> 0.10 へ変換
        this.percentDamage = configValue / 100.0;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        LivingEntity bukkitTarget = (LivingEntity) BukkitAdapter.adapt(target);

        // ターゲットがプレイヤーであるかを確認
        if (!(bukkitTarget instanceof Player)) {
            // プレイヤーではない場合は処理を終了
            return SkillResult.CONDITION_FAILED;
        }

        Player playerTarget = (Player) bukkitTarget;

        // プレイヤーの現在のカスタムHPを取得する（架空のメソッド）
        double maxCustomHP = Deepwither.getInstance().getStatManager().getActualMaxHealth(playerTarget);
        double currentCustomHP = Deepwither.getInstance().getStatManager().getActualCurrentHealth(playerTarget);

        // パーセントダメージを計算
        double damageAmount = maxCustomHP * this.percentDamage;

        double newCustomHP = currentCustomHP - damageAmount;

        // 計算されたダメージを適用する（架空のメソッド）
        Deepwither.getInstance().getStatManager().setActualCurrentHealth(playerTarget, newCustomHP);

        return SkillResult.SUCCESS;
    }
}