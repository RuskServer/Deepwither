package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.skill.utils.TargetingUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Support Heal (支援治癒)
 * ターゲットしたパーティーメンバーのHPを大幅に回復する。
 * ハイブリッド・ターゲッティングを採用し、多少のエイムズレを許容する。
 */
public class SupportHealSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        if (!(caster instanceof Player player)) return false;

        // ハイブリッド・ターゲッティングで対象を選択
        // 射程20ブロック、アシスト半径3.5ブロック
        Player target = TargetingUtil.getSupportTarget(player, 20.0, 3.5);

        if (target == null) {
            player.sendMessage(Component.text("支援対象のパーティーメンバーが見つかりませんでした。", NamedTextColor.RED));
            return false; // キャストキャンセル
        }

        // 回復処理
        com.lunar_prototype.deepwither.api.stat.IStatManager statManager = Deepwither.getInstance().getStatManager();
        double maxHealth = statManager.getActualMaxHealth(target);
        
        // レベルに応じて回復量アップ (10% + 5% * level)
        double healPercent = 0.10 + (0.05 * level);
        double healAmount = maxHealth * healPercent;
        
        statManager.heal(target, healAmount);

        // 演出
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.1);
        
        // 発動者側への通知
        player.sendMessage(Component.text("パーティーメンバー「" + target.getName() + "」を回復しました！", NamedTextColor.AQUA));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);

        return true;
    }
}
