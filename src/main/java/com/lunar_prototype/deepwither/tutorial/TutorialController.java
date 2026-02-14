package com.lunar_prototype.deepwither.tutorial;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.event.*;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.lunar_prototype.eqf.EQFPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private final Map<UUID, TutorialStage> stageMap = new HashMap<>();
    private final Set<UUID> loadingPlayers = new HashSet<>();
    private BukkitTask titleTask;

    public TutorialController(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startTitleTask();
    }

    @Override
    public void shutdown() {
        if (titleTask != null && !titleTask.isCancelled()) {
            titleTask.cancel();
        }
    }

    public enum TutorialStage {
        INIT, WAIT_OPEN_STATUS, WAIT_ALLOCATE_POINT, WAIT_OPEN_SKILLTREE,
        WAIT_UNLOCK_STARTER, WAIT_UNLOCK_SKILL_NODE, WAIT_ASSIGN_SKILL,
        WAIT_SKILL_CAST, CORE_DONE, EQF_HANDOVER, COMPLETE
    }

    private void startTitleTask() {
        this.titleTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : stageMap.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    TutorialStage stage = stageMap.get(uuid);
                    if (p == null || !p.isOnline() || stage == TutorialStage.COMPLETE) continue;

                    String title = getTitle(stage);
                    String subTitle = getSubtitle(p,stage);

                    Component subtitlecom = MiniMessage.miniMessage().deserialize(subTitle);
                    Component titlecom = MiniMessage.miniMessage().deserialize(title);

                    p.showTitle(Title.title(titlecom, subtitlecom,0,40,10));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        TutorialStage stage = stageMap.get(uuid);
        boolean isLoading = loadingPlayers.contains(uuid);
        boolean isRestrictedStage = (stage != null && stage != TutorialStage.COMPLETE && stage != TutorialStage.EQF_HANDOVER);

        if (!isLoading && !isRestrictedStage) return;

        if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY() || e.getFrom().getZ() != e.getTo().getZ()) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPlayedBefore()) {
            startTutorial(e.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {}

    private void startTutorial(Player p) {
        UUID uuid = p.getUniqueId();
        if (loadingPlayers.contains(uuid) || stageMap.containsKey(uuid)) return;
        loadingPlayers.add(uuid);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dungeon create tutorial " + p.getName());

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    loadingPlayers.remove(uuid);
                    return;
                }
                playOpeningSequence(p);
            }
        }.runTaskLater(plugin, 120L);
    }

    private void playOpeningSequence(Player p) {
        List<String> lines = List.of(
                "……目を覚ましたはずなのに、目覚めた感覚がない。",
                "空気はある、重力もある。だが、何かが決定的に違う",
                "――ここは、戻るための場所ではない"
        );
        playDialogueLine(p, lines, 0);
    }

    private void playDialogueLine(Player p, List<String> lines, int lineIndex) {
        if (!p.isOnline()) {
            loadingPlayers.remove(p.getUniqueId());
            return;
        }
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
                if (!p.isOnline()) {
                    loadingPlayers.remove(p.getUniqueId());
                    this.cancel();
                    return;
                }
                charIndex++;
                String currentText = text.substring(0, charIndex);
                p.sendActionBar(Component.text(currentText, NamedTextColor.WHITE));
                if (charIndex >= length) {
                    this.cancel();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            playDialogueLine(p, lines, lineIndex + 1);
                        }
                    }.runTaskLater(plugin, 40L);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void finishOpening(Player p) {
        if (!p.isOnline()) {
            loadingPlayers.remove(p.getUniqueId());
            return;
        }
        giveTutorialItems(p);
        stageMap.put(p.getUniqueId(), TutorialStage.WAIT_OPEN_STATUS);
        sendInstruction(p, TutorialStage.WAIT_OPEN_STATUS);
        loadingPlayers.remove(p.getUniqueId());
    }

    private void giveTutorialItems(Player p) {
        p.getInventory().clear();
        p.getInventory().addItem(Deepwither.getInstance().getItemFactory().getCustomItemStack("aether_sword"));
        p.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[]{
                Deepwither.getInstance().getItemFactory().getCustomItemStack("aether_boots"),
                Deepwither.getInstance().getItemFactory().getCustomItemStack("aether_leggings"),
                Deepwither.getInstance().getItemFactory().getCustomItemStack("aether_chestplate"),
                Deepwither.getInstance().getItemFactory().getCustomItemStack("aether_helmet")
        });
        Deepwither.getInstance().getStatManager().setActualCurrenttoMaxHelth(p);
    }

    @EventHandler
    public void onOpenAttributes(OpenAttributes e) {
        Player p = e.getPlayer();
        if (stage(p) == TutorialStage.WAIT_OPEN_STATUS) advanceStage(p, TutorialStage.WAIT_ALLOCATE_POINT);
    }

    @EventHandler
    public void onCloseInventory(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (stage(p) == TutorialStage.WAIT_ALLOCATE_POINT) advanceStage(p, TutorialStage.WAIT_OPEN_SKILLTREE);
    }

    @EventHandler
    public void onOpenSkillTree(OpenSkilltree e) {
        Player p = e.getPlayer();
        if (stage(p) == TutorialStage.WAIT_OPEN_SKILLTREE) advanceStage(p, TutorialStage.WAIT_UNLOCK_STARTER);
    }

    @EventHandler
    public void onGetTreeNode(GetTreeNode e) {
        Player p = e.getPlayer();
        String skillId = e.getSkillid().toLowerCase();
        TutorialStage currentStage = stage(p);
        if (currentStage == TutorialStage.WAIT_UNLOCK_STARTER) {
            if (skillId.contains("start")) advanceStage(p, TutorialStage.WAIT_UNLOCK_SKILL_NODE);
        } else if (currentStage == TutorialStage.WAIT_UNLOCK_SKILL_NODE) {
            if (!skillId.contains("start")) advanceStage(p, TutorialStage.WAIT_ASSIGN_SKILL);
        }
    }

    @EventHandler
    public void onOpenSkillAssign(OpenSkillassignment e) {
        Player p = e.getPlayer();
        if (stage(p) == TutorialStage.WAIT_ASSIGN_SKILL) advanceStage(p, TutorialStage.WAIT_SKILL_CAST);
    }

    @EventHandler
    public void onSkillCast(SkillCastEvent e) {
        Player p = e.getPlayer();
        if (stage(p) == TutorialStage.WAIT_SKILL_CAST) {
            advanceStage(p, TutorialStage.CORE_DONE);
            Bukkit.getScheduler().runTaskLater(plugin, () -> handOverToEQF(p), 40L);
        }
    }

    private void advanceStage(Player p, TutorialStage nextStage) {
        stageMap.put(p.getUniqueId(), nextStage);
        sendInstruction(p, nextStage);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private void handOverToEQF(Player p) {
        stageMap.put(p.getUniqueId(), TutorialStage.EQF_HANDOVER);
        p.sendMessage(Component.text("[Tutorial] ", NamedTextColor.YELLOW).append(Component.text("基本操作の確認完了。クエストを開始します...", NamedTextColor.WHITE)));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (EQFPlugin.getInstance() != null) EQFPlugin.getInstance().getQuestManager().startQuest(p, "tutorial");
            stageMap.put(p.getUniqueId(), TutorialStage.COMPLETE);
            p.showTitle(Title.title(Component.empty(), Component.empty()));
            p.sendMessage(Component.text("[Tutorial] ", NamedTextColor.GREEN).append(Component.text("移動制限が解除されました！", NamedTextColor.WHITE)));
        }, 60L);
    }

    private TutorialStage stage(Player p) {
        return stageMap.getOrDefault(p.getUniqueId(), TutorialStage.INIT);
    }

    private void sendInstruction(Player p, TutorialStage stage) {
        String chatMsg = getChatMessage(stage);
        if (chatMsg != null) {
            Component message = MiniMessage.miniMessage().deserialize(chatMsg);
            p.sendMessage(Component.text("[Tips] ", NamedTextColor.AQUA).append(message));
        }
    }

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

    private String getSubtitle(Player p, TutorialStage stage) {
        return switch (stage) {
            case WAIT_OPEN_STATUS -> "<white>コマンド <green>/att <white>を入力";
            case WAIT_ALLOCATE_POINT -> "<white>任意のステータスをクリックして強化";
            case WAIT_OPEN_SKILLTREE -> "<white>コマンド <green>/st <white>を入力";
            case WAIT_UNLOCK_STARTER -> "<white>ツリーの中心にある <gold>Starter Node <white>をクリック";
            case WAIT_UNLOCK_SKILL_NODE -> "<white>繋がった先のスキルをクリックして習得";
            case WAIT_ASSIGN_SKILL -> "<white>コマンド <green>/skills <white>を入力";
            case WAIT_SKILL_CAST -> "<white>オフハンド切り替えキー <yellow><key:key.swapOffhand><white>を押す";
            case CORE_DONE, EQF_HANDOVER -> "<white>準備中...";
            default -> "";
        };
    }

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
