package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

@DependsOn({SkillLoader.class, SkillSlotManager.class, CooldownManager.class, ManaManager.class, SkillCastManager.class})
public class SkillCastSessionManager implements Listener, IManager {
    private final Set<UUID> skillModePlayers = new HashSet<>();
    private final Map<UUID, Integer> previousSlotMap = new HashMap<>();
    private final JavaPlugin plugin;
    private BukkitTask actionBarTask;

    public SkillCastSessionManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.actionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                SkillLoader skillLoader = Deepwither.getInstance().getSkillLoader();
                SkillSlotManager slotManager = Deepwither.getInstance().getSkillSlotManager();
                CooldownManager cooldownManager = Deepwither.getInstance().getCooldownManager();
                ManaManager manaManager = Deepwither.getInstance().getManaManager();
                SkillCastManager castManager = Deepwither.getInstance().getSkillCastManager();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (!skillModePlayers.contains(uuid)) continue;

                    SkillSlotData slotData = slotManager.get(uuid);

                    // 詠唱中の場合は専用メッセージを表示
                    if (castManager.isCasting(player)) {
                        DW.ui(player).simpleActionBar(
                            Component.text("⟳ 詠唱中... (Fキーでキャンセル)", NamedTextColor.YELLOW)
                        );
                        continue;
                    }

                    Component actionBar = Component.empty();

                    for (int i = 0; i < 9; i++) {
                        String skillId = slotData.getSkill(i);
                        int displayKey = i + 1;

                        if (skillId == null) {
                            continue;
                        }

                        Component keyPrefix = Component.text("[", NamedTextColor.GRAY)
                                .append(Component.text(displayKey, NamedTextColor.YELLOW))
                                .append(Component.text("] ", NamedTextColor.GRAY));

                        SkillDefinition def = skillLoader.get(skillId);
                        if (def == null) {
                            actionBar = actionBar.append(keyPrefix)
                                    .append(Component.text("ERROR  ", NamedTextColor.RED));
                            continue;
                        }

                        boolean onCooldown = cooldownManager.isOnCooldown(uuid, skillId, def.cooldown, def.cooldown_min);
                        boolean notEnoughMana = manaManager.get(uuid).getCurrentMana() < def.manaCost;

                        Component display;
                        if (onCooldown) {
                            double remaining = cooldownManager.getRemaining(uuid, skillId, def.cooldown, def.cooldown_min);
                            display = Component.text(def.name, NamedTextColor.RED)
                                    .append(Component.text(" (" + String.format("%.1f", remaining) + "s)", NamedTextColor.DARK_RED));
                        } else if (notEnoughMana) {
                            // マナ不足: 青 + ✗ プレフィクスで明確に区別
                            display = Component.text("✗ ", NamedTextColor.BLUE)
                                    .append(Component.text(def.name, NamedTextColor.BLUE));
                        } else {
                            // 発動可能: 緑 + ボールド
                            display = Component.text(def.name, NamedTextColor.GREEN, TextDecoration.BOLD);
                        }

                        actionBar = actionBar.append(keyPrefix).append(display).append(Component.text("  "));
                    }

                    DW.ui(player).simpleActionBar(actionBar);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
        if (actionBarTask != null && !actionBarTask.isCancelled()) {
            actionBarTask.cancel();
        }
    }

    // ===== スキルモードの開始・終了 =====

    private void enterSkillMode(Player player) {
        UUID uuid = player.getUniqueId();
        skillModePlayers.add(uuid);
        previousSlotMap.put(uuid, player.getInventory().getHeldItemSlot());
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 2.0f);
    }

    private void exitSkillMode(Player player) {
        UUID uuid = player.getUniqueId();
        skillModePlayers.remove(uuid);

        // 詠唱中であればキャンセル
        Deepwither.getInstance().getSkillCastManager().cancelCast(player);

        DW.ui(player).simpleActionBar(Component.empty());
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 2.0f);
    }

    // ===== イベント =====

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking()) return;

        UUID uuid = player.getUniqueId();
        event.setCancelled(true); // オフハンド切替を無効化

        if (skillModePlayers.contains(uuid)) {
            exitSkillMode(player);
        } else {
            enterSkillMode(player);
        }
    }

    @EventHandler
    public void onHotbarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!skillModePlayers.contains(uuid)) return;

        // 詠唱中はホットバー操作を無視
        if (Deepwither.getInstance().getSkillCastManager().isCasting(player)) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getNewSlot();
        int skillIndex = rawSlot;

        if (skillIndex < 0 || skillIndex >= 9) {
            // スキルスロット外を選択した場合はスキルモードを終了
            exitSkillMode(player);
            return;
        }

        // スキルスロット内の操作なのでイベントをキャンセル (武器クールダウンのリセットを防止)
        event.setCancelled(true);

        String skillId = Deepwither.getInstance().getSkillSlotManager().getSkillInSlot(uuid, skillIndex);
        if (skillId == null) {
            player.sendMessage(Component.text("このスロットにはスキルが設定されていません。", NamedTextColor.RED));
            return;
        }

        SkillDefinition skill = Deepwither.getInstance().getSkillLoader().get(skillId);
        if (skill == null) {
            player.sendMessage(Component.text("スキルの読み込みに失敗しました。", NamedTextColor.RED));
            return;
        }

        // スキル発動
        Deepwither.getInstance().getSkillCastManager().cast(player, skill);

        // キャスト後もスキルモードを維持する（Fキーを再度押すまで連続キャスト可能）
    }
}
