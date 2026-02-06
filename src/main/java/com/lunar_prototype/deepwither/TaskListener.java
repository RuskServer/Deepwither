package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({DailyTaskManager.class})
public class TaskListener implements Listener, IManager {

    private DailyTaskManager taskManager;
    private final JavaPlugin plugin;
    private static final String DEFAULT_TASK_TRADER = "DailyTask";

    public TaskListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.taskManager = Deepwither.getInstance().getDailyTaskManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

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

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        // ブロックを跨いでいない移動は無視（負荷対策）
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
                e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player player = e.getPlayer();

        // プレイヤーが受けているアクティブなタスクを取得
        for (String traderId : taskManager.getActiveTaskTraders(player)) {
            // task_config からタスクタイプを確認 (実装に合わせて取得)
            // もし type == AREA なら
            checkAreaTask(player, traderId);
        }
    }

    private void checkAreaTask(Player player, String traderId) {
        // 専用の task_config.yml を参照
        ConfigurationSection section = taskManager.getTaskConfig().getConfigurationSection("tasks." + traderId);
        if (section == null) return;

        for (String taskKey : section.getKeys(false)) {
            ConfigurationSection config = section.getConfigurationSection(taskKey);
            if (config == null || !config.getString("type", "").equalsIgnoreCase("AREA")) continue;

            // すでに完了しているタスクなら無視（進捗[0] >= 必要[1] かどうか）
            int[] progress = taskManager.getTaskData(player).getProgress(traderId);
            if (progress[1] > 0 && progress[0] >= progress[1]) continue;

            Location targetLoc = new Location(
                    Bukkit.getWorld(config.getString("world", "world")),
                    config.getDouble("x"),
                    config.getDouble("y"),
                    config.getDouble("z")
            );
            double radius = config.getDouble("radius", 3.0);
            int seconds = config.getInt("seconds", 10);

            // 範囲内に入ったらカウント開始
            if (player.getWorld().equals(targetLoc.getWorld())) {
                if (player.getLocation().distanceSquared(targetLoc) <= (radius * radius)) {
                    taskManager.startAreaProgress(player, traderId, seconds);
                }
            }
        }
    }
}