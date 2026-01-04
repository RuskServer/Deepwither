package com.lunar_prototype.deepwither.fishing;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import org.bukkit.ChatColor;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public class FishingListener implements Listener {

    private final Deepwither plugin;

    public FishingListener(Deepwither plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        // 魚を釣り上げた状態かチェック
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item)) return;

        Player player = event.getPlayer();
        Item caughtEntity = (Item) event.getCaught();

        // カスタム釣果の計算
        ItemStack customLoot = plugin.getFishingManager().catchFish(player);

        if (customLoot != null) {
            // ドロップアイテムを置き換え
            caughtEntity.setItemStack(customLoot);

            // オプション: 釣ったアイテムの名前を表示する
            if (customLoot.hasItemMeta() && customLoot.getItemMeta().hasDisplayName()) {
                String msg = ChatColor.GRAY + "釣り上げた! -> " + customLoot.getItemMeta().getDisplayName();
                player.sendActionBar(msg);
            }
        }

        // 経験値の付与 (ProfessionManager)
        // 基礎EXP + ランダム性などはお好みで調整
        int expToGive = 15 + (int)(Math.random() * 10);
        plugin.getProfessionManager().addExp(player, ProfessionType.FISHING, expToGive);
    }
}