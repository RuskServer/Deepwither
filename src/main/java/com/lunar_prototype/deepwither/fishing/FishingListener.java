package com.lunar_prototype.deepwither.fishing;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.LevelManager;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

@DependsOn({FishingManager.class, ProfessionManager.class, LevelManager.class})
public class FishingListener implements Listener, IManager {

    private final JavaPlugin plugin;

    public FishingListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onFishStart(PlayerFishEvent event) {
        // 浮きを投げた瞬間
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            FishHook hook = event.getHook();

            // マイクラのデフォルト設定（日光あり）での待機時間は 100~600 ticks
            // 日光がない場合、このタイマーの減算が著しく遅くなるのが仕様。

            // 対処法: 自分で待機時間を再設定して上書きする
            // Lure（入れ食い）エンチャントのレベルを取得
            int lureLevel = event.getPlayer().getInventory().getItemInMainHand().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LURE);

            // 独自の計算式（例：5秒〜20秒の間でランダム）
            // マイクラ標準の待機時間をシミュレートしつつ、日光条件を無視した値をセット
            int minWait = Math.max(20, 100 - (lureLevel * 100)); // 最短1秒
            int maxWait = Math.max(100, 600 - (lureLevel * 100)); // 最長30秒

            int customWait = ThreadLocalRandom.current().nextInt(minWait, maxWait);

            // hook.setWaitTime(ticks) で強制設定。これで空が見えなくても爆速になります。
            hook.setWaitTime(customWait);
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        // 魚を釣り上げた状態かチェック
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item)) return;

        Player player = event.getPlayer();
        Item caughtEntity = (Item) event.getCaught();

        // カスタム釣果の計算
        ItemStack customLoot = Deepwither.getInstance().getFishingManager().catchFish(player);

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
        Deepwither.getInstance().getProfessionManager().addExp(player, ProfessionType.FISHING, expToGive);
        Deepwither.getInstance().getLevelManager().addExp(player,expToGive);
    }
}