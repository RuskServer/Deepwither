package com.lunar_prototype.deepwither;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.HashMap;
import java.util.UUID;

public class DropPreventionListener implements Listener {

    // プレイヤーUUIDと、前回のQキー入力時刻 (ミリ秒) を保持するマップ
    private final HashMap<UUID, Long> lastDropTime = new HashMap<>();

    // 連続ドロップとして許容する最大時間差 (例: 500ミリ秒 = 0.5秒)
    private static final long DROP_INTERVAL_MS = 500;

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // プレイヤーがインベントリを開いている場合は、このイベントは通常発生しませんが、
        // 念のため、安全策としてインベントリが開いている場合は処理をスキップ
        // (InventoryClickEventで処理されない、通常のドロップ操作のみを対象とする)
        if (player.getOpenInventory().getTopInventory().getHolder() != null) {
            return;
        }

        // 1. プレイヤーの前回のドロップ時刻を取得
        long previousTime = lastDropTime.getOrDefault(playerId, 0L);

        // 2. 現在時刻と前回時刻の差分を計算
        long timeDifference = currentTime - previousTime;

        if (timeDifference < DROP_INTERVAL_MS) {
            // 連続してQが押された場合 (2回目と判断)
            // -> ドロップを許可 (event.setCancelled(false) はデフォルトなので不要)
            player.sendMessage("§e§l[アイテムドロップ]§r アイテムをドロップしました。");
        } else {
            // Qが単独で押された場合 (1回目と判断)
            // -> ドロップをキャンセル
            event.setCancelled(true);
            player.sendMessage("§cアイテムをドロップするには、短時間で§lQキーを2回§c押してください。");
        }

        // 3. マップの時刻を更新 (2回目と判断された場合でも、次回のドロップの基点にするため更新が必要)
        lastDropTime.put(playerId, currentTime);
    }
}