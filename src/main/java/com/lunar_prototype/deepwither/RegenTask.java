package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;
import com.lunar_prototype.deepwither.api.item.ISpecialItemEffect;

@DependsOn({StatManager.class})
public class RegenTask extends BukkitRunnable implements IManager {

    private IStatManager statManager;
    private final JavaPlugin plugin;
    private BukkitTask task;
    // タスクが実行される間隔（秒）
    private static final double INTERVAL_SECONDS = 2.0;

    public RegenTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.statManager = Deepwither.getInstance().getStatManager();
        this.task = this.runTaskTimer(plugin, 0L, (long) (INTERVAL_SECONDS * 20));
    }

    @Override
    public void shutdown() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    @Override
    public void run() {
        for (Player player : Deepwither.getInstance().getServer().getOnlinePlayers()) {

            // 死亡していないかチェック (HPが0.0でなければ回復)
            if (statManager.getActualCurrentHealth(player) > 0.0) {
                statManager.naturalRegeneration(player, INTERVAL_SECONDS);

                // マナの自然回復
                StatMap stats = statManager.getTotalStats(player);
                double maxMana = stats.getFinal(StatType.MAX_MANA);
                double manaRegen = stats.getFinal(StatType.MANA_REGEN);

                // 基礎回復量 (最大マナの2%) + ステータスによる回復量
                double baseManaRegenPerSecond = maxMana * 0.02 + manaRegen;
                double actualManaRegen = baseManaRegenPerSecond * INTERVAL_SECONDS;

                Deepwither.getInstance().getManaManager().get(player.getUniqueId()).regen(actualManaRegen);
            }

            // 特殊効果の onTick
            SpecialItemEffectManager effectManager = Deepwither.getInstance().getSpecialItemEffectManager();
            if (effectManager != null) {
                // メインハンド・オフハンド
                handleEffectTick(player, player.getInventory().getItemInMainHand(), effectManager);
                handleEffectTick(player, player.getInventory().getItemInOffHand(), effectManager);

                // 防具
                for (ItemStack armor : player.getInventory().getArmorContents()) {
                    handleEffectTick(player, armor, effectManager);
                }

                // アーティファクト
                ArtifactManager artifactManager = Deepwither.getInstance().getArtifactManager();
                if (artifactManager != null) {
                    for (ItemStack artifact : artifactManager.getPlayerArtifacts(player)) {
                        handleEffectTick(player, artifact, effectManager);
                    }
                    // 背中装備
                    handleEffectTick(player, artifactManager.getPlayerBackpack(player), effectManager);
                }
            }
        }
    }

    private void handleEffectTick(Player player, ItemStack item, SpecialItemEffectManager manager) {
        if (item == null || item.getType().isAir()) return;
        ISpecialItemEffect effect = manager.getEffect(item);
        if (effect != null) {
            effect.onTick(player, item);
        }
    }
}
