package com.lunar_prototype.deepwither.layer_move;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

@DependsOn({LayerMoveManager.class})
public class BossKillListener implements Listener, IManager {

    private final JavaPlugin plugin;

    public BossKillListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!(event.getKiller() instanceof Player)) return;
        Player player = (Player) event.getKiller();

        String killedMobId = event.getMobType().getInternalName();

        LayerMoveManager moveManager = Deepwither.getInstance().getLayerMoveManager();
        Collection<LayerMoveManager.WarpData> allWarps = moveManager.getAllWarpData();

        boolean isRequiredBoss = false;

        for (LayerMoveManager.WarpData warp : allWarps) {
            if (warp.bossRequired && killedMobId.equalsIgnoreCase(warp.bossMythicId)) {
                isRequiredBoss = true;
                break;
            }
        }

        if (isRequiredBoss) {
            NamespacedKey key = new NamespacedKey(Deepwither.getInstance(), "boss_killed_" + killedMobId.toLowerCase());

            if (!player.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("★ ボス「" + killedMobId + "」の撃破を記録しました！", NamedTextColor.GOLD));
                player.sendMessage(Component.text("これで新しい階層への道が開かれました。", NamedTextColor.YELLOW));
                player.sendMessage(Component.empty());
            }

            Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
                if (player.isOnline()) {
                    player.sendMessage(Component.text("自動的にダンジョンから退出します...", NamedTextColor.GRAY, TextDecoration.ITALIC));
                    player.performCommand("dungeon leave");
                }
            }, 20L);
        }
    }
}
