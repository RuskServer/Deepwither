package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.aethelgard.QuestProgress;
import com.lunar_prototype.deepwither.data.DailyTaskData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TaskCommand implements CommandExecutor {

    private final Deepwither plugin;

    public TaskCommand(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        // --- コマンド実行時の表示整形 ---
        player.sendMessage("§9§l--- 進行中タスク・クエスト ---");

        displayGuildQuestStatus(player);
        player.sendMessage("§9§m--------------------------------------");
        displayDailyTaskStatus(player);
        player.sendMessage("§9§l--------------------------------------");

        return true;
    }

    // ギルドクエストの進捗を表示するヘルパーメソッド
    private void displayGuildQuestStatus(Player player) {
        PlayerQuestManager questManager = plugin.getPlayerQuestManager();
        PlayerQuestData questData = questManager.getPlayerData(player.getUniqueId());

        if (questData == null) {
            player.sendMessage("§c[ギルド] クエストデータがロードされていません。");
            return;
        }

        Map<UUID, QuestProgress> activeQuests = questData.getActiveQuests();

        player.sendMessage("§6§l[ギルドクエスト] (" + activeQuests.size() + "件)");

        if (activeQuests.isEmpty()) {
            player.sendMessage("§7現在、ギルドクエストは受注していません。");
            return;
        }

        // クエストは最大1個の制限があるため、最初のクエストのみ表示
        for (QuestProgress progress : activeQuests.values()) {
            String title = progress.getQuestDetails().getTitle();
            String mobId = progress.getQuestDetails().getTargetMobId();
            int current = progress.getCurrentCount();
            int required = progress.getQuestDetails().getRequiredQuantity();
            String locationText = progress.getQuestDetails().getLocationDetails().getLlmLocationText();

            // 表示形式
            player.sendMessage(String.format(" §a■ クエスト名: §f%s", title));
            player.sendMessage(String.format("   §7- 目標: §c%s §7を討伐 (§a%d§7/%d)", mobId, current, required));
            player.sendMessage(String.format("   §7- 場所: §b%s", locationText));

            if (progress.isComplete()) {
                player.sendMessage("   §e報告可能: §6達成済みです。ギルドに戻り報酬を受け取りましょう。");
            }
        }
    }

    // デイリータスクの進捗を表示するヘルパーメソッド
    private void displayDailyTaskStatus(Player player) {
        DailyTaskManager taskManager = plugin.getDailyTaskManager();
        DailyTaskData taskData = taskManager.getTaskData(player); // データの取得とリセットチェック

        Set<String> activeTraders = taskManager.getActiveTaskTraders(player);

        player.sendMessage("§6§l[デイリータスク] (" + activeTraders.size() + "件)");

        if (activeTraders.isEmpty()) {
            player.sendMessage("§7現在、トレーダーからのデイリータスクは受注していません。");
            return;
        }

        for (String traderId : activeTraders) {
            // progress[0] = Current Kill Count, progress[1] = Target Kill Count
            int[] progress = taskData.getProgress(traderId);
            int current = progress[0];
            int required = progress[1];

            // ★変更: DailyTaskDataから保存されたターゲットMobIDを取得
            String targetMobId = taskData.getTargetMob(traderId);
            String displayName = targetMobId.equals("bandit") ? "バンディット" : targetMobId;

            player.sendMessage(String.format(" §a■ トレーダー: §f%s", traderId));
            // ★変更: 具体的なMob名を表示するように修正
            player.sendMessage(String.format("   §7- 目標: %s 討伐 (§a%d§7/%d)", displayName, current, required));

            if (current >= required) {
                player.sendMessage("   §e報告可能: §6達成済みです。トレーダーに報告しましょう。");
            }
        }
    }
}