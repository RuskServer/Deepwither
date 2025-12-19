package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.event.SkillCastEvent;
import io.lumine.mythic.api.MythicPlugin;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkillCastManager {

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

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
        // 1. 基本的な発動条件（マナ残量・CD）をチェック
        if (!canCast(player, def)) return;

        // 2. 先にMythicMobsのスキルを発動させる
        // castSkillは条件(Conditions)に引っかかって不発だった場合 false を返します
        boolean isCastSuccessful = MythicBukkit.inst().getAPIHelper().castSkill(player, def.mythicSkillId);

        // 3. 発動が成功した場合のみ、コストを支払う
        if (isCastSuccessful) {
            ManaData mana = Deepwither.getInstance().getManaManager().get(player.getUniqueId());
            mana.consume(def.manaCost);

            Deepwither.getInstance().getCooldownManager().setCooldown(player.getUniqueId(), def.id);

            Bukkit.getPluginManager().callEvent(new SkillCastEvent(player));

            player.sendMessage(ChatColor.GREEN + "スキル「" + def.name + "」を発動！");
        } else {
            // 条件不一致で発動しなかった場合の処理（必要であればメッセージなど）
            player.sendMessage(ChatColor.GRAY + "発動条件を満たしていません。");
        }
    }
}

