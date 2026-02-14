package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.aethelgard.LocationDetails;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.aethelgard.QuestProgress;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@DependsOn({PlayerQuestManager.class})
public class PlayerListener implements Listener, IManager {

    private final JavaPlugin plugin;
    private final PlayerQuestManager questManager;
    private final Map<UUID, Integer> activeLocationTasks = new HashMap<>();

    private static final long CHECK_PERIOD_TICKS = 20L * 5;
    private static final double CHECK_RADIUS_SQUARED = 30.0 * 30.0;

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getProjectile() == null) return;

        double projectileSpeedStat = Deepwither.getInstance().getStatManager()
                .getTotalStats(player).getFinal(StatType.PROJECTILE_SPEED);

        if (projectileSpeedStat > 0) {
            double multiplier = 1.0 + (projectileSpeedStat / 100.0);
            Vector velocity = event.getProjectile().getVelocity();
            event.getProjectile().setVelocity(velocity.multiply(multiplier));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicMobDeath(MythicMobDeathEvent e) {
        Player killer = e.getKiller() instanceof Player ? (Player) e.getKiller() : null;
        if (killer == null) return;

        String mobId = e.getMobType().getInternalName();
        Location mobLocation = e.getEntity().getLocation();
        UUID killerId = killer.getUniqueId();

        PlayerQuestData playerData = questManager.getPlayerData(killerId);
        if (playerData == null) return;

        boolean progressUpdated = false;

        for (QuestProgress progress : playerData.getActiveQuests().values()) {
            String targetMobId = progress.getQuestDetails().getTargetMobId();

            if (targetMobId.equalsIgnoreCase(mobId)) {
                LocationDetails locationDetails = progress.getQuestDetails().getLocationDetails();
                Location objectiveLocation = locationDetails.toBukkitLocation();

                if (objectiveLocation != null && objectiveLocation.getWorld().equals(mobLocation.getWorld())) {
                    if (objectiveLocation.distanceSquared(mobLocation) <= 400.0) {
                        progressUpdated = questManager.updateQuestProgress(killer, mobId);
                        if (progressUpdated) {
                            killer.sendMessage(Component.text("[クエスト] ", NamedTextColor.GREEN)
                                    .append(Component.text("Mobを討伐！ (", NamedTextColor.YELLOW))
                                    .append(Component.text(progress.getCurrentCount(), NamedTextColor.GREEN))
                                    .append(Component.text("/", NamedTextColor.YELLOW))
                                    .append(Component.text(progress.getQuestDetails().getRequiredQuantity(), NamedTextColor.YELLOW))
                                    .append(Component.text(")", NamedTextColor.YELLOW)));
                            break;
                        }
                    }
                }
            }
        }

        if (progressUpdated) {
            questManager.savePlayerQuestData(killerId);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        questManager.loadPlayer(player);
        startLocationCheckTask(player);
        showAlphaDisclaimer(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        stopLocationCheckTask(player);
        questManager.unloadPlayer(player);
    }

    private void showAlphaDisclaimer(Player player) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("【重要】Alphaテスト参加について", NamedTextColor.RED, TextDecoration.BOLD))
                        .canCloseWithEscape(false)
                        .body(List.of(
                                DialogBody.plainMessage(Component.text("このサーバーは現在 Alpha Prototype 段階です。\n", NamedTextColor.YELLOW)),
                                DialogBody.plainMessage(Component.text("・予期せぬバグやデータのロールバックが発生する可能性があります。", NamedTextColor.WHITE)),
                                DialogBody.plainMessage(Component.text("・製品版の品質は保証されません。", NamedTextColor.WHITE)),
                                DialogBody.plainMessage(Component.text("\n上記を理解し、テストに参加しますか？", NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("同意して参加", NamedTextColor.GREEN))
                                .tooltip(Component.text("クリックして承諾"))
                                .action(DialogAction.customClick(createAgreeCallback(),
                                        ClickCallback.Options.builder().uses(1).build()))
                                .build(),
                        ActionButton.builder(Component.text("退出する", NamedTextColor.RED))
                                .tooltip(Component.text("サーバーから切断します"))
                                .action(DialogAction.customClick(createDisagreeCallback(),
                                        ClickCallback.Options.builder().uses(1).build()))
                                .build())));
        player.showDialog(dialog);
    }

    private DialogActionCallback createAgreeCallback() {
        return (view, audience) -> {
            if (audience instanceof Player p) {
                p.sendMessage(Component.text("同意を確認しました。テストへのご協力ありがとうございます！", NamedTextColor.GREEN));
            }
        };
    }

    private DialogActionCallback createDisagreeCallback() {
        return (view, audience) -> {
            if (audience instanceof Player p) {
                p.kick(Component.text("Alpha版の規約に同意されなかったため、切断しました。", NamedTextColor.RED));
            }
        };
    }

    private void startLocationCheckTask(Player player) {
        if (activeLocationTasks.containsKey(player.getUniqueId())) return;
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> checkPlayerLocation(player), CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS).getTaskId();
        activeLocationTasks.put(player.getUniqueId(), taskId);
    }

    private void stopLocationCheckTask(Player player) {
        Integer taskId = activeLocationTasks.remove(player.getUniqueId());
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    private void checkPlayerLocation(Player player) {
        PlayerQuestData playerData = questManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return;
        Location playerLoc = player.getLocation();
        for (QuestProgress progress : playerData.getActiveQuests().values()) {
            if (progress.getQuestDetails().getTargetMobId() != null) {
                LocationDetails details = progress.getQuestDetails().getLocationDetails();
                Location objectiveLoc = details.toBukkitLocation();
                if (objectiveLoc == null || !objectiveLoc.getWorld().equals(playerLoc.getWorld())) continue;
                if (objectiveLoc.distanceSquared(playerLoc) <= CHECK_RADIUS_SQUARED) {
                    player.sendActionBar(Component.text("[クエスト目標地点] ", NamedTextColor.AQUA)
                            .append(Component.text(details.getName(), NamedTextColor.DARK_AQUA))
                            .append(Component.text(" に接近中...", NamedTextColor.AQUA)));
                }
            }
        }
    }
}
