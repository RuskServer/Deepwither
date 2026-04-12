package com.lunar_prototype.deepwither.modules.outpost;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import java.util.concurrent.TimeUnit;
import java.util.Random;

@DependsOn({OutpostConfig.class})
public class OutpostManager implements IManager {

    private final Deepwither plugin;
    private final OutpostConfig config;
    private OutpostEvent activeEvent = null;
    private long cooldownEnds = 0;
    private final Random random = new Random();
    private double currentBonusChance = 0.0;

    public OutpostManager(Deepwither plugin, OutpostConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public void init() {
        startScheduler();
    }

    @Override
    public void shutdown() {
        if (activeEvent != null) {
            // イベント強制終了などの処理
        }
    }

    /**
     * イベント抽選用のスケジューラーを開始します。
     */
    public void startScheduler() {
        long intervalTicks = config.getGlobalSettings().getCheckIntervalMinutes() * 60 * 20L;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAndStartEvent, intervalTicks, intervalTicks);
    }



    /**
     * イベント開始の抽選と条件チェックを行います。
     */
    private void checkAndStartEvent() {
        if (activeEvent != null || isCooldownActive()) return;
        if (Bukkit.getOnlinePlayers().size() < config.getGlobalSettings().getMinPlayersOnline()) return;

        double baseChance = config.getGlobalSettings().getEventChancePercent(); // 例: 20.0
        double totalChance = baseChance + currentBonusChance;

        if (random.nextDouble() * 100 < totalChance) {
            // 当選
            currentBonusChance = 0.0; // ボーナスリセット
            startRandomOutpost();
        } else {
            // ハズレたら次回の確率を5%上乗せ（設定値の25%分など）
            currentBonusChance += (baseChance * 0.25);
        }
    }

    /**
     * ランダムなOutpostを選択し、イベントを開始します。
     */
    public void startRandomOutpost() {
        // 利用可能なOutpostをリスト化
        OutpostConfig.OutpostData[] available = config.getOutposts().values().toArray(new OutpostConfig.OutpostData[0]);
        if (available.length == 0) return;

        // ランダムに一つ選択
        OutpostConfig.OutpostData selectedOutpost = available[random.nextInt(available.length)];

        // イベントの開始
        activeEvent = new OutpostEvent(plugin, this, selectedOutpost, config.getWeights());
        activeEvent.startEvent();

        plugin.getLogger().info("Outpostイベントが開始されました: " + selectedOutpost.getDisplayName());
        // プレイヤーへの通知処理などをここに追加
    }

    /**
     * アクティブなイベントを終了し、報酬を配布し、クールダウンを設定します。
     */
    public void endActiveEvent() {
        if (activeEvent == null) return;

        // 1. 報酬配布ロジックを実行
        activeEvent.distributeRewards();

        // 2. クールダウンを設定
        long cooldownMinutes = config.getGlobalSettings().getCooldownMinutes();
        cooldownEnds = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(cooldownMinutes);

        plugin.getLogger().info("Outpostイベントが終了しました。クールダウン: " + cooldownMinutes + "分");

        // 3. イベントをリセット
        activeEvent = null;
    }

    public boolean isCooldownActive() {
        return cooldownEnds > System.currentTimeMillis();
    }

    public OutpostEvent getActiveEvent() {
        return activeEvent;
    }

    public OutpostConfig getConfig() {
        return config;
    }
}