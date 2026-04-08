package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.event.SkillCastEvent;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({ManaManager.class, CooldownManager.class})
public class SkillCastManager implements IManager {

    /** 詠唱中プレイヤー → 実行予定タスク */
    private final Map<UUID, BukkitTask> castingTasks = new HashMap<>();

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        // サーバー停止時に詠唱タスクをすべてキャンセル
        castingTasks.values().forEach(BukkitTask::cancel);
        castingTasks.clear();
    }

    // ===== 状態確認 =====

    /** プレイヤーが詠唱中かどうかを返す */
    public boolean isCasting(Player player) {
        return castingTasks.containsKey(player.getUniqueId());
    }

    // ===== キャスト判定 =====

    public boolean canCast(Player player, SkillDefinition def) {
        // 詠唱中は発動不可
        if (isCasting(player)) {
            player.sendMessage(Component.text("詠唱中です！", NamedTextColor.RED));
            return false;
        }

        ManaData mana = Deepwither.getInstance().getManaManager().get(player.getUniqueId());
        CooldownManager cd = Deepwither.getInstance().getCooldownManager();

        if (mana.getCurrentMana() < def.manaCost) {
            player.sendMessage(Component.text("マナが足りません！", NamedTextColor.RED));
            return false;
        }

        if (cd.isOnCooldown(player.getUniqueId(), def.id, def.cooldown, def.cooldown_min)) {
            double rem = cd.getRemaining(player.getUniqueId(), def.id, def.cooldown, def.cooldown_min);
            player.sendMessage(Component.text(String.format("スキルはクールダウン中です！（残り %.1f 秒）", rem), NamedTextColor.YELLOW));
            return false;
        }

        return true;
    }

    // ===== キャスト処理 =====

    public void cast(Player player, SkillDefinition def) {
        if (!canCast(player, def)) return;

        if (def.castTime <= 0) {
            executeSkill(player, def);
        } else {
            startCasting(player, def);
        }
    }

    private void startCasting(Player player, SkillDefinition def) {
        UUID uuid = player.getUniqueId();
        player.sendMessage(Component.text("詠唱開始: " + def.name + " (" + def.castTime + "s)", NamedTextColor.YELLOW));

        int durationTicks = (int) (def.castTime * 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks + 5, 3, false, false));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            castingTasks.remove(uuid);
            if (!player.isOnline()) return;
            // 詠唱完了直前に再チェック（CDやマナ変動を考慮）
            ManaData mana = Deepwither.getInstance().getManaManager().get(uuid);
            CooldownManager cd = Deepwither.getInstance().getCooldownManager();
            if (mana.getCurrentMana() < def.manaCost) {
                player.sendMessage(Component.text("マナが不足したため詠唱を中断しました。", NamedTextColor.RED));
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                return;
            }
            if (cd.isOnCooldown(uuid, def.id, def.cooldown, def.cooldown_min)) {
                player.sendMessage(Component.text("クールダウン中のため詠唱を中断しました。", NamedTextColor.RED));
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                return;
            }
            executeSkill(player, def);
        }, (long) (def.castTime * 20));

        castingTasks.put(uuid, task);
    }

    /**
     * 詠唱をキャンセルします。
     * スキルモード終了時や移動妨害時などから呼び出します。
     */
    public void cancelCast(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = castingTasks.remove(uuid);
        if (task != null) {
            task.cancel();
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.sendMessage(Component.text("詠唱をキャンセルしました。", NamedTextColor.GRAY));
        }
    }

    // ===== スキル実行 =====

    private void executeSkill(Player player, SkillDefinition def) {
        boolean isCastSuccessful = false;
        com.lunar_prototype.deepwither.api.skill.ISkillLogic logic = Deepwither.getInstance().getSkillRegistry().getLogic(def.id);
        
        if (logic != null) {
            SkillData data = Deepwither.getInstance().getSkilltreeManager().load(player.getUniqueId());
            int level = data.getSkillLevel(def.id);
            if (level <= 0) level = 1;
            isCastSuccessful = logic.cast(player, def, level);
        } else if (def.mythicSkillId != null && !def.mythicSkillId.isEmpty()) {
            isCastSuccessful = MythicBukkit.inst().getAPIHelper().castSkill(player, def.mythicSkillId);
        } else {
            player.sendMessage(Component.text("このスキルは未実装です。(Missing Protocol)", NamedTextColor.RED));
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            return;
        }

        if (isCastSuccessful) {
            ManaData mana = Deepwither.getInstance().getManaManager().get(player.getUniqueId());
            mana.consume(def.manaCost);
            Deepwither.getInstance().getCooldownManager().setCooldown(player.getUniqueId(), def.id);
            Bukkit.getPluginManager().callEvent(new SkillCastEvent(player));
            player.sendMessage(Component.text("スキル「" + def.name + "」を発動！", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("発動条件を満たしていません。", NamedTextColor.GRAY));
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }
}
