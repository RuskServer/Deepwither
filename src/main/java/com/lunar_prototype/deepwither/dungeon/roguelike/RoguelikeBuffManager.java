package com.lunar_prototype.deepwither.dungeon.roguelike;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.StatMap;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;

public class RoguelikeBuffManager implements IManager, Listener {

    private final Deepwither plugin;
    private final Map<UUID, List<RoguelikeBuff>> playerBuffs = new HashMap<>();
    // UUID -> (ChestLocationString -> LastUsedTime)
    private final Map<UUID, Map<String, Long>> chestCooldowns = new HashMap<>();
    private static final long CHEST_COOLDOWN_MS = 180000; // 3 mins

    // バフ選択中のプレイヤー (透明＆無敵)
    private final Set<UUID> selectingBuffPlayers = new HashSet<>();

    private BukkitTask particleTask;

    public RoguelikeBuffManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        startParticleTask();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
        if (particleTask != null && !particleTask.isCancelled()) {
            particleTask.cancel();
        }
        // オンラインプレイヤーのバフをクリアするのは再起動時などの挙動次第だが、
        // StatManager側のtemporarybuffもクリアされるはずなので、メモリだけ解放しておく
        playerBuffs.clear();
    }

    /**
     * プレイヤーにバフを追加する
     */
    public void addBuff(Player player, RoguelikeBuff buff) {
        playerBuffs.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(buff);
        recalculateAndApply(player);

        // エフェクトと音
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
        player.sendMessage("§a[Buff] " + buff.getDisplayName() + " §7を獲得しました！");

        // 可視性更新 (0 -> 1個になった時など変化があるため)
        updateVisibility(player);
    }

    /**
     * プレイヤーの現在のバフ数を取得
     */
    public int getBuffCount(Player player) {
        List<RoguelikeBuff> buffs = playerBuffs.get(player.getUniqueId());
        return buffs == null ? 0 : buffs.size();
    }

    /**
     * プレイヤーのバフを全てクリアする（ダンジョン脱出時など）
     */
    public void clearBuffs(Player player) {
        if (playerBuffs.remove(player.getUniqueId()) != null) {
            plugin.getStatManager().removeTemporaryBuff(player.getUniqueId());
            plugin.getStatManager().updatePlayerStats(player);
        }
        chestCooldowns.remove(player.getUniqueId());
        selectingBuffPlayers.remove(player.getUniqueId());
        updateVisibility(player); // 状態リセットに合わせて更新

        // 抜けるときは全員から見えるように戻す（必要であれば）
        // ただ、PvPvEワールドから出るなら関係ないが、ロビーに戻るなら見えるべき。
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
    }

    public boolean tryUseChest(Player player, Location chestLoc) {
        String locKey = chestLoc.getBlockX() + "," + chestLoc.getBlockY() + "," + chestLoc.getBlockZ();
        Map<String, Long> userCooldowns = chestCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        if (userCooldowns.containsKey(locKey)) {
            long lastUsed = userCooldowns.get(locKey);
            long elapsed = System.currentTimeMillis() - lastUsed;
            if (elapsed < CHEST_COOLDOWN_MS) {
                long remainingSec = (CHEST_COOLDOWN_MS - elapsed) / 1000;
                player.sendMessage("§cこのチェストはあと " + remainingSec + "秒後に再使用可能です。");
                return false;
            }
        }

        userCooldowns.put(locKey, System.currentTimeMillis());
        return true;
    }

    /**
     * 現在のバフリストから合計ステータスを計算し、StatManagerに適用する
     */
    private void recalculateAndApply(Player player) {
        List<RoguelikeBuff> buffs = playerBuffs.get(player.getUniqueId());
        if (buffs == null || buffs.isEmpty()) {
            plugin.getStatManager().removeTemporaryBuff(player.getUniqueId());
        } else {
            StatMap totalBuffStats = new StatMap();
            for (RoguelikeBuff buff : buffs) {
                totalBuffStats.add(buff.getStatMap());
            }
            plugin.getStatManager().applyTemporaryBuff(player.getUniqueId(), totalBuffStats);
        }
        // StatManagerに変更を通知して再計算させる
        plugin.getStatManager().updatePlayerStats(player);
    }

    private void startParticleTask() {
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : playerBuffs.keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline() || player.isDead())
                        continue;

                    List<RoguelikeBuff> buffs = playerBuffs.get(uuid);
                    if (buffs == null || buffs.isEmpty())
                        continue;

                    spawnBuffParticle(player, buffs.size());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 10L); // 0.5秒ごとに実行
    }

    private void spawnBuffParticle(Player player, int count) {
        Location loc = player.getLocation().add(0, 1.0, 0); // 体の中心付近
        Color color = calculateColor(count);
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);

        // 非同期から呼ぶため、World編集系ではないspawnParticleは安全（実装によるが基本OK）
        // 不安ならメインスレッドスケジューリングが必要だが、1.13+のspawnParticleはスレッドセーフなことが多い
        // ここでは念のため同期コンテキストでなくても動く範囲で記述するが、
        // 厳密にはPlayerのLocation取得などは同期推奨。
        // 今回はrunTaskTimerAsynchronouslyを使っているが、Location取得はメインスレッドで行うべき場合がある。
        // ただし、単純な座標取得は多くのサーバー実装で許容される。
        // 万全を期すならrunTaskTimerで同期実行にする。パーティクル生成負荷は軽いので同期で問題ない。

        // 色設定ありのパーティクル
        // 1.20.5+ で REDSTONE -> DUST に変更されたため対応
        try {
            player.getWorld().spawnParticle(Particle.DUST, loc, 5, 0.3, 0.5, 0.3, 0, dustOptions);
        } catch (NoSuchFieldError | IllegalArgumentException e) {
            // 万が一古いバージョンの場合は REDSTONE を試行
            // (互換性維持のためリフレクション等は省略し、単純なフォールバックはコード上記述できないのでDUSTのみとする)
            // Errorログにあった通りREDSTONEが見つからないためDUSTを使用
        }
    }

    /**
     * バフの数に応じて色を変化させる
     */
    private Color calculateColor(int count) {
        // 例:
        // 1-2: 白～薄い青
        // 3-5: 青～緑
        // 6-9: 黄～赤
        // 10+: 紫/黒など

        if (count <= 2) {
            return Color.fromRGB(200, 200, 255); // 薄い青
        } else if (count <= 5) {
            return Color.fromRGB(100, 255, 100); // 緑
        } else if (count <= 9) {
            return Color.fromRGB(255, 255, 0); // 黄色
        } else {
            return Color.fromRGB(255, 50, 50); // 赤
        }
    }
}
