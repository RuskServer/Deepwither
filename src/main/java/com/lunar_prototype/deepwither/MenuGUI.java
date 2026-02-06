package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.companion.CompanionGui;
import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.profession.PlayerProfessionData;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.aethelgard.QuestProgress;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

@DependsOn({LevelManager.class, StatManager.class, ProfessionManager.class, PlayerQuestManager.class, DailyTaskManager.class})
public class MenuGUI implements Listener, IManager {

    private final Deepwither plugin;

    // 依存マネージャー
    private LevelManager levelManager;
    private StatManager statManager;
    private ProfessionManager professionManager;
    private PlayerQuestManager questManager;
    private DailyTaskManager dailyTaskManager;

    // GUI設定
    private static final String GUI_TITLE = "§8Main Menu";
    private static final int GUI_SIZE = 54;

    public MenuGUI(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.levelManager = plugin.getLevelManager();
        this.statManager = plugin.getStatManager();
        this.professionManager = plugin.getProfessionManager();
        this.questManager = plugin.getPlayerQuestManager();
        this.dailyTaskManager = plugin.getDailyTaskManager();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}


    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // 背景装飾 (ガラス板)
        fillBackground(inv);

        // --- 1. ステータス情報セクション ---
        inv.setItem(10, createProfileIcon(player));      // プレイヤー基本情報 (Lv, Money)
        inv.setItem(11, createCombatStatsIcon(player));  // 戦闘ステータス
        inv.setItem(12, createProfessionIcon(player));   // 職業ステータス

        // --- 2. タスク/クエストセクション ---
        inv.setItem(15, createGuildQuestIcon(player));   // ギルドクエスト
        inv.setItem(16, createDailyTaskIcon(player));    // デイリータスク

        // コンパニオン
        inv.setItem(22, createNavButton(Material.TOTEM_OF_UNDYING, "§6§lコンパニオン",
                "§7共に冒険する仲間を管理します。",
                "§7スキルの設定や装備の変更が可能です。",
                "", "§e▶ クリックして開く"));

        // --- 3. ナビゲーションボタン ---
        // Skill Tree
        inv.setItem(38, createNavButton(Material.ENCHANTED_BOOK, "§a§lスキルツリー",
                "§7スキルポイントを消費して", "§7新しい能力を習得します。", "", "§e▶ クリックして開く"));

        // Attributes
        inv.setItem(39, createNavButton(Material.NETHER_STAR, "§b§l能力値 (Attributes)",
                "§7ステータスポイントを割り振り", "§7基礎能力を強化します。", "", "§e▶ クリックして開く"));

        // Skill Assignment
        inv.setItem(41, createNavButton(Material.WRITABLE_BOOK, "§d§lスキルセット",
                "§7習得したスキルを", "§7スロットに装備します。", "", "§e▶ クリックして開く"));

        // Artifacts
        inv.setItem(42, createNavButton(Material.AMETHYST_SHARD, "§5§lアーティファクト",
                "§7特殊な遺物を管理・装備します。", "", "§e▶ クリックして開く"));

        // 閉じるボタン
        inv.setItem(49, createNavButton(Material.BARRIER, "§c閉じる", "§7メニューを閉じます。"));

        inv.setItem(50, createNavButton(Material.COMPARATOR, "§7§lシステム設定",
                "§7チャットログ表示などを", "§7カスタマイズします。", "", "§e▶ クリックして設定"));

