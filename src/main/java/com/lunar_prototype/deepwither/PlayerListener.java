package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.aethelgard.LocationDetails;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.aethelgard.QuestProgress;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.Dialog.*;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@DependsOn({PlayerQuestManager.class})
public class PlayerListener implements Listener, IManager { // 以前のPlayerListenerをメインリスナーとして維持

    private final JavaPlugin plugin;
    private final PlayerQuestManager questManager;
    private final Map<UUID, Integer> activeLocationTasks = new HashMap<>(); // プレイヤーごとのタスクID

    private static final long CHECK_PERIOD_TICKS = 20L * 5; // 5秒ごとにチェック
    private static final double CHECK_RADIUS_SQUARED = 30.0 * 30.0; // 400.0

    public PlayerListener(JavaPlugin plugin, PlayerQuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    /**
     * MythicMobが死亡した際に、討伐クエストの進捗を更新します。
     * Priority.MONITORを使用し、他のプラグインの処理を妨げないようにします。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicMobDeath(MythicMobDeathEvent e) {
        Player killer = e.getKiller() instanceof Player ? (Player) e.getKiller() : null;
        if (killer == null)
            return;

        String mobId = e.getMobType().getInternalName();
        Location mobLocation = e.getEntity().getLocation();
        UUID killerId = killer.getUniqueId();

        // プレイヤーのクエストデータを取得
        PlayerQuestData playerData = questManager.getPlayerData(killerId);
        if (playerData == null)
            return;

        boolean progressUpdated = false;

        // 進行中の全てのクエストをチェック
        for (QuestProgress progress : playerData.getActiveQuests().values()) {
            String targetMobId = progress.getQuestDetails().getTargetMobId();

            // 1. Mob IDが一致するかチェック
            if (targetMobId.equalsIgnoreCase(mobId)) {

                // 2. 空間制約チェック: Mobの死亡地点が目標地点の半径20m以内か？
                LocationDetails locationDetails = progress.getQuestDetails().getLocationDetails();
                Location objectiveLocation = locationDetails.toBukkitLocation(); // LocationDetailsに変換メソッドが必要

                if (objectiveLocation != null && objectiveLocation.getWorld().equals(mobLocation.getWorld())) {

                    double distanceSquared = objectiveLocation.distanceSquared(mobLocation);

                    // 半径20ブロック以内 (20 * 20 = 400)
                    if (distanceSquared <= 400.0) {

                        // 3. 進捗更新
                        progressUpdated = questManager.updateQuestProgress(killer, mobId);
                        if (progressUpdated) {
                            killer.sendMessage("§a[クエスト] §eMobを討伐！ (" + progress.getCurrentCount() + "/"
                                    + progress.getQuestDetails().getRequiredQuantity() + ")");
                            break; // 1つのMob討伐で複数のクエストの進捗があるとは限らないため、breakしない方が良い場合もある
                        }
                    }
                }
            }
        }

        // 進捗があった場合、データを永続化
        if (progressUpdated) {
            questManager.savePlayerQuestData(killerId); // PlayerQuestManagerに保存メソッドが必要
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        questManager.loadPlayer(player);
        startLocationCheckTask(player); // ログイン時にタスク開始
        // --- 追加処理: Alpha版免責ダイアログを表示 ---
        showAlphaDisclaimer(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        stopLocationCheckTask(player); // ログアウト時にタスク停止
        questManager.unloadPlayer(player);
    }

    private void showAlphaDisclaimer(Player player) {
        // ダイアログの作成
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("【重要】Alphaテスト参加について", NamedTextColor.RED))
                        .canCloseWithEscape(false) // ESCキーで閉じられないようにする（同意必須の場合）
                        .body(List.of(
                                DialogBody.plainMessage(
                                        Component.text("このサーバーは現在 Alpha Prototype 段階です。\n", NamedTextColor.YELLOW)),
                                DialogBody.plainMessage(
                                        Component.text("・予期せぬバグやデータのロールバックが発生する可能性があります。", NamedTextColor.WHITE)),
                                DialogBody.plainMessage(Component.text("・製品版の品質は保証されません。", NamedTextColor.WHITE)),
                                DialogBody.plainMessage(Component.text("\n上記を理解し、テストに参加しますか？", NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.confirmation(
                        // 「同意する」ボタン
                        ActionButton.builder(Component.text("同意して参加", NamedTextColor.GREEN))
                                .tooltip(Component.text("クリックして承諾"))
                                .action(DialogAction.customClick(createAgreeCallback(),
                                        ClickCallback.Options.builder().uses(1).build()))
                                .build(),
                        // 「同意しない」ボタン
                        ActionButton.builder(Component.text("退出する", NamedTextColor.RED))
                                .tooltip(Component.text("サーバーから切断します"))
                                .action(DialogAction.customClick(createDisagreeCallback(),
                                        ClickCallback.Options.builder().uses(1).build()))
                                .build())));

        // プレイヤーにダイアログを表示
        player.showDialog(dialog);
    }

    // 同意した場合の処理（コールバック）
    private DialogActionCallback createAgreeCallback() {
        return (view, audience) -> {
            if (audience instanceof Player p) {
                p.sendMessage(Component.text("同意を確認しました。テストへのご協力ありがとうございます！", NamedTextColor.GREEN));
                // ダイアログは自動で閉じます
            }
        };
    }

    // 同意しなかった場合の処理（コールバック）
    private DialogActionCallback createDisagreeCallback() {
        return (view, audience) -> {
            if (audience instanceof Player p) {
                // プレイヤーをキックする
                p.kick(Component.text("Alpha版の規約に同意されなかったため、切断しました。", NamedTextColor.RED));
            }
        };
    }

    // ---------------------- 空間メッセージタスク ----------------------

    private void startLocationCheckTask(Player player) {
        if (activeLocationTasks.containsKey(player.getUniqueId())) {
            return;
        }

        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkPlayerLocation(player);
        }, CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS).getTaskId();

        activeLocationTasks.put(player.getUniqueId(), taskId);
    }

    private void stopLocationCheckTask(Player player) {
        Integer taskId = activeLocationTasks.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    // プレイヤーの位置をチェックし、メッセージを送信
    private void checkPlayerLocation(Player player) {
        PlayerQuestData playerData = questManager.getPlayerData(player.getUniqueId());
        if (playerData == null)
            return;

        Location playerLoc = player.getLocation();

        for (QuestProgress progress : playerData.getActiveQuests().values()) {
            // 討伐クエストのみを対象とする
            if (progress.getQuestDetails().getTargetMobId() != null) {
                LocationDetails details = progress.getQuestDetails().getLocationDetails();
                Location objectiveLoc = details.toBukkitLocation(); // LocationDetailsに変換メソッドが必要

                if (objectiveLoc == null || !objectiveLoc.getWorld().equals(playerLoc.getWorld())) {
                    continue;
                }

                // 距離チェック
                if (objectiveLoc.distanceSquared(playerLoc) <= CHECK_RADIUS_SQUARED) {
                    player.sendActionBar("§b[クエスト目標地点] §3" + details.getName() + " §bに接近中...");
                    // 1回メッセージを送ったら、同じクエストではクールダウンを設けるなどの工夫も可能
                }
            }
        }
    }
}