package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

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
            }
        }
    }

    /**
     * DeepWitherプラグインでこのタスクを開始するためのヘルパー。
     * @param plugin DeepWitherインスタンス
     */
    public void start(Deepwither plugin) {
        // 2秒 (40ティック) ごとに実行
        this.runTaskTimer(plugin, 0L, (long) (INTERVAL_SECONDS * 20));
    }
}