        player.openInventory(inv);
    }

    // --- アイテム作成メソッド群 ---

    private ItemStack createProfileIcon(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName("§e§l[ 基本情報 ]");

        PlayerLevelData levelData = levelManager.get(player);
        Economy econ = Deepwither.getEconomy();

        List<String> lore = new ArrayList<>();
        lore.add("");
        if (levelData != null) {
            double percent = (levelData.getExp() / levelData.getRequiredExp()) * 100;
            lore.add(String.format(" §7Level: §a%d", levelData.getLevel()));
            lore.add(String.format(" §7Exp: §e%.1f%%", percent));
        } else {
            lore.add(" §7Level: §cLoading...");
        }
        lore.add(" §7Money: §6" + (econ != null ? econ.format(econ.getBalance(player)) : "0"));
        lore.add("");
        lore.add("§7プレイヤーの基本ステータスです。");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCombatStatsIcon(Player player) {
        StatMap stats = statManager.getTotalStatsFromEquipment(player);
        double curHp = statManager.getActualCurrentHealth(player);
        double maxHp = statManager.getActualMaxHealth(player);

        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§l[ 戦闘ステータス ]");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(" §7HP: §f" + String.format("%.0f", curHp) + " §7/ §f" + String.format("%.0f", maxHp));
        lore.add(getStatLine("攻撃力", StatType.ATTACK_DAMAGE, stats, "§c", false));
        lore.add(getStatLine("防御力", StatType.DEFENSE, stats, "§9", false));
        lore.add(getStatLine("魔攻力", StatType.MAGIC_DAMAGE, stats, "§d", false));
        lore.add(getStatLine("魔耐性", StatType.MAGIC_RESIST, stats, "§3", false));
        lore.add(getStatLine("クリ率", StatType.CRIT_CHANCE, stats, "§6", true));
        lore.add(getStatLine("クリダメ", StatType.CRIT_DAMAGE, stats, "§6", true));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProfessionIcon(Player player) {
        ItemStack item = new ItemStack(Material.GOLDEN_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a§l[ 職業スキル ]");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        PlayerProfessionData profData = professionManager.getData(player);
        List<String> lore = new ArrayList<>();
        lore.add("");

        if (profData != null) {
            for (ProfessionType type : ProfessionType.values()) {
                long totalExp = profData.getExp(type);
                int profLevel = professionManager.getLevel(totalExp);
                String typeName = (type == ProfessionType.MINING) ? "採掘" : type.name();
                lore.add(String.format(" §7%s: §aLv.%d §7(%d xp)", typeName, profLevel, totalExp));
            }
        } else {
            lore.add(" §7データ読み込み中...");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuildQuestIcon(Player player) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§l[ ギルドクエスト ]");

        PlayerQuestData qData = questManager.getPlayerData(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        lore.add("");

        if (qData != null && !qData.getActiveQuests().isEmpty()) {
            Map<UUID, QuestProgress> quests = qData.getActiveQuests();
            for (QuestProgress progress : quests.values()) {
                String title = progress.getQuestDetails().getTitle();
                int cur = progress.getCurrentCount();
                int req = progress.getQuestDetails().getRequiredQuantity();
                String mob = progress.getQuestDetails().getTargetMobId();

                lore.add(" §f" + title);
                lore.add("  §7討伐: §c" + mob);
                lore.add("  §7進捗: §a" + cur + "§7 / " + req);
                if (progress.isComplete()) {
                    lore.add("  §e§l[報告可能]");
                }
                lore.add(""); // クエスト間の区切り
            }
        } else {
            lore.add(" §7現在受注しているクエストはありません。");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDailyTaskIcon(Player player) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§l[ デイリータスク ]");

        DailyTaskData tData = dailyTaskManager.getTaskData(player);
        Set<String> activeTraders = dailyTaskManager.getActiveTaskTraders(player);
        List<String> lore = new ArrayList<>();
        lore.add("");

        if (!activeTraders.isEmpty()) {
            for (String traderId : activeTraders) {
                int[] progress = tData.getProgress(traderId);
                String mob = tData.getTargetMob(traderId);
                String mobName = mob.equals("bandit") ? "バンディット" : mob;

                lore.add(" §f依頼者: " + traderId);
                lore.add("  §7討伐: " + mobName);
                lore.add("  §7進捗: §a" + progress[0] + "§7 / " + progress[1]);
                if (progress[0] >= progress[1]) {
                    lore.add("  §e§l[報告可能]");
                }
                lore.add("");
            }
        } else {
            lore.add(" §7現在受注しているタスクはありません。");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavButton(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setLore(Arrays.asList(loreLines));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBackground(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, glass);
            }
        }
    }

    private String getStatLine(String name, StatType type, StatMap stats, String color, boolean isPercent) {
        double value = stats.getFinal(type);
        String valStr = isPercent ? String.format("%.1f%%", value) : String.format("%.0f", value);
        return String.format("  §7%s: %s%s", name, color, valStr);
    }

    // --- イベントハンドリング ---

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // ボタンの処理
        int slot = e.getSlot();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        switch (slot) {
            case 22:
                player.closeInventory();
                new CompanionGui(Deepwither.getInstance().getCompanionManager()).openGui(player);
                break;
            case 38: // Skill Tree
                // 既存のSkilltreeGUIを開くコマンドを実行させる、もしくは直接Manager経由で開く
                // SkilltreeGUIのコマンドロジックを直接呼ぶのは複雑なため、コマンドを実行させます
                player.closeInventory();
                player.performCommand("skilltree");
                break;
            case 39: // Attributes
                // AttributeCommandは Event Call -> Static Open という手順
                player.closeInventory();
                // イベントが必要な場合はコマンド実行の方が安全ですが、
                // コードを見る限り直接呼んでも動きそうです。一貫性のためにコマンド推奨。
                player.performCommand("attributes");
                break;

            case 41: // Skill Assignment
                player.closeInventory();
                // DeepwitherインスタンスからGUIを取得して開く
                Deepwither.getInstance().getSkillAssignmentGUI().open(player);
                break;

            case 42: // Artifacts
                player.closeInventory();
                new ArtifactGUI().openArtifactGUI(player);
                break;

            case 49: // Close
                player.closeInventory();
                break;
            case 50:
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                player.closeInventory();
                // DeepwitherインスタンスからSettingsGUIを開く
                Deepwither.getInstance().getSettingsGUI().open(player);
        }
    }
}