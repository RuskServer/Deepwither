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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemGlowHandler implements Listener {

    // 定義マップ
    private static final Map<String, Integer> MAX_MODIFIERS_BY_RARITY = Map.of(
            "コモン", 1,
            "アンコモン", 2,
            "レア", 3,
            "エピック", 4,
            "レジェンダリー", 6
    );

    // テキスト(色なし) と 色 の対応マップ
    // 例: "レジェンダリー" -> GOLD
    private static final Map<String, ChatColor> RARITY_TEXT_TO_COLOR = new HashMap<>();
    private static final String TEAM_PREFIX = "dw_glow_";
    private final Scoreboard scoreboard;

    // 初期化ブロック：定義から「純粋な名前」と「色」を抽出してマップ化
    static {
        for (String key : MAX_MODIFIERS_BY_RARITY.keySet()) {
            // 1. 色を取得（先頭の &x から）
            ChatColor color = ChatColor.WHITE; // デフォルト
            if (key.length() >= 2 && key.charAt(0) == '&') {
                ChatColor parsed = ChatColor.getByChar(key.charAt(1));
                if (parsed != null) color = parsed;
            }

            // 2. 純粋なテキスト名を取得（色コードを除去）
            // "&6&lレジェンダリー" -> "レジェンダリー"
            String cleanName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', key));

            RARITY_TEXT_TO_COLOR.put(cleanName, color);
        }
    }

    public ItemGlowHandler(JavaPlugin plugin) {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        initializeTeams(plugin);
    }

    private void initializeTeams(JavaPlugin plugin) {
        for (ChatColor color : RARITY_TEXT_TO_COLOR.values()) {
            String teamName = TEAM_PREFIX + color.name();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            try {
                team.setColor(color);
            } catch (NoSuchMethodError e) {
                // 古いバージョン用
                team.setPrefix(color.toString());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        ItemStack itemStack = itemEntity.getItemStack();

        if (!itemStack.hasItemMeta()) return;
        ItemMeta meta = itemStack.getItemMeta();
        if (!meta.hasLore()) return;

        List<String> lore = meta.getLore();
        ChatColor targetColor = null;

        // buildメソッドの仕様に合わせて「レアリティ:」の行を探す
        for (String line : lore) {
            // 色コードを除去してテキストのみにする
            // 例: "§7レアリティ:§f§6§lレジェンダリー" -> "レアリティ:レジェンダリー"
            String cleanLine = ChatColor.stripColor(line);

            // "レアリティ" という単語が含まれている行を対象にする
            if (cleanLine.contains("レアリティ")) {
                // マップにある名前（コモン、レジェンダリー等）が含まれているかチェック
                for (Map.Entry<String, ChatColor> entry : RARITY_TEXT_TO_COLOR.entrySet()) {
                    if (cleanLine.contains(entry.getKey())) {
                        targetColor = entry.getValue();
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

            // 念のため再チェック
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                team.setColor(targetColor);
            }
            team.addEntry(itemEntity.getUniqueId().toString());
        }
    }
}
