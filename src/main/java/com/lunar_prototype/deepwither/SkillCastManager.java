package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.event.SkillCastEvent;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.api.MythicPlugin;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({ManaManager.class, CooldownManager.class})
public class SkillCastManager implements IManager {

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    public boolean canCast(Player player, SkillDefinition def) {
        ManaData mana = Deepwither.getInstance().getManaManager().get(player.getUniqueId());
        CooldownManager cd = Deepwither.getInstance().getCooldownManager();

        if (mana.getCurrentMana() < def.manaCost) {
            player.sendMessage(ChatColor.RED + "マナが足りません！");
            return false;
        }

        if (cd.isOnCooldown(player.getUniqueId(), def.id, def.cooldown,def.cooldown_min)) {
            double rem = cd.getRemaining(player.getUniqueId(), def.id, def.cooldown,def.cooldown_min);
            player.sendMessage(ChatColor.YELLOW + String.format("スキルはクールダウン中です！（残り %.1f 秒）", rem));
            return false;
        }

        return true;
    }

    public void cast(Player player, SkillDefinition def) {
        // 1. 最初のチェック
        if (!canCast(player, def)) return;

        // 2. 詠唱時間の判定
        if (def.castTime <= 0) {
            executeSkill(player, def);
        } else {
            startCasting(player, def);
        }
    }

    private void startCasting(Player player, SkillDefinition def) {
        player.sendMessage(ChatColor.YELLOW + "詠唱開始: " + def.name + " (" + def.castTime + "s)");

        // 移動速度低下を付与（強さは調整してください。4でかなり遅くなります）
        // 詠唱時間分だけ付与
        int durationTicks = (int) (def.castTime * 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 3, false, false));

        // 指定秒数後に実行
        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            if (player.isOnline()) {
                // 実行直前にもう一度チェック（詠唱中にマナが減ったりCDがリセットされる可能性考慮）
                if (canCast(player, def)) {
                    executeSkill(player, def);
                }
            }
        }, (long) (def.castTime * 20));
    }

    private void executeSkill(Player player, SkillDefinition def) {
        boolean isCastSuccessful = MythicBukkit.inst().getAPIHelper().castSkill(player, def.mythicSkillId);

        if (isCastSuccessful) {
            ManaData mana = Deepwither.getInstance().getManaManager().get(player.getUniqueId());
            mana.consume(def.manaCost);
            Deepwither.getInstance().getCooldownManager().setCooldown(player.getUniqueId(), def.id);
            Bukkit.getPluginManager().callEvent(new SkillCastEvent(player));
            player.sendMessage(ChatColor.GREEN + "スキル「" + def.name + "」を発動！");
        } else {
            player.sendMessage(ChatColor.GRAY + "発動条件を満たしていません。");
            // 失敗時にスローを解除したい場合はここでremovePotionEffect
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }
}

