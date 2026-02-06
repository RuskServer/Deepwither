package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
                    StringBuilder sb = new StringBuilder();

                    for (int i = 0; i < 4; i++) {
                        String skillId = slotData.getSkill(i);
                        int displayKey = i + 1 + offset;

                        if (displayKey > 9) continue;

                        // ■ 改善点1: キー番号の表示を統一（灰色ブラケット + 黄色数字）
                        // §7[ §e1 §7] のようになります
                        String keyPrefix = "§7[§e" + displayKey + "§7] ";

                        if (skillId == null) {
                            // 未設定は目立たないように暗い灰色で
                            sb.append(keyPrefix).append("§8----  ");
                            continue;
                        }

                        SkillDefinition def = skillLoader.get(skillId);
                        if (def == null) {
                            sb.append(keyPrefix).append("§cERROR  ");
                            continue;
                        }

                        boolean onCooldown = cooldownManager.isOnCooldown(uuid, skillId, def.cooldown, def.cooldown_min);
                        boolean notEnoughMana = manaManager.get(uuid).getCurrentMana() < def.manaCost;

                        String display;
                        if (onCooldown) {
                            double remaining = cooldownManager.getRemaining(uuid, skillId, def.cooldown, def.cooldown_min);
                            // ■ 改善点2: クールダウン中は赤文字＋秒数は少し薄くして見やすく
                            display = "§c" + def.name + " §7(" + String.format("%.1f", remaining) + ")";
                        } else if (notEnoughMana) {
                            // ■ 改善点3: マナ不足は §9(濃い青) だと見づらいので §b(水色) や §3(濃い水色) 推奨
                            display = "§b" + def.name;
                        } else {
                            // ■ 改善点4: 使用可能時は緑 (§a) + 太字 (§l) で強調しても良い
                            display = "§a§l" + def.name;
                        }

                        // ■ 改善点5: 末尾にスペースを2つ入れて区切りを明確に
                        sb.append(keyPrefix).append(display).append("  ");
                    }

                    player.sendActionBar(sb.toString().trim());
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
            player.sendActionBar(Component.text(" "));
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
