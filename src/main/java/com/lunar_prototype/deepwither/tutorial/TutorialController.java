package com.lunar_prototype.deepwither.tutorial;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.*;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.lunar_prototype.eqf.EQFPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

@DependsOn({StatManager.class, ItemFactory.class})
public class TutorialController implements Listener, IManager {

    private final JavaPlugin plugin;
    // プレイヤーごとの進行状況
    private final Map<UUID, TutorialStage> stageMap = new HashMap<>();
    private final Set<UUID> loadingPlayers = new HashSet<>();
    private BukkitTask titleTask;

    public TutorialController(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // ★ タイトル常時表示タスクの開始
        startTitleTask();
    }

    @Override
    public void shutdown() {
        if (titleTask != null && !titleTask.isCancelled()) {
            titleTask.cancel();
        }
    }

    /* -----------------------------
     * Tutorial Stage Enum
     * ----------------------------- */
    public enum TutorialStage {
        INIT,                   // 開始待機
        WAIT_OPEN_STATUS,       // ステータス画面を開く待ち (/status)
        WAIT_ALLOCATE_POINT,    // ポイント割り振り待ち
        WAIT_OPEN_SKILLTREE,
        WAIT_UNLOCK_STARTER,    // 追加: スターターノード解放待ち
        WAIT_UNLOCK_SKILL_NODE, // 追加: 具体的なスキル習得待ち
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
        this.titleTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : stageMap.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    TutorialStage stage = stageMap.get(uuid);

                    // プレイヤーがオフライン、または完了済みならスキップ
                    if (p == null || !p.isOnline() || stage == TutorialStage.COMPLETE) continue;

                    // ステージに応じたタイトルを取得して表示
                    String title = getTitle(stage);
                    String subTitle = getSubtitle(p,stage);

                    Component subtitlecom = MiniMessage.miniMessage().deserialize(subTitle);
                    Component titlecom = MiniMessage.miniMessage().deserialize(title);

                    // 0.1秒フェードイン, 2秒維持, 1秒フェードアウト (ループさせるので維持時間を長めに)
                    p.showTitle(Title.title(titlecom, subtitlecom,0,40,10));
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
        UUID uuid = p.getUniqueId();
        TutorialStage stage = stageMap.get(uuid);

        // 1. ロード中（文字演出中など）かどうか
        boolean isLoading = loadingPlayers.contains(uuid);

        // 2. チュートリアル進行中（かつ移動制限すべきステージ）かどうか
        boolean isRestrictedStage = (stage != null
                && stage != TutorialStage.COMPLETE
                && stage != TutorialStage.EQF_HANDOVER);

        // 「演出中でもなく」かつ「制限ステージでもない」なら何もしない
        if (!isLoading && !isRestrictedStage) {
            return;
        }

        // X, Y, Z のいずれかが変化した場合 (視点移動は許可)
        if (e.getFrom().getX() != e.getTo().getX() ||
                e.getFrom().getY() != e.getTo().getY() ||
                e.getFrom().getZ() != e.getTo().getZ()) {

            e.setCancelled(true);
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
        UUID uuid = p.getUniqueId();

        // 1. 二重起動対策
        // すでにロード中、またはチュートリアル進行中なら弾く
        if (loadingPlayers.contains(uuid) || stageMap.containsKey(uuid)) {
            return;
        }
        loadingPlayers.add(uuid);

        // ダンジョン作成コマンド
        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "dungeon create tutorial " + p.getName()
        );

        // 2. ロード時間（4秒 = 80tick）待機してから演出開始
        new BukkitRunnable() {
            @Override
            public void run() {
                // プレイヤーがログアウトしていたら中止
                if (!p.isOnline()) {
                    loadingPlayers.remove(uuid);
                    return;
                }
                // 演出開始
                playOpeningSequence(p);
            }
        }.runTaskLater(plugin, 120L);
    }

    // 3. アクションバーでのタイプライター演出（分割表示版）
    private void playOpeningSequence(Player p) {
        // 文章をリストに分割して定義
        List<String> lines = List.of(
                "……目を覚ましたはずなのに、目覚めた感覚がない。",
                "空気はある、重力もある。だが、何かが決定的に違う",
                "――ここは、戻るための場所ではない"
        );

        // 最初の行（インデックス0）から開始
        playDialogueLine(p, lines, 0);
    }

    // 1行ずつ処理するメソッド
    private void playDialogueLine(Player p, List<String> lines, int lineIndex) {
        // プレイヤーがオフラインなら中断
        if (!p.isOnline()) {
            loadingPlayers.remove(p.getUniqueId());
            return;
        }

        // 全ての行を表示し終わったら終了処理へ
        if (lineIndex >= lines.size()) {
            finishOpening(p);
            return;
        }

        String text = lines.get(lineIndex);

        new BukkitRunnable() {
            int charIndex = 0;
            final int length = text.length();

            @Override
            public void run() {
                // 途中で落ちた場合
                if (!p.isOnline()) {
                    loadingPlayers.remove(p.getUniqueId());
                    this.cancel();
                    return;
                }

                charIndex++;
                String currentText = text.substring(0, charIndex);

                // 表示
                p.sendActionBar(MiniMessage.miniMessage().deserialize("<white>" + currentText));

                // この行の表示が終わったら
                if (charIndex >= length) {
                    this.cancel();

                    // 読み終わる余韻（待機時間）
                    // 文章量に応じて変えてもいいですが、固定で40tick(2秒)待機させて次の行へ
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // 次の行 (lineIndex + 1) を呼び出す
                            playDialogueLine(p, lines, lineIndex + 1);
                        }
                    }.runTaskLater(plugin, 40L);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // 文字送り速度
    }

    // 4. 演出終了後の処理（アイテム付与・ステージ遷移）
    private void finishOpening(Player p) {
        if (!p.isOnline()) {
            loadingPlayers.remove(p.getUniqueId());
            return;
        }

        giveTutorialItems(p); // アイテム付与

        // ステージをセット
        stageMap.put(p.getUniqueId(), TutorialStage.WAIT_OPEN_STATUS);

        // 最初の指示を出す
        sendInstruction(p, TutorialStage.WAIT_OPEN_STATUS); // これで「/attを入力」が出る

        // ロード中フラグ解除
        loadingPlayers.remove(p.getUniqueId());
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
            // スキルツリーを開いたら「スターター解放」ステージへ
            advanceStage(p, TutorialStage.WAIT_UNLOCK_STARTER);
        }
    }

