package com.lunar_prototype.deepwither.modules.dynamic_quest.objective;

import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.DynamicQuest;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class EliminateObjective implements IQuestObjective {
    private final String targetMobId;
    private final int targetAmount;

    public EliminateObjective(String targetMobId, int targetAmount) {
        this.targetMobId = targetMobId;
        this.targetAmount = targetAmount;
    }

    @Override
    public String getDescription() {
        return targetMobId + " を " + targetAmount + "体討伐する";
    }

    @Override
    public boolean isMet(DynamicQuest quest) {
        return quest.getProgressCount() >= targetAmount;
    }

    @Override
    public void onAction(DynamicQuest quest, Event event) {
        if (!(event instanceof EntityDeathEvent)) return;
        EntityDeathEvent e = (EntityDeathEvent) event;
        LivingEntity victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || !killer.getUniqueId().equals(quest.getAssignee())) return;

        String killedMobId = victim.getType().name();
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(victim.getUniqueId());
            if (activeMob.isPresent()) {
                killedMobId = activeMob.get().getMobType();
            }
        }

        if (killedMobId.equalsIgnoreCase(targetMobId)) {
            quest.setProgressCount(quest.getProgressCount() + 1);
            if (quest.getProgressCount() >= targetAmount) {
                quest.setObjectiveMet(true);
                killer.sendMessage(Component.text(">> ターゲットの排除が完了しました！NPCに報告してください。", NamedTextColor.GOLD));
            } else {
                killer.sendMessage(Component.text(">> " + targetMobId + " を排除 (" + quest.getProgressCount() + "/" + targetAmount + ")", NamedTextColor.YELLOW));
            }
        }
    }
}
