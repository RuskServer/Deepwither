package com.lunar_prototype.deepwither.tutorial;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.OpenAttributes;
import com.lunar_prototype.deepwither.api.event.OpenSkillassignment;
import com.lunar_prototype.deepwither.api.event.OpenSkilltree;
import com.lunar_prototype.deepwither.api.event.SkillCastEvent;
import com.lunar_prototype.eqf.EQFPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

import static io.lumine.mythic.bukkit.commands.CommandHelper.send;

public class TutorialController implements Listener {

    private final JavaPlugin plugin;
    // プレイヤーごとの進行状況
    private final Map<UUID, TutorialStage> stageMap = new HashMap<>();

    public TutorialController(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // ★ タイトル常時表示タスクの開始
        startTitleTask();
    }

    /* -----------------------------
     * Tutorial Stage Enum
     * ----------------------------- */
    public enum TutorialStage {
        INIT,                   // 開始待機
        WAIT_OPEN_STATUS,       // ステータス画面を開く待ち (/status)
        WAIT_ALLOCATE_POINT,    // ポイント割り振り待ち
        WAIT_OPEN_SKILLTREE,    // スキルツリーを開く待ち (/skills)
        WAIT_UNLOCK_SKILL,      // スキル習得待ち
        WAIT_ASSIGN_SKILL,      // スキルセット画面を開く待ち (/setskill)
        WAIT_SKILL_CAST,        // スキル発動待ち
        CORE_DONE,              // 基本操作完了
        EQF_HANDOVER,           // クエストプラグインへ引き継ぎ中
        COMPLETE                // 完了 (自由行動可)
    }

    /* -----------------------------
     * ★ 常時タイトル表示タスク
     * ----------------------------- */
    private void startTitleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : stageMap.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    TutorialStage stage = stageMap.get(uuid);

                    // プレイヤーがオフライン、または完了済みならスキップ
                    if (p == null || !p.isOnline() || stage == TutorialStage.COMPLETE) continue;

                    // ステージに応じたタイトルを取得して表示
                    String title = getTitle(stage);
                    String subTitle = getSubtitle(stage);

