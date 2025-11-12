package com.lunar_prototype.deepwither;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CustomDamageMechanics implements ITargetedEntitySkill {
    // MythicLineConfigから読み込むべきカスタム攻撃力（StatType.ATTACK_DAMAGEのFlat値として）
    protected final int customAttackDamage;

    public CustomDamageMechanics(MythicLineConfig config) {
        // 既存の 'damage' パラメータを ATTACK_DAMAGE の Flat値として読み込む
        this.customAttackDamage = config.getInteger(new String[] {"damage", "d"}, 10);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!(data.getCaster().getEntity().getBukkitEntity() instanceof Player player)) {
            return SkillResult.ERROR; // プレイヤー以外は対象外
        }
        LivingEntity bukkitTarget = (LivingEntity) BukkitAdapter.adapt(target);

        // プラグインインスタンスを取得
        Deepwither plugin = Deepwither.getInstance();
        StatManager statManager = plugin.statManager;

        // 1. 一時バフを作成: ATTACK_DAMAGE の Flat 値としてカスタムダメージを格納
        StatMap buff = new StatMap();
        buff.setFlat(StatType.ATTACK_DAMAGE, this.customAttackDamage);

        // 2. StatManagerに一時バフを適用
        statManager.applyTemporaryBuff(player.getUniqueId(), buff);

        // 3. Bukkitのダメージイベントをトリガー（近接攻撃を模倣）
        //    これにより、DamageManagerの onMeleeDamage が発火する。
        bukkitTarget.damage(0.01, player); // ダメージ値は0.01など非常に小さく設定（キャンセルされる前提）

        // 4. 次のティックでバフを削除: 処理の完了を待つために非同期処理を使用
        new BukkitRunnable() {
            @Override
            public void run() {
                statManager.removeTemporaryBuff(player.getUniqueId());
            }
        }.runTaskLater(plugin, 1L); // 1ティック後に実行

        return SkillResult.SUCCESS;
    }
}
