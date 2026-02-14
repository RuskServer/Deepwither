package com.lunar_prototype.deepwither.outpost;

import com.lunar_prototype.deepwither.Deepwither;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class OutpostEvent {

    private final JavaPlugin plugin;
    private final OutpostManager manager;
    private final OutpostConfig.OutpostData outpostData;
    private final ContributionTracker tracker;
    private final Set<UUID> participants = new HashSet<>();
    private int currentWave = 0;
    private int totalMobs = 0;
    private int waveTaskId = -1;
    private int spawnTaskId = -1;
    private int initialWaitTaskId = -1;
    private static final int INITIAL_WAIT_SECONDS = 120;

    public OutpostEvent(JavaPlugin plugin, OutpostManager manager, OutpostConfig.OutpostData data, OutpostConfig.ScoreWeights weights) {
        this.plugin = plugin;
        this.manager = manager;
        this.outpostData = data;
        this.tracker = new ContributionTracker(weights);
        Deepwither.getInstance().getMobSpawnManager().disableNormalSpawning(data.getRegionName());
    }

    public void startEvent() {
        Component message = Component.text("【Outpost発生】", NamedTextColor.YELLOW, TextDecoration.BOLD)
                .append(Component.text(outpostData.getDisplayName() + " にPvEイベントが発生しました！", NamedTextColor.WHITE));

        Bukkit.broadcast(message);

        Title title = Title.title(
                Component.text("Outpost Emerged!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(outpostData.getDisplayName(), NamedTextColor.WHITE)
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f);
        }

        startInitialWaitTimer();
    }

    private void startInitialWaitTimer() {
        Bukkit.broadcast(Component.text("[Outpost] ", NamedTextColor.GRAY)
                .append(Component.text(outpostData.getDisplayName() + " は " + INITIAL_WAIT_SECONDS + "秒以内に参加者がいない場合、キャンセルされます。", NamedTextColor.WHITE)));

        this.initialWaitTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (participants.isEmpty()) {
                Bukkit.broadcast(Component.text("【Outpost】", NamedTextColor.RED)
                        .append(Component.text("参加者がいなかったため、イベント「" + outpostData.getDisplayName() + "」はキャンセルされました。", NamedTextColor.WHITE)));
                Deepwither.getInstance().getMobSpawnManager().enableNormalSpawning(outpostData.getRegionName());
                manager.endActiveEvent();
            } else {
                Bukkit.broadcast(Component.text("【Outpost】", NamedTextColor.GREEN)
                        .append(Component.text("待機時間終了！ウェーブを開始します。", NamedTextColor.WHITE)));
                runNextWave();
            }
            this.initialWaitTaskId = -1;
        }, INITIAL_WAIT_SECONDS * 20L).getTaskId();
    }

    private void runNextWave() {
        if (spawnTaskId != -1) {
            Bukkit.getScheduler().cancelTask(spawnTaskId);
            spawnTaskId = -1;
        }

        currentWave++;
        OutpostConfig.WaveData waveData = outpostData.getWaves().get(currentWave);

        if (waveData == null) {
            finishEvent();
            return;
        }

        totalMobs = spawnWaveMobs(waveData.getMobList());

        sendParticipantMessage(Component.text("--- ウェーブ " + currentWave + " / " + outpostData.getWaves().size() + " 開始 ---", NamedTextColor.GOLD, TextDecoration.BOLD));

        long durationTicks = waveData.getDurationSeconds() * 20L;
        waveTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (manager.getActiveEvent() == this && this.currentWave == currentWave) {
                if (spawnTaskId != -1) {
                    Bukkit.getScheduler().cancelTask(spawnTaskId);
                }
                forceNextWave();
            }
        }, durationTicks).getTaskId();
    }

    private void forceNextWave() {
        Deepwither.getInstance().getMobSpawnManager().removeAllOutpostMobs(outpostData.getRegionName());
        totalMobs = 0;
        sendParticipantMessage(Component.text("ウェーブ時間切れ！残存Mobを処理し、次のウェーブへ移行します。", NamedTextColor.RED));
        runNextWave();
    }

    public void addParticipant(UUID playerUUID) {
        if (participants.add(playerUUID)) {
            if (participants.size() == 1) {
                if (initialWaitTaskId != -1) {
                    Bukkit.getScheduler().cancelTask(initialWaitTaskId);
                    initialWaitTaskId = -1;
                    Bukkit.broadcast(Component.text("【Outpost】", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .append(Component.text("最初の参加者が出現したため、即座にウェーブを開始します！", NamedTextColor.WHITE)));
                    runNextWave();
                }
            }
        }
    }

    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    public OutpostConfig.OutpostData getOutpostData() {
        return outpostData;
    }

    public void sendParticipantMessage(Component message) {
        participants.stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .forEach(player -> player.sendMessage(message));
    }

    private int spawnWaveMobs(Map<String, Integer> mobList) {
        List<String> spawnQueue = new ArrayList<>();
        mobList.forEach((mobId, count) -> {
            for (int i = 0; i < count; i++) spawnQueue.add(mobId);
        });
        Collections.shuffle(spawnQueue);

        int totalToSpawn = spawnQueue.size();
        if (totalToSpawn == 0) return 0;

        final int mobsPerBatch = 3;
        final long intervalTicks = 40L;
        double fixedY = outpostData.getSpawnYCoordinate();
        final int targetWave = this.currentWave;

        spawnTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (manager.getActiveEvent() != this || this.currentWave != targetWave) {
                Bukkit.getScheduler().cancelTask(spawnTaskId);
                return;
            }
            if (spawnQueue.isEmpty()) {
                Bukkit.getScheduler().cancelTask(spawnTaskId);
                return;
            }
            int count = 0;
            while (count < mobsPerBatch && !spawnQueue.isEmpty()) {
                String mobId = spawnQueue.remove(0);
                Deepwither.getInstance().getMobSpawnManager().spawnOutpostMobs(mobId, 1, outpostData.getRegionName(), fixedY, this);
                count++;
            }
        }, 0L, intervalTicks).getTaskId();

        return totalToSpawn;
    }

    public void mobDefeated(Entity mob, UUID killer) {
        if (totalMobs > 0) totalMobs--;
        Deepwither.getInstance().getMobSpawnManager().untrackOutpostMob(mob.getUniqueId());
        tracker.addKill(killer);
        if (totalMobs <= 0) {
            if (waveTaskId != -1) Bukkit.getScheduler().cancelTask(waveTaskId);
            runNextWave();
        }
    }

    private void finishEvent() {
        Deepwither.getInstance().getMobSpawnManager().enableNormalSpawning(outpostData.getRegionName());
        manager.endActiveEvent();
    }

    public void distributeRewards() {
        List<Map.Entry<UUID, Double>> rankings = tracker.getRankings();
        if (rankings.isEmpty()) return;

        double totalScore = rankings.stream().mapToDouble(Map.Entry::getValue).sum();
        double averageScore = totalScore / rankings.size();

        sendParticipantMessage(Component.text("--- Outpost イベント終了: 貢献度ランキング ---", NamedTextColor.GREEN, TextDecoration.BOLD));

        for (int i = 0; i < rankings.size(); i++) {
            Map.Entry<UUID, Double> entry = rankings.get(i);
            Player p = Bukkit.getPlayer(entry.getKey());
            String playerName = (p != null) ? p.getName() : entry.getKey().toString().substring(0, 8);

            Component rankPrefix = Component.text((i + 1) + ". ", NamedTextColor.WHITE);
            if (i == 0) rankPrefix = Component.text("1. ", NamedTextColor.YELLOW, TextDecoration.BOLD);
            else if (i == 1) rankPrefix = Component.text("2. ", NamedTextColor.YELLOW);
            else if (i == 2) rankPrefix = Component.text("3. ", NamedTextColor.GOLD);

            sendParticipantMessage(rankPrefix.append(Component.text(playerName, NamedTextColor.WHITE))
                    .append(Component.text(" (", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.2f", entry.getValue()), NamedTextColor.AQUA))
                    .append(Component.text(" スコア)", NamedTextColor.GRAY)));
        }
        sendParticipantMessage(Component.text("--------------------------------------", NamedTextColor.GREEN).decoration(TextDecoration.STRIKETHROUGH, true));

        for (int i = 0; i < rankings.size(); i++) {
            Map.Entry<UUID, Double> entry = rankings.get(i);
            UUID playerUUID = entry.getKey();
            double score = entry.getValue();
            int rank = i + 1;

            Player p = Bukkit.getPlayer(playerUUID);
            if (p == null) continue;

            p.sendMessage(Component.text("【貢献度】", NamedTextColor.AQUA)
                    .append(Component.text("あなたの順位は ", NamedTextColor.WHITE))
                    .append(Component.text(rank + "位", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("、スコアは ", NamedTextColor.WHITE))
                    .append(Component.text(String.format("%.2f", score), NamedTextColor.AQUA))
                    .append(Component.text(" でした。", NamedTextColor.WHITE)));

            List<OutpostConfig.RewardItem> rewards;
            String rewardTier;

            if (rank <= 3) {
                rewards = outpostData.getRewards().getTopContributor();
                rewardTier = "TOP3";
            } else if (score >= averageScore * 0.5) {
                rewards = outpostData.getRewards().getAverageContributor();
                rewardTier = "AVERAGE";
            } else {
                rewards = outpostData.getRewards().getMinimumReward();
                rewardTier = "MINIMUM";
            }

            if (rewards.isEmpty()) {
                p.sendMessage(Component.text("[Outpost] ", NamedTextColor.GRAY).append(Component.text("報酬ティア: " + rewardTier + " に設定されたアイテムはありませんでした。", NamedTextColor.WHITE)));
                continue;
            }

            rewards.forEach(reward -> {
                int min = reward.getMinQuantity();
                int max = reward.getMaxQuantity();
                int quantity = Deepwither.getInstance().getRandom().nextInt(max - min + 1) + min;
                ItemStack customItem = Deepwither.getInstance().itemFactory.getCustomCountItemStack(reward.getCustomItemId(), quantity);

                if (customItem != null) {
                    p.getInventory().addItem(customItem);
                    Component itemName = customItem.hasItemMeta() && customItem.getItemMeta().hasDisplayName() ? customItem.getItemMeta().displayName() : Component.text(customItem.getType().name());
                    p.sendMessage(Component.text("[Outpost] ", NamedTextColor.GREEN)
                            .append(Component.text("報酬アイテム: ", NamedTextColor.WHITE))
                            .append(itemName.colorIfAbsent(NamedTextColor.YELLOW))
                            .append(Component.text(" x" + quantity, NamedTextColor.WHITE)));
                } else {
                    p.sendMessage(Component.text("[Outpost] ", NamedTextColor.RED).append(Component.text("報酬アイテム生成エラー: ID " + reward.getCustomItemId(), NamedTextColor.DARK_RED)));
                }
            });
        }
    }

    public ContributionTracker getTracker() { return tracker; }
    public String getOutpostRegionId() { return outpostData.getRegionName(); }
}
