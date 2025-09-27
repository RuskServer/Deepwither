package com.lunar_prototype.deepwither;

import io.lumine.mythic.api.MythicPlugin;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
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

        if (cd.isOnCooldown(player.getUniqueId(), def.id, def.cooldown)) {
            double rem = cd.getRemaining(player.getUniqueId(), def.id, def.cooldown);
            player.sendMessage(ChatColor.YELLOW + String.format("スキルはクールダウン中です！（残り %.1f 秒）", rem));
            return false;
        }

        return true;
    }

    public void cast(Player player, SkillDefinition def) {
        if (!canCast(player, def)) return;

        ManaData mana = Deepwither.getInstance().getManaManager().get(player.getUniqueId());
        mana.consume(def.manaCost);

        Deepwither.getInstance().getCooldownManager().setCooldown(player.getUniqueId(), def.id);

        MythicBukkit.inst().getAPIHelper().castSkill(player, def.mythicSkillId);

        player.sendMessage(ChatColor.GREEN + "スキル「" + def.name + "」を発動！");
    }
}

