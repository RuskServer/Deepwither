package com.lunar_prototype.deepwither.dungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

@DependsOn({})
public class DungeonSignListener implements Listener, IManager {

    private final JavaPlugin plugin;

    public DungeonSignListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (PlainTextComponentSerializer.plainText().serialize(event.line(0)).equalsIgnoreCase("[Dungeon]")) {
            event.line(0, Component.text("[", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("Dungeon", NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text("]", NamedTextColor.DARK_PURPLE)));
            
            Component line1 = event.line(1);
            String dungeonName = (line1 != null) ? PlainTextComponentSerializer.plainText().serialize(line1) : "";
            
            if (dungeonName.isEmpty()) {
                event.line(1, Component.text("Dungeon Name?", NamedTextColor.RED));
            } else {
                event.line(1, Component.text(dungeonName, NamedTextColor.GOLD));
            }

            event.getPlayer().sendMessage(Component.text("ダンジョン看板を作成しました！", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) return;

        if (PlainTextComponentSerializer.plainText().serialize(sign.line(0)).contains("Dungeon")) {
            Player player = event.getPlayer();
            String dungeonId = PlainTextComponentSerializer.plainText().serialize(sign.line(1));

            File dungeonFile = new File(Deepwither.getInstance().getDataFolder(), "dungeons/" + dungeonId + ".yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(dungeonFile);

            event.setCancelled(true);
            new DungeonDifficultyGUI(dungeonId, config).open(player);
        }
    }
}
