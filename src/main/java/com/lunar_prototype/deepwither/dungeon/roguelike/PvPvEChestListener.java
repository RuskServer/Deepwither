package com.lunar_prototype.deepwither.dungeon.roguelike;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class PvPvEChestListener implements Listener {

    private final Deepwither plugin;

    public PvPvEChestListener(Deepwither plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChestInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Block block = e.getClickedBlock();
        if (block == null)
            return;

        // デバッグログ: クリックされたブロックを確認
        // plugin.getLogger().info("Block clicked: " + block.getType());

        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST
                && block.getType() != Material.BARREL)
            return;

        Player player = e.getPlayer();
        World world = player.getWorld();

        // デバッグログ: ワールド名の確認
        plugin.getLogger().info("[PvPvEChestListener] Checking world: " + world.getName());

        // ワールド名または管理クラスの判定ロジックを使用して、PvPvEダンジョン内かどうかを確認
        if (isPvPvEDungeon(world)) {
            plugin.getLogger().info("[PvPvEChestListener] Valid PvPvE dungeon. Cancelling event and opening GUI.");

            // イベントキャンセル（チェストを開かない）
            e.setCancelled(true);

            // バフGUIを開く
            if (Deepwither.getInstance().getRoguelikeBuffGUI() != null) {
                Deepwither.getInstance().getRoguelikeBuffGUI().open(player);
            } else {
                plugin.getLogger().warning("[PvPvEChestListener] RoguelikeBuffGUI is null!");
            }
        } else {
            plugin.getLogger().info("[PvPvEChestListener] Not a PvPvE dungeon.");
        }
    }

    private boolean isPvPvEDungeon(World world) {
        return world.getName().startsWith("pvpve_");
    }
}
