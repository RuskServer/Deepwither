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
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@DependsOn({StatManager.class, PlayerSettingsManager.class})
public class DamageProcessor implements IManager {

    private final JavaPlugin plugin;
    private final IStatManager statManager;
    private final PlayerSettingsManager settingsManager;
    private final Set<UUID> isProcessingDamage = new HashSet<>();

    public DamageProcessor(JavaPlugin plugin, IStatManager statManager, PlayerSettingsManager settingsManager) {
        this.plugin = plugin;
        this.statManager = statManager;
        this.settingsManager = settingsManager;
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
            sendLog(player, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, 
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
                sendLog(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION, 
                        Component.text("シールド防御: ", NamedTextColor.YELLOW)
                                .append(Component.text("-" + Math.round(damage), NamedTextColor.YELLOW)));
                return;
            } else {
                player.setAbsorptionAmount(0f);
                sendLog(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION, 
                        Component.text("シールドブレイク！", NamedTextColor.RED));
                // 残りのダメージは通常通り通るが、ここでは吸収分を差し引いた処理を継続させても良い(現状の仕様に合わせる)
            }
        }

        double newHp = currentHealth - damage;
        statManager.setActualCurrentHealth(player, newHp);
        
        if (newHp <= 0) {
            player.sendMessage(Component.text(sourceName + "に倒されました。", NamedTextColor.DARK_RED));
        } else {
            sendLog(player, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, 
                    Component.text("-" + Math.round(damage) + " HP", NamedTextColor.RED));
        }
    }

    public boolean isProcessing(UUID uuid) {
        return isProcessingDamage.contains(uuid);
    }

    private void sendLog(Player player, PlayerSettingsManager.SettingType type, Component message) {
        if (settingsManager.isEnabled(player, type)) {
            player.sendMessage(message);
        }
    }
}