                    // 0.1秒フェードイン, 2秒維持, 1秒フェードアウト (ループさせるので維持時間を長めに)
                    p.sendTitle(title, subTitle, 0, 40, 10);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1秒ごとに更新
    }

    /* -----------------------------
     * ★ 移動制限 (Movement Lock)
     * ----------------------------- */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        TutorialStage stage = stageMap.get(p.getUniqueId());

        // チュートリアル管理外、または完了済みなら制限しない
        if (stage == null || stage == TutorialStage.COMPLETE || stage == TutorialStage.EQF_HANDOVER) {
            return;
        }

        // X, Y, Z のいずれかが変化した場合 (視点移動は許可)
        if (e.getFrom().getX() != e.getTo().getX() ||
                e.getFrom().getY() != e.getTo().getY() ||
                e.getFrom().getZ() != e.getTo().getZ()) {

            e.setCancelled(true);
            // 数秒に1回警告を出すなどの調整も可
            // p.sendMessage("§c[チュートリアル] §f指示に従ってください。移動は制限されています。");
        }
    }

    /* -----------------------------
     * Event Handlers
     * ----------------------------- */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPlayedBefore()) {
            startTutorial(e.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // ログアウト時にデータを削除するか、DBに保存するかは要件次第
        // ここではメモリ管理のみのため削除しない (再ログインで継続)
    }

    private void startTutorial(Player p) {
        stageMap.put(p.getUniqueId(), TutorialStage.WAIT_OPEN_STATUS);
        // ダンジョン作成
        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "dungeon create tutorial " + p.getName()
        );

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            giveTutorialItems(p);
            send(p, "……意識が覚醒する");
            send(p, "ここは通常の世界ではない");
        }, 40L);
        sendInstruction(p, TutorialStage.WAIT_OPEN_STATUS);
    }

    /* -----------------------------
     * 装備付与
     * ----------------------------- */
    private void giveTutorialItems(Player p) {
        p.getInventory().clear();

        p.getInventory().addItem(
                Deepwither.getInstance()
                        .getItemFactory()
                        .getCustomItemStack("aether_sword")
        );

        p.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[]{
                Deepwither.getInstance().getItemFactory().getCustomItemStack("aether_boots"),
                Deepwither.getInstance().getItemFactory().getCustomItemStack("aether_leggings"),
                Deepwither.getInstance().getItemFactory().getCustomItemStack("aether_chestplate"),
                Deepwither.getInstance().getItemFactory().getCustomItemStack("aether_helmet")
        });

        Deepwither.getInstance().getStatManager().setActualCurrenttoMaxHelth(p);
    }

    /* -----------------------------
     * Stage Progress Logic
     * ----------------------------- */

    // 1. ステータス画面を開いた
    @EventHandler
    public void onOpenAttributes(OpenAttributes e) {
        Player p = e.getPlayer();
        if (stage(p) == TutorialStage.WAIT_OPEN_STATUS) {
            advanceStage(p, TutorialStage.WAIT_ALLOCATE_POINT);
        }
    }

    // 2. インベントリを閉じた (ポイント割り振りをチェック)
    @EventHandler
    public void onCloseInventory(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;

        if (stage(p) == TutorialStage.WAIT_ALLOCATE_POINT) {
            // ステータス画面を閉じたタイミングでチェック
            // 実際にポイントを振ったかどうか確認するなら StatManager を参照
            // if (StatManager.get(p).getUsedPoints() > 0) ...

            // 簡易的に「閉じた」ことで次へ進める
            advanceStage(p, TutorialStage.WAIT_OPEN_SKILLTREE);
        }
    }

    // 3. スキルツリーを開いた
    @EventHandler
    public void onOpenSkillTree(OpenSkilltree e) {
        Player p = e.getPlayer();
        if (stage(p) == TutorialStage.WAIT_OPEN_SKILLTREE) {
            advanceStage(p, TutorialStage.WAIT_UNLOCK_SKILL);
        }
    }

    // 4. スキル習得 (ツリー画面を閉じた際に判定、またはSkillUnlockEventがあればそれを使う)
    // ここでは「ツリー画面を閉じた」タイミングで次へ
    @EventHandler
    public void onCloseSkillTree(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;

        // GUIのタイトル等で判定する必要があるかも
        if (stage(p) == TutorialStage.WAIT_UNLOCK_SKILL) {
            advanceStage(p, TutorialStage.WAIT_ASSIGN_SKILL);
        }
    }

    // 5. スキルセット画面を開いた
    @EventHandler
    public void onOpenSkillAssign(OpenSkillassignment e) {
        Player p = e.getPlayer();
        if (stage(p) == TutorialStage.WAIT_ASSIGN_SKILL) {
            advanceStage(p, TutorialStage.WAIT_SKILL_CAST);
        }
    }

    // 6. スキルを発動した
    @EventHandler
    public void onSkillCast(SkillCastEvent e) {
        Player p = e.getPlayer();
        if (stage(p) == TutorialStage.WAIT_SKILL_CAST) {
            advanceStage(p, TutorialStage.CORE_DONE);

            // 少し待ってからクエストへ移行
            Bukkit.getScheduler().runTaskLater(plugin, () -> handOverToEQF(p), 40L);
        }
    }

    /* -----------------------------
     * Helper Methods
     * ----------------------------- */

    private void advanceStage(Player p, TutorialStage nextStage) {
        stageMap.put(p.getUniqueId(), nextStage);
        sendInstruction(p, nextStage);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private void handOverToEQF(Player p) {
        stageMap.put(p.getUniqueId(), TutorialStage.EQF_HANDOVER);

        p.sendMessage("§e[Tutorial] §f基本操作の確認完了。クエストを開始します...");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // EQF クエスト開始
            if (EQFPlugin.getInstance() != null) {
                EQFPlugin.getInstance().getQuestManager().startQuest(p, "tutorial");
            }

            // 完了状態にして移動制限解除
            stageMap.put(p.getUniqueId(), TutorialStage.COMPLETE);
            p.sendTitle("", "", 0, 1, 0); // タイトル消去
            p.sendMessage("§a[Tutorial] §f移動制限が解除されました！");

        }, 60L); // 3秒後に開始
    }

    private TutorialStage stage(Player p) {
        return stageMap.getOrDefault(p.getUniqueId(), TutorialStage.INIT);
    }

    /* -----------------------------
     * Messages & Titles
     * ----------------------------- */

    private void sendInstruction(Player p, TutorialStage stage) {
        // チャット欄へのメッセージ送信
        String message = getChatMessage(stage);
        if (message != null) {
            p.sendMessage("§b[Tips] §f" + message);
        }
    }

    // ステージごとのタイトル (常時表示用)
    private String getTitle(TutorialStage stage) {
        return switch (stage) {
            case WAIT_OPEN_STATUS -> "§bステータス画面を開く";
            case WAIT_ALLOCATE_POINT -> "§eポイントを割り振る";
            case WAIT_OPEN_SKILLTREE -> "§bスキルツリーを開く";
            case WAIT_UNLOCK_SKILL -> "§eスキルを習得する";
            case WAIT_ASSIGN_SKILL -> "§bスキルセット画面を開く";
            case WAIT_SKILL_CAST -> "§cスキルを発動する";
            case CORE_DONE, EQF_HANDOVER -> "§aチュートリアル完了！";
            default -> "";
        };
    }

    // ステージごとのサブタイトル (コマンド指示など)
    private String getSubtitle(TutorialStage stage) {
        return switch (stage) {
            case WAIT_OPEN_STATUS -> "§fコマンド §a/att §fを入力";
            case WAIT_ALLOCATE_POINT -> "§f任意のステータスをクリックして強化";
            case WAIT_OPEN_SKILLTREE -> "§fコマンド §a/st §fを入力";
            case WAIT_UNLOCK_SKILL -> "§f習得可能なスキルをクリック";
            case WAIT_ASSIGN_SKILL -> "§fコマンド §a/skills §fを入力";
            case WAIT_SKILL_CAST -> "§fオフハンド切り替えキー §e(Fキー) §fを押す";
            case CORE_DONE, EQF_HANDOVER -> "§f準備中...";
            default -> "";
        };
    }

    // ステージごとのチャットメッセージ (ステージ移行時に1回だけ表示)
    private String getChatMessage(TutorialStage stage) {
        return switch (stage) {
            case WAIT_OPEN_STATUS -> "まずは自身の能力を確認しましょう。チャット欄を開き、§a/att §fと入力してください。";
            case WAIT_ALLOCATE_POINT -> "ステータス画面が開かれました。ポイントを使って能力を強化し、ESCキーで閉じてください。";
            case WAIT_OPEN_SKILLTREE -> "次はスキルの習得です。§a/st §fと入力してスキルツリーを開いてください。";
            case WAIT_UNLOCK_SKILL -> "スキルツリーから任意のスキルを習得（クリック）してください。";
            case WAIT_ASSIGN_SKILL -> "習得したスキルを装備します。§a/skills §fと入力してください。";
            case WAIT_SKILL_CAST -> "スキル装備完了です！ 武器を持ち、§eFキー（オフハンド切り替え）§fを押してスキルを発動してみましょう。";
            case CORE_DONE -> "お見事です！これで基本操作の確認は終了です。";
            default -> null;
        };
    }
}