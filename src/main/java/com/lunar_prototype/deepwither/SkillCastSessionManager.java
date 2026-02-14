package com.lunar_prototype.deepwither;

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
    private final Map<UUID, Integer> skillSlotOffsetMap = new HashMap<>();
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

                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (!skillModePlayers.contains(uuid)) continue;

                    int heldSlot = player.getInventory().getHeldItemSlot();
                    int offset = (heldSlot >= 0 && heldSlot <= 3) ? 1 : 0;
                    skillSlotOffsetMap.put(uuid, offset);

                    SkillSlotData slotData = slotManager.get(uuid);
                    Component actionBar = Component.empty();

                    for (int i = 0; i < 4; i++) {
                        String skillId = slotData.getSkill(i);
                        int displayKey = i + 1 + offset;

                        if (displayKey > 9) continue;

                        Component keyPrefix = Component.text("[", NamedTextColor.GRAY)
                                .append(Component.text(displayKey, NamedTextColor.YELLOW))
                                .append(Component.text("] ", NamedTextColor.GRAY));

                        if (skillId == null) {
                            actionBar = actionBar.append(keyPrefix).append(Component.text("----  ", NamedTextColor.DARK_GRAY));
                            continue;
                        }

                        SkillDefinition def = skillLoader.get(skillId);
                        if (def == null) {
                            actionBar = actionBar.append(keyPrefix).append(Component.text("ERROR  ", NamedTextColor.RED));
                            continue;
                        }

                        boolean onCooldown = cooldownManager.isOnCooldown(uuid, skillId, def.cooldown, def.cooldown_min);
                        boolean notEnoughMana = manaManager.get(uuid).getCurrentMana() < def.manaCost;

                        Component display;
                        if (onCooldown) {
                            double remaining = cooldownManager.getRemaining(uuid, skillId, def.cooldown, def.cooldown_min);
                            display = Component.text(def.name, NamedTextColor.RED)
                                    .append(Component.text(" (" + String.format("%.1f", remaining) + ")", NamedTextColor.GRAY));
                        } else if (notEnoughMana) {
                            display = Component.text(def.name, NamedTextColor.AQUA);
                        } else {
                            display = Component.text(def.name, NamedTextColor.GREEN, TextDecoration.BOLD);
                        }

                        actionBar = actionBar.append(keyPrefix).append(display).append(Component.text("  "));
                    }

                    player.sendActionBar(actionBar);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // 0.5秒ごと更新
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
        if (actionBarTask != null && !actionBarTask.isCancelled()) {
            actionBarTask.cancel();
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking()) return;

        UUID uuid = player.getUniqueId();
        event.setCancelled(true); // オフハンド切替無効化

        if (skillModePlayers.contains(uuid)) {
            skillModePlayers.remove(uuid);
            skillSlotOffsetMap.remove(uuid);
            player.sendActionBar(Component.empty());
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 2.0f);
            return;
        }

        skillModePlayers.add(uuid);
        previousSlotMap.put(uuid, player.getInventory().getHeldItemSlot());

        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 2.0f);
    }

    @EventHandler
    public void onHotbarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!skillModePlayers.contains(uuid)) return;

        int offset = skillSlotOffsetMap.getOrDefault(uuid, 0);
        int rawSlot = event.getNewSlot();
        int skillIndex = rawSlot - offset;

        if (skillIndex < 0 || skillIndex >= 4) {
            player.sendMessage(Component.text("スキルスロット外を選択しました。", NamedTextColor.RED));
            return;
        }

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

        Deepwither.getInstance().getSkillCastManager().cast(player, skill);

        int prevSlot = previousSlotMap.getOrDefault(uuid, 0);
        Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
            player.getInventory().setHeldItemSlot(prevSlot);
            previousSlotMap.remove(uuid);
        }, 2L);
    }
}
