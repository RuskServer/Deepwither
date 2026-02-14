package com.lunar_prototype.deepwither.layer_move;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({LayerMoveManager.class})
public class LayerSignListener implements Listener, IManager {

    private final JavaPlugin plugin;

    public LayerSignListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onSignCreate(SignChangeEvent event) {
        if ("[warp]".equalsIgnoreCase(PlainTextComponentSerializer.plainText().serialize(event.line(0)))) {
            String warpId = (event.line(1) != null) ? PlainTextComponentSerializer.plainText().serialize(event.line(1)) : "";
            if (warpId.isEmpty()) return;

            LayerMoveManager.WarpData data = Deepwither.getInstance().getLayerMoveManager().getWarpData(warpId);

            if (data == null) {
                event.getPlayer().sendMessage(Component.text("存在しないWarp IDです: " + warpId, NamedTextColor.RED));
                event.line(0, Component.text("ERROR", NamedTextColor.RED));
                return;
            }

            event.line(0, Component.text("[EoA]", NamedTextColor.YELLOW));
            event.line(1, Component.text(data.displayName, NamedTextColor.WHITE));
            event.line(2, Component.empty());
            event.line(3, Component.text("ID:" + warpId, NamedTextColor.DARK_GRAY));

            event.getPlayer().sendMessage(Component.text("移動看板を作成しました。", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        if (event.getClickedBlock().getType().name().contains("SIGN")) {
            Sign sign = (Sign) event.getClickedBlock().getState();

            if (PlainTextComponentSerializer.plainText().serialize(sign.line(0)).equals("[EoA]")) {
                String line3 = PlainTextComponentSerializer.plainText().serialize(sign.line(3));
                if (line3.startsWith("ID:")) {
                    String warpId = line3.replace("ID:", "");
                    event.setCancelled(true);
                    Deepwither.getInstance().getLayerMoveManager().tryWarp(event.getPlayer(), warpId);
                }
            }
        }
    }
}
