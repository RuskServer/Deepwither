package com.lunar_prototype.deepwither.layer_move;

import com.lunar_prototype.deepwither.Deepwither;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;

public class BossKillListener implements Listener {

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        // キラーがプレイヤーでない場合は無視
        if (!(event.getKiller() instanceof Player)) return;
        Player player = (Player) event.getKiller();

        // 倒されたMythicMobの内部IDを取得
        String killedMobId = event.getMobType().getInternalName();

        // LayerMoveManagerから全ワープ設定を取得して走査
        LayerMoveManager moveManager = Deepwither.getInstance().getLayerMoveManager();
        Collection<LayerMoveManager.WarpData> allWarps = moveManager.getAllWarpData();

        boolean isRequiredBoss = false;

        for (LayerMoveManager.WarpData warp : allWarps) {
            // ボスチェックが有効かつ、IDが一致するか確認
            if (warp.bossRequired && killedMobId.equalsIgnoreCase(warp.bossMythicId)) {
                isRequiredBoss = true;
                break; // 該当するワープ設定が見つかればループ終了
            }
        }

        // 設定ファイルに存在するボスだった場合のみ、PDCに記録
        if (isRequiredBoss) {
            NamespacedKey key = new NamespacedKey(Deepwither.getInstance(), "boss_killed_" + killedMobId.toLowerCase());

            // まだ記録（1 = 撃破済み）がない場合のみ更新
            if (!player.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "★ ボス「" + killedMobId + "」の撃破を記録しました！");
                player.sendMessage(ChatColor.YELLOW + "これで新しい階層への道が開かれました。");
                player.sendMessage("");
            }

            // 1秒(20ticks)待機してから退出させる（メッセージを読ませるため）
            // 即座に退出させて良い場合は直接 player.performCommand だけでOKです
            Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.GRAY + "§o自動的にダンジョンから退出します...");
                    player.performCommand("dungeon leave");
                }
            }, 20L);
        }
    }
}