    // 4. スキル習得 (ツリー画面を閉じた際に判定、またはSkillUnlockEventがあればそれを使う)
    // ここでは「ツリー画面を閉じた」タイミングで次へ
    @EventHandler
    public void onGetTreeNode(GetTreeNode e) {
        Player p = e.getPlayer();
        String skillId = e.getSkillid().toLowerCase();
        TutorialStage currentStage = stage(p);

        if (currentStage == TutorialStage.WAIT_UNLOCK_STARTER) {
            // スターターノード (IDに"start"を含む) を取得したかチェック
            if (skillId.contains("start")) {
                advanceStage(p, TutorialStage.WAIT_UNLOCK_SKILL_NODE);
            }
        } else if (currentStage == TutorialStage.WAIT_UNLOCK_SKILL_NODE) {
            // スターター以外のスキルノードを取得したかチェック
            if (!skillId.contains("start")) {
                // スキルを習得したので、次はセット画面へ
                advanceStage(p, TutorialStage.WAIT_ASSIGN_SKILL);
            }
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
        Component message = MiniMessage.miniMessage().deserialize(getChatMessage(stage));

        if (message != null) {

            //p.sendMessage("§b[Tips] §f" + message);
            Component componentmessage = MiniMessage.miniMessage().deserialize("<aqua>[Tips] <white> ");
            componentmessage.append(message);

            p.sendMessage(componentmessage);
        }
    }

    // ステージごとのタイトル (常時表示用)
    private String getTitle(TutorialStage stage) {
        return switch (stage) {
            case WAIT_OPEN_STATUS -> "<aqua>ステータス画面を開く";
            case WAIT_ALLOCATE_POINT -> "<yellow>ポイントを割り振る";
            case WAIT_OPEN_SKILLTREE -> "<aqua>スキルツリーを開く";
            case WAIT_UNLOCK_STARTER -> "<yellow>中心のノードを解放する";
            case WAIT_UNLOCK_SKILL_NODE -> "<yellow>スキルを習得する";
            case WAIT_ASSIGN_SKILL -> "<aqua>スキルセット画面を開く";
            case WAIT_SKILL_CAST -> "<red>スキルを発動する";
            case CORE_DONE, EQF_HANDOVER -> "<green>チュートリアル完了！";
            default -> "";
        };
    }

    // ステージごとのサブタイトル (コマンド指示など)
    private String getSubtitle(Player p, TutorialStage stage) {
        return switch (stage) {
            // §f -> <white>, §a -> <green> に置き換え
            case WAIT_OPEN_STATUS -> "<white>コマンド <green>/att <white>を入力";
            case WAIT_ALLOCATE_POINT -> "<white>任意のステータスをクリックして強化";
            case WAIT_OPEN_SKILLTREE -> "<white>コマンド <green>/st <white>を入力";
            case WAIT_UNLOCK_STARTER -> "<white>ツリーの中心にある <gold>Starter Node <white>をクリック";
            case WAIT_UNLOCK_SKILL_NODE -> "<white>繋がった先のスキルをクリックして習得";
            case WAIT_ASSIGN_SKILL -> "<white>コマンド <green>/skills <white>を入力";

            // ここが変更箇所です
            // §e -> <yellow>
            // Component.keybind -> <key:key.swapOffhand>
            case WAIT_SKILL_CAST -> "<white>オフハンド切り替えキー <yellow><key:key.swapOffhand><white>を押す";

            case CORE_DONE, EQF_HANDOVER -> "<white>準備中...";
            default -> "";
        };
    }

    // ステージごとのチャットメッセージ (ステージ移行時に1回だけ表示)
    private String getChatMessage(TutorialStage stage) {
        return switch (stage) {
            case WAIT_OPEN_STATUS -> "まずは自身の能力を確認しましょう。チャット欄を開き、<green>/att <white>と入力してください。";
            case WAIT_ALLOCATE_POINT -> "ステータス画面が開かれました。ポイントを使って能力を強化し、ESCキーで閉じてください。";
            case WAIT_OPEN_SKILLTREE -> "次はスキルの習得です。<green>/st<white>と入力してスキルツリーを開いてください。";
            case WAIT_UNLOCK_STARTER -> "まずはツリーの中心にある「スターターノード」を解放しましょう。";
            case WAIT_UNLOCK_SKILL_NODE -> "道が開通しました！次に、隣接するスキルノードをクリックして習得してください。";
            case WAIT_ASSIGN_SKILL -> "習得したスキルを装備します。<green>/skills <white>と入力してください。";
            case WAIT_SKILL_CAST -> "スキル装備完了です！ 武器を持ち、<yellow><key:key.swapOffhand>キー（オフハンド切り替え）<white>を押してスキルを発動してみましょう。";
            case CORE_DONE -> "お見事です！これで基本操作の確認は終了です。";
            default -> null;
        };
    }
}