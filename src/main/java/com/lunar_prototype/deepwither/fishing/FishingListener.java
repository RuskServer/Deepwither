package com.lunar_prototype.deepwither.fishing;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.LevelManager;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
        // 釣り失敗時のコンボリセット
        if (event.getState() == PlayerFishEvent.State.FAILED_ATTEMPT || event.getState() == PlayerFishEvent.State.IN_GROUND) {
            Deepwither.getInstance().getFishingManager().resetCombo(event.getPlayer());
            return;
        }

        // 魚を釣り上げた状態かチェック
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item)) return;

        Player player = event.getPlayer();
        Item caughtEntity = (Item) event.getCaught();
        FishingManager fishingManager = Deepwither.getInstance().getFishingManager();

        // カスタム釣果の計算
        FishingManager.FishingResult result = fishingManager.catchFish(player);

        if (result != null) {
            ItemStack customLoot = result.item();
            String rarityKey = result.rarityKey();

            // ドロップアイテムを置き換え
            caughtEntity.setItemStack(customLoot);

            // コンボ更新
            int combo = fishingManager.updateCombo(player);
            double comboBonus = fishingManager.getComboBonus(combo);

            // 経験値計算
            FishingManager.RaritySettings settings = fishingManager.getRaritySettings(rarityKey);
            int baseExp = (settings != null) ? settings.baseExp : 20;

            // 基礎EXP + ランダム(0-5)
            int randomBonus = (int)(Math.random() * 6);
            int expToGive = (int)((baseExp + randomBonus) * comboBonus);

            // アクションバー表示
            if (customLoot.hasItemMeta()) {
                Component displayName = customLoot.getItemMeta().hasDisplayName() ? customLoot.getItemMeta().displayName() : Component.text(customLoot.getType().name());

                Component comboMsg = combo > 1
                    ? Component.text(combo + " COMBO! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    : Component.empty();

                Component msg = comboMsg
                    .append(displayName)
                    .append(Component.text(" を釣り上げた！ ", NamedTextColor.GRAY))
                    .append(Component.text("+" + expToGive + " EXP", NamedTextColor.GREEN));

                DW.ui(player).simpleActionBar(msg);
            }

            // プレミアム演出 (EPIC以上)
            if (rarityKey.equals("EPIC") || rarityKey.equals("LEGENDARY")) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.2f);
                player.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, caughtEntity.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
            } else {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
            }

            // 経験値の付与
            Deepwither.getInstance().getProfessionManager().addExp(player, ProfessionType.FISHING, expToGive);
            Deepwither.getInstance().getLevelManager().addExp(player, expToGive);
        }
    }
}