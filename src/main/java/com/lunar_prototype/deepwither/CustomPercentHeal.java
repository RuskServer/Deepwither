package com.lunar_prototype.deepwither;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class CustomPercentHeal implements ITargetedEntitySkill {

    // MythicLineConfigから読み込む、ターゲットの最大HPに対する回復割合 (%)
    // 例: 5.0 を設定すると、ターゲットの最大HPの 5% を回復する
    protected final double healPercent;

    public CustomPercentHeal(MythicLineConfig config) {
        // 'percent' または 'p' パラメータを読み込む (デフォルト: 5.0)
        this.healPercent = config.getDouble(new String[] {"percent", "p"}, 5.0);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        // 1. キャスター（回復を受ける側）がプレイヤーであることを確認
        if (!(data.getCaster().getEntity().getBukkitEntity() instanceof Player player)) {
            return SkillResult.ERROR;
        }

        // 2. ターゲット（回復源となる敵）がLivingEntityであることを確認
        if (!(BukkitAdapter.adapt(target) instanceof LivingEntity bukkitTarget)) {
            return SkillResult.INVALID_TARGET;
        }

        // 3. ターゲットが既に死亡している場合は回復しない
        if (bukkitTarget.isDead()) {
            return SkillResult.INVALID_TARGET;
        }

        // 4. 回復量を計算 (ターゲットの最大HPの healPercent 割合)
        double targetMaxHealth = bukkitTarget.getMaxHealth();

        // 回復量 = ターゲットの最大HP * (設定された割合 / 100)
        double healAmount = targetMaxHealth * (this.healPercent / 100.0);

        if (healAmount <= 0) {
            return SkillResult.SUCCESS; // 回復量が0以下なら終了
        }

        // 5. カスタムHPシステムに回復を適用

        Deepwither.getInstance().getStatManager().healCustomHealth(player,healAmount);

        // 便宜上のメッセージとエフェクト（実際にはカスタムHPシステム内で実行すべき）
        player.sendMessage(String.format("§a§l割合回復！ §2%.1f §aHPを回復しました。", healAmount));
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);

        return SkillResult.SUCCESS;
    }
}