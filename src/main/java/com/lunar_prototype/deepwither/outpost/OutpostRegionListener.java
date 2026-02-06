package com.lunar_prototype.deepwither.outpost;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.outpost.OutpostEvent;
import com.lunar_prototype.deepwither.outpost.OutpostManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

@DependsOn({OutpostManager.class})
public class OutpostRegionListener implements Listener, IManager {

    private OutpostManager manager;
    private final JavaPlugin plugin;

    public OutpostRegionListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.manager = OutpostManager.getInstance();
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 1. アクティブなイベントがなければ処理を終了
        OutpostEvent activeEvent = manager.getActiveEvent();
        if (activeEvent == null) return;

        Player player = event.getPlayer();
        Location to = event.getTo();

        // 2. ブロック境界を越える移動がない場合はスキップし、負荷を軽減
        if (event.getFrom().getBlockX() == to.getBlockX() &&
                event.getFrom().getBlockY() == to.getBlockY() &&
                event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(to));

        String outpostRegionId = activeEvent.getOutpostRegionId();

        // 適用可能なリージョンを全てチェック
        for (ProtectedRegion region : set) {
            // 4. プレイヤーがOutpostリージョン内にいるか確認
            if (region.getId().toLowerCase().contains(outpostRegionId)) {
                // 5. プレイヤーがまだ参加者リストにいなければ追加
                if (!activeEvent.getParticipants().contains(player.getUniqueId())) {
                    activeEvent.addParticipant(player.getUniqueId());
                    player.sendMessage("§a§l[Outpost]§f あなたは「" + activeEvent.getOutpostRegionId() + "」の§6§l参加者§fとして記録されました！");
                }
            }
        }
        // NOTE: リージョンから出た場合の処理は不要です。一度参加者になれば、イベント終了までその状態を維持します。
    }
}