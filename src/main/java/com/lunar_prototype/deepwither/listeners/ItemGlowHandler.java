package com.lunar_prototype.deepwither.listeners;

import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.LinkedHashMap;
import java.util.Map;

@DependsOn({})
public class ItemGlowHandler implements Listener, IManager {

    private final JavaPlugin plugin;
    private static final String TEAM_PREFIX = "dw_glow_";

    public ItemGlowHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    // 順序を維持（長い名前を先に判定）
    private static final Map<String, ChatColor> RARITY_CONFIG = new LinkedHashMap<>();

    static {
        RARITY_CONFIG.put("レジェンダリー", ChatColor.GOLD);
        RARITY_CONFIG.put("アンコモン", ChatColor.GREEN);
        RARITY_CONFIG.put("エピック", ChatColor.LIGHT_PURPLE);
        RARITY_CONFIG.put("コモン", ChatColor.WHITE);
        RARITY_CONFIG.put("レア", ChatColor.AQUA);
    }

    /**
     * 指定したスコアボードに対してレアリティチームをセットアップします
     */
    private void setupTeams(Scoreboard sb) {
        for (Map.Entry<String, ChatColor> entry : RARITY_CONFIG.entrySet()) {
            ChatColor color = entry.getValue();
            String teamName = TEAM_PREFIX + color.name();

            Team team = sb.getTeam(teamName);
            if (team == null) {
                team = sb.registerNewTeam(teamName);
            }
            team.setColor(color);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        ItemStack itemStack = itemEntity.getItemStack();

        if (!itemStack.hasItemMeta()) return;
        ItemMeta meta = itemStack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!meta.hasLore()) return;

        // --- PDCからレアリティを取得 ---
        // ItemFactory.RARITY_KEY ("item_rarity") を使用
        String rarityTag = pdc.get(ItemFactory.RARITY_KEY, PersistentDataType.STRING);
        if (rarityTag == null) return;

        ChatColor targetColor = null;
        // 保存されている文字列（例: "§6§lレジェンダリー"）にキーワードが含まれているか判定
        // stripColor して純粋なテキストのみで比較
        String cleanRarity = ChatColor.stripColor(rarityTag.replace("&", "§"));

        for (Map.Entry<String, ChatColor> entry : RARITY_CONFIG.entrySet()) {
            if (cleanRarity.contains(entry.getKey())) {
                targetColor = entry.getValue();
                break;
            }
        }

        if (targetColor != null) {
            itemEntity.setGlowing(true);
            String entryName = itemEntity.getUniqueId().toString();
            String teamName = TEAM_PREFIX + targetColor.name();

            // ★重要: 全プレイヤーのスコアボードに対して登録を行う
            for (Player player : Bukkit.getOnlinePlayers()) {
                Scoreboard sb = player.getScoreboard();
                setupTeams(sb); // チームがなければ作成

                Team team = sb.getTeam(teamName);
                if (team != null && !team.hasEntry(entryName)) {
                    team.addEntry(entryName);
                }
            }

            // ★重要: サーバーのメインスコアボードにも念のため登録
            Scoreboard mainSb = Bukkit.getScoreboardManager().getMainScoreboard();
            setupTeams(mainSb);
            Team mainTeam = mainSb.getTeam(teamName);
            if (mainTeam != null) mainTeam.addEntry(entryName);
        }
    }
}