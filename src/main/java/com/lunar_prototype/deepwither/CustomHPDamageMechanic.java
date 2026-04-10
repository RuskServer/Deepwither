package com.lunar_prototype.deepwither;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;

import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;

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
        LivingEntity caster = (LivingEntity) data.getCaster().getEntity().getBukkitEntity();

        // ターゲットがプレイヤーであるかを確認
        if (!(bukkitTarget instanceof Player)) {
            // プレイヤーではない場合は処理を終了
            return SkillResult.CONDITION_FAILED;
        }

        Player playerTarget = (Player) bukkitTarget;

        // プレイヤーの現在のカスタムHPを取得する
        double maxCustomHP = Deepwither.getInstance().getStatManager().getActualMaxHealth(playerTarget);

        // パーセントダメージを計算
        double damageAmount = maxCustomHP * this.percentDamage;

        // ダメージコンテキストを作成し、TRUE_DAMAGEフラグを立てて防御等無視する
        DamageContext context = new DamageContext(caster, playerTarget, DeepwitherDamageEvent.DamageType.MAGIC, damageAmount);
        context.setTrueDamage(true);
        context.addTag("HP_PERCENT_DAMAGE");

        // 計算された割合ダメージをプロセッサへ委譲
        Deepwither.getInstance().getDamageProcessor().process(context);

        return SkillResult.SUCCESS;
    }
}