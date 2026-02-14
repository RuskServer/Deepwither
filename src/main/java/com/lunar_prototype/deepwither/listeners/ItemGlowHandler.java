package com.lunar_prototype.deepwither.listeners;

import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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

    private static final Map<String, NamedTextColor> RARITY_CONFIG = new LinkedHashMap<>();

    static {
        RARITY_CONFIG.put("レジェンダリー", NamedTextColor.GOLD);
        RARITY_CONFIG.put("アンコモン", NamedTextColor.GREEN);
        RARITY_CONFIG.put("エピック", NamedTextColor.LIGHT_PURPLE);
        RARITY_CONFIG.put("コモン", NamedTextColor.WHITE);
        RARITY_CONFIG.put("レア", NamedTextColor.AQUA);
    }

    private void setupTeams(Scoreboard sb) {
        for (Map.Entry<String, NamedTextColor> entry : RARITY_CONFIG.entrySet()) {
            NamedTextColor color = entry.getValue();
            String teamName = TEAM_PREFIX + color.toString();

            Team team = sb.getTeam(teamName);
            if (team == null) {
                team = sb.registerNewTeam(teamName);
            }
            team.color(color);
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

        String rarityTag = pdc.get(ItemFactory.RARITY_KEY, PersistentDataType.STRING);
        if (rarityTag == null) return;

        NamedTextColor targetColor = null;
        String cleanRarity = PlainTextComponentSerializer.plainText().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(rarityTag)
        );

        for (Map.Entry<String, NamedTextColor> entry : RARITY_CONFIG.entrySet()) {
            if (cleanRarity.contains(entry.getKey())) {
                targetColor = entry.getValue();
                break;
            }
        }

        if (targetColor != null) {
            itemEntity.setGlowing(true);
            String entryName = itemEntity.getUniqueId().toString();
            String teamName = TEAM_PREFIX + targetColor.toString();

            for (Player player : Bukkit.getOnlinePlayers()) {
                Scoreboard sb = player.getScoreboard();
                setupTeams(sb);

                Team team = sb.getTeam(teamName);
                if (team != null && !team.hasEntry(entryName)) {
                    team.addEntry(entryName);
                }
            }

            Scoreboard mainSb = Bukkit.getScoreboardManager().getMainScoreboard();
            setupTeams(mainSb);
            Team mainTeam = mainSb.getTeam(teamName);
            if (mainTeam != null) mainTeam.addEntry(entryName);
        }
    }
}
