package com.lunar_prototype.deepwither.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Map;

public class ItemGlowHandler implements Listener {

    private final JavaPlugin plugin;
    private final Scoreboard scoreboard;
    private static final String TEAM_PREFIX = "dw_glow_";

    // 判定用マップ
    private static final Map<String, ChatColor> RARITY_CONFIG = Map.of(
            "コモン", ChatColor.WHITE,
            "アンコモン", ChatColor.GREEN,
            "レア", ChatColor.AQUA,
            "エピック", ChatColor.LIGHT_PURPLE,
            "レジェンダリー", ChatColor.GOLD
    );

    public ItemGlowHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        initializeTeams();
    }

    private void initializeTeams() {
        for (ChatColor color : RARITY_CONFIG.values()) {
            String teamName = TEAM_PREFIX + color.name();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            team.setColor(color);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        ItemStack itemStack = itemEntity.getItemStack();

        // 【デバッグ1】アイテムがスポーンしたことを確認
        plugin.getLogger().info("[GlowDebug] アイテムスポーン検知: " + itemStack.getType());

        if (!itemStack.hasItemMeta()) return;
        ItemMeta meta = itemStack.getItemMeta();
        if (!meta.hasLore()) {
            plugin.getLogger().info("[GlowDebug] Loreがありません。");
            return;
        }

        List<String> lore = meta.getLore();
        ChatColor targetColor = null;

        for (String line : lore) {
            String cleanLine = ChatColor.stripColor(line);
            // 【デバッグ2】読み取ったLoreの行を表示
            plugin.getLogger().info("[GlowDebug] Lore行確認: " + cleanLine);

            if (cleanLine.contains("レアリティ")) {
                for (Map.Entry<String, ChatColor> entry : RARITY_CONFIG.entrySet()) {
                    if (cleanLine.contains(entry.getKey())) {
                        targetColor = entry.getValue();
                        // 【デバッグ3】一致したレアリティを表示
                        plugin.getLogger().info("[GlowDebug] 一致! レアリティ: " + entry.getKey() + " -> 色: " + targetColor.name());
                        break;
                    }
                }
            }
            if (targetColor != null) break;
        }

        if (targetColor != null) {
            itemEntity.setGlowing(true);
            String teamName = TEAM_PREFIX + targetColor.name();
            Team team = scoreboard.getTeam(teamName);

            if (team != null) {
                team.addEntry(itemEntity.getUniqueId().toString());
                // 【デバッグ4】最終的なチーム参加を確認
                plugin.getLogger().info("[GlowDebug] チーム " + teamName + " に追加完了。");
            } else {
                plugin.getLogger().warning("[GlowDebug] チーム " + teamName + " が存在しません。");
            }
        } else {
            plugin.getLogger().info("[GlowDebug] レアリティ判定に失敗しました。");
        }
    }
}