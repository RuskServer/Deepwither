package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.data.DailyTaskData;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class TaskListener implements Listener {

    private final DailyTaskManager taskManager;
    private static final String DEFAULT_TASK_TRADER = "DailyTask";

    public TaskListener(DailyTaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntityType() == EntityType.PLAYER) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        taskManager.updateKillProgress(killer, DEFAULT_TASK_TRADER);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicMobDeath(MythicMobDeathEvent e) {
        Player killer = e.getKiller() instanceof Player ? (Player) e.getKiller() : null;
        if (killer == null) return;

        String killedMobId = e.getMobType().getInternalName();
        DailyTaskData data = taskManager.getTaskData(killer); // キャッシュから取得

        // 現在進行中のすべてのタスクを確認
        for (String traderId : taskManager.getActiveTaskTraders(killer)) {

            // タスクに設定されているターゲットMob IDを取得
            String targetMobId = data.getTargetMob(traderId);

            boolean matched = false;

            // ケース1: ターゲットが "bandit" の場合 (従来動作: IDにbanditを含むかチェック)
            if ("bandit".equalsIgnoreCase(targetMobId)) {
                if (killedMobId.toLowerCase().contains("bandit")) {
                    matched = true;
                }
            }
            // ケース2: 特定のMythicMobがターゲットの場合 (完全一致)
            else {
                if (killedMobId.equals(targetMobId)) {
                    matched = true;
                }
            }

            if (matched) {
                taskManager.updateKillProgress(killer, traderId);
            }
        }
    }
}