package com.lunar_prototype.deepwither.core.damage;

import com.lunar_prototype.deepwither.PlayerSettingsManager;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.api.event.onPlayerRecevingDamageEvent;
import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@DependsOn({StatManager.class, PlayerSettingsManager.class, com.lunar_prototype.deepwither.core.UIManager.class})
public class DamageProcessor implements IManager {

    private final JavaPlugin plugin;
    private final IStatManager statManager;
    private final com.lunar_prototype.deepwither.core.UIManager uiManager;
    private final Set<UUID> isProcessingDamage = new HashSet<>();

    public DamageProcessor(JavaPlugin plugin, IStatManager statManager, com.lunar_prototype.deepwither.core.UIManager uiManager) {
        this.plugin = plugin;
        this.statManager = statManager;
        this.uiManager = uiManager;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    /**
     * ダメージ処理のメインエントリポイント
     */
    public void process(DamageContext context) {
        LivingEntity victim = context.getVictim();
        LivingEntity attacker = context.getAttacker();

        // 1. イベント発火 (DeepwitherDamageEvent)
        DeepwitherDamageEvent dwEvent = new DeepwitherDamageEvent(
                victim, attacker, context.getFinalDamage(), context.getDamageType());
        Bukkit.getPluginManager().callEvent(dwEvent);

        if (dwEvent.isCancelled()) return;
        context.setFinalDamage(dwEvent.getDamage());

        // 2. ダメージ適用
        applyDamage(context);

        // 3. プレイヤーへの追加イベント (onPlayerRecevingDamageEvent)
        if (victim instanceof Player playerVictim && attacker != null) {
            Bukkit.getPluginManager().callEvent(new onPlayerRecevingDamageEvent(playerVictim, attacker, context.getFinalDamage()));
        }
    }

    private void applyDamage(DamageContext context) {
        LivingEntity victim = context.getVictim();
        double damage = context.getFinalDamage();
        LivingEntity attacker = context.getAttacker();

        if (victim instanceof Player player) {
            processPlayerDamageWithAbsorption(player, damage, attacker != null ? attacker.getName() : "魔法/環境");
            
            // ログ送信
            NamedTextColor prefixColor = context.isMagic() ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.RED;
            String prefixText = context.isMagic() ? "魔法被弾！" : "物理被弾！";
            uiManager.of(player).message(PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, 
                    Component.text(prefixText, prefixColor, TextDecoration.BOLD)
                            .append(Component.text(" " + Math.round(damage), NamedTextColor.RED)));
        } else {
            // モブへのダメージ
            if (attacker instanceof Player playerAttacker) {
                executeCustomMobDamage(victim, damage, playerAttacker);
            } else {
                victim.damage(damage, attacker);
            }
        }

        // --- 被弾演出のブロードキャスト (PacketEvents + Sound + Knockback) ---
        playDamageFeedback(context);
    }

    private void playDamageFeedback(DamageContext context) {
        LivingEntity victim = context.getVictim();
        LivingEntity attacker = context.getAttacker();

        // 1. PacketEvents による被弾アニメーション (赤色フラッシュ)
        WrapperPlayServerEntityAnimation hurtPacket = new WrapperPlayServerEntityAnimation(victim.getEntityId(), WrapperPlayServerEntityAnimation.EntityAnimationType.HURT);
        victim.getWorld().getNearbyPlayers(victim.getLocation(), 40).forEach(p -> {
            PacketEvents.getAPI().getPlayerManager().sendPacket(p, hurtPacket);
        });

        // 2. 音の再生 (被害者の種類に応じて)
        Sound hurtSound = (victim instanceof Player) ? Sound.ENTITY_PLAYER_HURT : Sound.ENTITY_GENERIC_HURT;
        victim.getWorld().playSound(victim.getLocation(), hurtSound, 1.0f, 1.0f);

        // 3. ノックバックの適用
        if (attacker != null) {
            Vector dir = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize();
            dir.setY(0).normalize().multiply(0.4).setY(0.2); // 軽く横に弾き、少し浮かせる
            
            // プレイヤーかつシールドなどで防御していない場合、またはモブに適用
            victim.setVelocity(dir);
        }
    }

    private void executeCustomMobDamage(LivingEntity target, double damage, Player damager) {
        isProcessingDamage.add(damager.getUniqueId());
        try {
            double currentHealth = target.getHealth();
            if (currentHealth <= damage) {
                // 即死/トドメの処理
                target.setHealth(0.5);
                target.damage(100.0, damager);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (target.isValid() && !target.isDead()) target.remove();
                }, 3L);
            } else {
                target.damage(damage, damager);
            }
        } finally {
            isProcessingDamage.remove(damager.getUniqueId());
        }
    }

    private void processPlayerDamageWithAbsorption(Player player, double damage, String sourceName) {
        double currentHealth = statManager.getActualCurrentHealth(player);
        if (currentHealth <= 0) return;

        double absorption = player.getAbsorptionAmount() * 10.0;
        if (absorption > 0) {
            if (damage <= absorption) {
                double newAbs = absorption - damage;
                player.setAbsorptionAmount((float) (newAbs / 10.0));
                uiManager.of(player).message(PlayerSettingsManager.SettingType.SHOW_MITIGATION, 
                        Component.text("シールド防御: ", NamedTextColor.YELLOW)
                                .append(Component.text("-" + Math.round(damage), NamedTextColor.YELLOW)));
                return;
            } else {
                player.setAbsorptionAmount(0f);
                uiManager.of(player).message(PlayerSettingsManager.SettingType.SHOW_MITIGATION, 
                        Component.text("シールドブレイク！", NamedTextColor.RED));
                // 残りのダメージは通常通り通るが、ここでは吸収分を差し引いた処理を継続させても良い(現状の仕様に合わせる)
            }
        }

        double newHp = currentHealth - damage;
        statManager.setActualCurrentHealth(player, newHp);
        
        if (newHp <= 0) {
            player.sendMessage(Component.text(sourceName + "に倒されました。", NamedTextColor.DARK_RED));
        } else {
            uiManager.of(player).message(PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, 
                    Component.text("-" + Math.round(damage) + " HP", NamedTextColor.RED));
        }
    }

    public boolean isProcessing(UUID uuid) {
        return isProcessingDamage.contains(uuid);
    }
}
