package com.lunar_prototype.deepwither.modules.minerun;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.modules.mob.util.MobRegionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

public class MineRunListener implements Listener {

    private final Deepwither plugin;
    private final MineRunManager manager;

    public MineRunListener(Deepwither plugin, MineRunManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        
        // --- MineRun突入処理 ---
        // メインワールド等にあるネガティブレイヤーゲート（END_GATEWAY等でマークされている）に入ったか
        if (to != null && to.getBlock().getType() == Material.END_GATEWAY) {
            // ポータルが「MineRunLayerPortal」かどうかの厳密なチェックはスコアボードタグや
            // ポータル管理リストがあればそれで行うが、今回はEND_GATEWAYを踏んだらという簡易判定。
            if (!manager.isInMineRun(player)) {
                // Tierの算出
                MobRegionService regionService = com.lunar_prototype.deepwither.api.DW.get(MobRegionService.class);
                // 同じポータル（ブロック座標）のインスタンスに参加する
                manager.joinRun(player, to);
            } else {
                // すでにMineRun中にEND_GATEWAY（脱出ポータル）を踏んだ場合
                manager.endRun(player, true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInMineRun(player)) return;

        // MineRun内では基本的にブロック破壊を禁止するが、鉱石だけは掘れるようにする。
        Material type = event.getBlock().getType();
        MineRunSession session = manager.getSession(player);
        
        if (type == Material.STONE || type == Material.ANDESITE || type == Material.OBSIDIAN) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
            }
            return;
        }

        // 金鉱石やダイヤ等の独自ドロップ（Tier依存）
        if (type == Material.GOLD_ORE || type == Material.DEEPSLATE_GOLD_ORE || type == Material.DIAMOND_ORE) {
            event.setDropItems(false);
            
            int tier = session.getTier();
            Material dropMaterial = Material.GOLD_NUGGET;
            
            // Tierが高いほどダイヤが出やすい等
            if (tier >= 3 && plugin.getRandom().nextDouble() < 0.3) {
                dropMaterial = Material.DIAMOND;
            } else if (tier >= 2 && plugin.getRandom().nextDouble() < 0.5) {
                dropMaterial = Material.GOLD_INGOT;
            }
            
            player.getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(dropMaterial));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (manager.isInMineRun(player)) {
            // MineRun中の死はペナルティありで継続、あるいはドロップさせない
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (manager.isInMineRun(player)) {
            MineRunSession session = manager.getSession(player);
            // ダンジョンのスタート位置（dungeonCenterは安全地帯として保存されている）にリスポーンさせる
            event.setRespawnLocation(session.getDungeonCenter());
            player.sendMessage(Component.text("[MineRun] ダンジョン内で復活しました。まだ諦めてはいけません！", NamedTextColor.YELLOW));
        }
    }
}
