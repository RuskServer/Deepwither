package com.lunar_prototype.deepwither;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SkillCastSessionManager implements Listener {
    private final Set<UUID> skillModePlayers = new HashSet<>();
    private final Map<UUID, Integer> previousSlotMap = new HashMap<>();
    private final Map<UUID, Integer> skillSlotOffsetMap = new HashMap<>();

    public SkillCastSessionManager() {
        new BukkitRunnable() {
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
                    StringBuilder sb = new StringBuilder();

                    for (int i = 0; i < 4; i++) {
                        String skillId = slotData.getSkill(i);
                        int displayKey = i + 1 + offset;

                        if (displayKey > 9) continue;

                        if (skillId == null) {
                            sb.append("[").append(displayKey).append("] §7未設定 ");
                            continue;
                        }

                        SkillDefinition def = skillLoader.get(skillId);
                        if (def == null) {
                            sb.append("[").append(displayKey).append("] §cエラー ");
                            continue;
                        }

                        boolean onCooldown = cooldownManager.isOnCooldown(uuid, skillId, def.cooldown);
                        boolean notEnoughMana = manaManager.get(uuid).getCurrentMana() < def.manaCost;

                        String display;
                        if (onCooldown) {
                            double remaining = cooldownManager.getRemaining(uuid, skillId, def.cooldown);
                            display = "§c" + def.name + String.format("(%.1f)", remaining);
                        } else if (notEnoughMana) {
                            display = "§9" + def.name;
                        } else {
                            display = def.name;
                        }

                        sb.append("[").append(displayKey).append("] ").append(display).append(" ");
                    }

                    player.sendActionBar(sb.toString().trim());
                }
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 10L); // 0.5秒ごと更新
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
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 2.0f);
            return;
        }

        skillModePlayers.add(uuid);
        previousSlotMap.put(uuid, player.getInventory().getHeldItemSlot());

        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 2.0f);

        //player.sendTitle(ChatColor.GOLD + "スキル発動モード", ChatColor.GRAY + "スロットでスキルを選択", 5, 40, 10);
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
            player.sendMessage(ChatColor.RED + "スキルスロット外を選択しました。");
            return;
        }

        String skillId = Deepwither.getInstance().getSkillSlotManager().getSkillInSlot(uuid, skillIndex);
        if (skillId == null) {
            player.sendMessage(ChatColor.RED + "このスロットにはスキルが設定されていません。");
            return;
        }

        SkillDefinition skill = Deepwither.getInstance().getSkillLoader().get(skillId);
        if (skill == null) {
            player.sendMessage(ChatColor.RED + "スキルの読み込みに失敗しました。");
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
