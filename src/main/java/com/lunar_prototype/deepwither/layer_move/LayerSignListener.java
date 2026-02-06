package com.lunar_prototype.deepwither.layer_move;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
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
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onSignCreate(SignChangeEvent event) {
        // 1行目に [warp] と書くと生成
        if ("[warp]".equalsIgnoreCase(event.getLine(0))) {
            String warpId = event.getLine(1); // 2行目にWarp IDを書く
            if (warpId == null || warpId.isEmpty()) return;

            LayerMoveManager.WarpData data = Deepwither.getInstance().getLayerMoveManager().getWarpData(warpId);

            if (data == null) {
                event.getPlayer().sendMessage(ChatColor.RED + "存在しないWarp IDです: " + warpId);
                event.setLine(0, "§cERROR");
                return;
            }

            // 看板のデザイン変更
            event.setLine(0, "§e[EoA]");
            event.setLine(1, "§f" + data.displayName); // Configの表示名を使用
            event.setLine(2, "");
            event.setLine(3, "§8ID:" + warpId); // 隠しIDとして4行目に保持（またはNBTに入れる）

            event.getPlayer().sendMessage(ChatColor.GREEN + "移動看板を作成しました。");
        }
    }

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        if (event.getClickedBlock().getType().name().contains("SIGN")) {
            Sign sign = (Sign) event.getClickedBlock().getState();

            if ("§e[EoA]".equals(sign.getLine(0))) {
                // 4行目からIDを取得
                String line3 = sign.getLine(3);
                if (line3.startsWith("§8ID:")) {
                    String warpId = line3.replace("§8ID:", "");

                    event.setCancelled(true);
                    Deepwither.getInstance().getLayerMoveManager().tryWarp(event.getPlayer(), warpId);
                }
            }
        }
    }
}