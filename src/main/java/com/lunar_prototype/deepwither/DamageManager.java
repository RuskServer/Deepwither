package com.lunar_prototype.deepwither;

import io.lumine.mythic.bukkit.events.MythicDamageEvent;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DamageManager implements Listener {

    // ダメージ適用中の再帰呼び出しを防ぐためのフラグ
    private final Set<UUID> isProcessingDamage = new HashSet<>();

    @EventHandler
    public void onMythicDamage(MythicDamageEvent e){
        if (!(e.getCaster().getEntity().getBukkitEntity() instanceof Player player)) return;
        if (!((e.getTarget().getBukkitEntity() instanceof LivingEntity targetLiving))) return;

        if (e.getDamageMetadata().getDamageCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION){
            StatMap attacker = StatManager.getTotalStatsFromEquipment(player);
            StatMap defender = (targetLiving instanceof Player p)
                    ? StatManager.getTotalStatsFromEquipment(p)
                    : new StatMap();

            double magicAttack = attacker.getFinal(StatType.MAGIC_DAMAGE);
            double magicDefense = defender.getFinal(StatType.MAGIC_RESIST);
            double magicPenetration = attacker.getFinal(StatType.MAGIC_PENETRATION);
            double critChance = attacker.getFinal(StatType.CRIT_CHANCE);
            double critDamage = attacker.getFinal(StatType.CRIT_DAMAGE);

            // 魔法クリティカル判定
            boolean isCrit = Math.random() * 100 < critChance;

            // 魔法ダメージの基本値を計算
            double baseDamage = magicAttack + e.getDamage();
            if (isCrit) {
                baseDamage *= (critDamage / 100.0);
                player.sendMessage("§6§l魔法クリティカル！");
            }

            // 魔法貫通を考慮した魔法防御の計算
            double effectiveMagicDefense = Math.max(0, magicDefense - magicPenetration);
            double magicDefenseRatio = effectiveMagicDefense / (effectiveMagicDefense + 100.0);
            double finalMagicDamage = baseDamage * (1.0 - magicDefenseRatio);

            // エフェクト・演出
            player.sendMessage("§b§l魔法ダメージ！ §c+" + Math.round(finalMagicDamage));

            e.setDamage(finalMagicDamage);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        // ダメージを適用しているプレイヤーがすでに処理中であれば、このイベントを無視する
        if (e.getDamager() instanceof Player p && isProcessingDamage.contains(p.getUniqueId())) {
            return;
        }

        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity targetLiving)) return;

        StatMap attacker = StatManager.getTotalStatsFromEquipment(player);
        StatMap defender = (targetLiving instanceof Player p)
                ? StatManager.getTotalStatsFromEquipment(p)
                : new StatMap();

        double attack = attacker.getFinal(StatType.ATTACK_DAMAGE);
        double defense = defender.getFinal(StatType.DEFENSE);
        double critChance = attacker.getFinal(StatType.CRIT_CHANCE);
        double critDamage = attacker.getFinal(StatType.CRIT_DAMAGE);

        // クリティカル判定
        boolean isCrit = Math.random() * 100 < critChance;

        if (!isCrit) {
            //player.sendMessage("§7クリティカル失敗！");
            return; // クリティカルでない場合は何もしない
        }

        // ここで元のイベントをキャンセル
        e.setCancelled(true);

        // クリティカルダメージ計算
        double baseDamage = attack * (critDamage / 100.0);
        double defenseRatio = defense / (defense + 100.0);
        double finalDamage = baseDamage * (1.0 - defenseRatio);

        finalDamage = Math.max(0.1, finalDamage);

        // エフェクト・演出
        player.sendMessage("§6§lクリティカルヒット！ §c+" + Math.round(finalDamage));
        player.getWorld().spawnParticle(Particle.CRIT, targetLiving.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);

        // --- ダメージ適用ロジック ---
        // フラグをセットして再帰呼び出しを防止
        isProcessingDamage.add(player.getUniqueId());
        try {
            // ダメージ適用
            targetLiving.damage(finalDamage, player);
        } finally {
            // 処理が完了したらフラグを必ず解除
            isProcessingDamage.remove(player.getUniqueId());
        }
    }
}

