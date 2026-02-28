package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.aethelgard.QuestProgress;
import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.modules.economy.trader.DailyTaskManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行できます。", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("--- 進行中タスク・クエスト ---", NamedTextColor.BLUE, TextDecoration.BOLD));

        displayGuildQuestStatus(player);
        player.sendMessage(Component.text("--------------------------------------", NamedTextColor.BLUE).decoration(TextDecoration.STRIKETHROUGH, true));
        displayDailyTaskStatus(player);
        player.sendMessage(Component.text("--------------------------------------", NamedTextColor.BLUE, TextDecoration.BOLD));

        return true;
    }

    private void displayGuildQuestStatus(Player player) {
        PlayerQuestManager questManager = plugin.getPlayerQuestManager();
        PlayerQuestData questData = questManager.getPlayerData(player.getUniqueId());

        if (questData == null) {
            player.sendMessage(Component.text("[ギルド] クエストデータがロードされていません。", NamedTextColor.RED));
            return;
        }

        Map<UUID, QuestProgress> activeQuests = questData.getActiveQuests();

        player.sendMessage(Component.text("[ギルドクエスト] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("(" + activeQuests.size() + "件)", NamedTextColor.WHITE)));

        if (activeQuests.isEmpty()) {
            player.sendMessage(Component.text("現在、ギルドクエストは受注していません。", NamedTextColor.GRAY));
            return;
        }

        for (QuestProgress progress : activeQuests.values()) {
            String title = progress.getQuestDetails().getTitle();
            String mobId = progress.getQuestDetails().getTargetMobId();
            int current = progress.getCurrentCount();
            int required = progress.getQuestDetails().getRequiredQuantity();
            String locationText = progress.getQuestDetails().getLocationDetails().getLlmLocationText();

            player.sendMessage(Component.text(" ■ クエスト名: ", NamedTextColor.GREEN).append(Component.text(title, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("   - 目標: ", NamedTextColor.GRAY)
                    .append(Component.text(mobId, NamedTextColor.RED))
                    .append(Component.text(" を討伐 (", NamedTextColor.GRAY))
                    .append(Component.text(current, NamedTextColor.GREEN))
                    .append(Component.text("/", NamedTextColor.GRAY))
                    .append(Component.text(required, NamedTextColor.GRAY))
                    .append(Component.text(")", NamedTextColor.GRAY)));
            player.sendMessage(Component.text("   - 場所: ", NamedTextColor.GRAY).append(Component.text(locationText, NamedTextColor.AQUA)));

            if (progress.isComplete()) {
                player.sendMessage(Component.text("   報告可能: ", NamedTextColor.YELLOW)
                        .append(Component.text("達成済みです。ギルドに戻り報酬を受け取りましょう。", NamedTextColor.GOLD)));
            }
        }
    }

    private void displayDailyTaskStatus(Player player) {
        DailyTaskManager taskManager = plugin.getDailyTaskManager();
        DailyTaskData taskData = taskManager.getTaskData(player);

        Set<String> activeTraders = taskManager.getActiveTaskTraders(player);

        player.sendMessage(Component.text("[デイリータスク] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("(" + activeTraders.size() + "件)", NamedTextColor.WHITE)));

        if (activeTraders.isEmpty()) {
            player.sendMessage(Component.text("現在、トレーダーからのデイリータスクは受注していません。", NamedTextColor.GRAY));
            return;
        }

        for (String traderId : activeTraders) {
            int[] progress = taskData.getProgress(traderId);
            int current = progress[0];
            int required = progress[1];

            String targetMobId = taskData.getTargetMob(traderId);
            String displayName = targetMobId.equals("bandit") ? "バンディット" : targetMobId;

            player.sendMessage(Component.text(" ■ トレーダー: ", NamedTextColor.GREEN).append(Component.text(traderId, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("   - 目標: ", NamedTextColor.GRAY)
                    .append(Component.text(displayName, NamedTextColor.WHITE))
                    .append(Component.text(" 討伐 (", NamedTextColor.GRAY))
                    .append(Component.text(current, NamedTextColor.GREEN))
                    .append(Component.text("/", NamedTextColor.GRAY))
                    .append(Component.text(required, NamedTextColor.GRAY))
                    .append(Component.text(")", NamedTextColor.GRAY)));

            if (current >= required) {
                player.sendMessage(Component.text("   報告可能: ", NamedTextColor.YELLOW)
                        .append(Component.text("達成済みです。トレーダーに報告しましょう。", NamedTextColor.GOLD)));
            }
        }
    }
}
