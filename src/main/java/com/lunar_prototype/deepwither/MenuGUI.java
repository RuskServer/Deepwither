package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.companion.CompanionGui;
import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.profession.PlayerProfessionData;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.aethelgard.QuestProgress;
import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

    private LevelManager levelManager;
    private IStatManager statManager;
    private ProfessionManager professionManager;
    private PlayerQuestManager questManager;
    private DailyTaskManager dailyTaskManager;

    public static final Component GUI_TITLE = Component.text("Main Menu", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
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

        fillBackground(inv);

        inv.setItem(10, createProfileIcon(player));
        inv.setItem(11, createCombatStatsIcon(player));
        inv.setItem(12, createProfessionIcon(player));

        inv.setItem(15, createGuildQuestIcon(player));
        inv.setItem(16, createDailyTaskIcon(player));

        inv.setItem(22, createNavButton(Material.TOTEM_OF_UNDYING, Component.text("コンパニオン", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("共に冒険する仲間を管理します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("スキルの設定や装備の変更が可能です。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(), Component.text("▶ クリックして開く", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));

        inv.setItem(38, createNavButton(Material.ENCHANTED_BOOK, Component.text("スキルツリー", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("スキルポイントを消費して", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), 
                Component.text("新しい能力を習得します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), 
                Component.empty(), Component.text("▶ クリックして開く", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));

        inv.setItem(39, createNavButton(Material.NETHER_STAR, Component.text("能力値 (Attributes)", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("ステータスポイントを割り振り", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), 
                Component.text("基礎能力を強化します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), 
                Component.empty(), Component.text("▶ クリックして開く", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));

        inv.setItem(41, createNavButton(Material.WRITABLE_BOOK, Component.text("スキルセット", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("習得したスキルを", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), 
                Component.text("スロットに装備します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), 
                Component.empty(), Component.text("▶ クリックして開く", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));

        inv.setItem(42, createNavButton(Material.AMETHYST_SHARD, Component.text("アーティファクト", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("特殊な遺物を管理・装備します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), 
                Component.empty(), Component.text("▶ クリックして開く", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));

        inv.setItem(49, createNavButton(Material.BARRIER, Component.text("閉じる", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), 
                Component.text("メニューを閉じます。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));

        inv.setItem(50, createNavButton(Material.COMPARATOR, Component.text("システム設定", NamedTextColor.GRAY, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("チャットログ表示などを", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), 
                Component.text("カスタマイズします。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), 
                Component.empty(), Component.text("▶ クリックして設定", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));

        player.openInventory(inv);
    }

    private ItemStack createProfileIcon(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text("[ 基本情報 ]", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        PlayerLevelData levelData = levelManager.get(player);
        Economy econ = Deepwither.getEconomy();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (levelData != null) {
            double percent = (levelData.getExp() / levelData.getRequiredExp()) * 100;
            lore.add(Component.text(" Level: ", NamedTextColor.GRAY).append(Component.text(levelData.getLevel(), NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(" Exp: ", NamedTextColor.GRAY).append(Component.text(String.format("%.1f%%", percent), NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text(" Level: ", NamedTextColor.GRAY).append(Component.text("Loading...", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text(" Money: ", NamedTextColor.GRAY).append(Component.text(econ != null ? econ.format(econ.getBalance(player)) : "0", NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("プレイヤーの基本ステータスです。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCombatStatsIcon(Player player) {
        StatMap stats = statManager.getTotalStats(player);
        double curHp = statManager.getActualCurrentHealth(player);
        double maxHp = statManager.getActualMaxHealth(player);

        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("[ 戦闘ステータス ]", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(" HP: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.0f", curHp), NamedTextColor.WHITE))
                .append(Component.text(" / ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.0f", maxHp), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        
        lore.add(getStatLine("攻撃力", StatType.ATTACK_DAMAGE, stats, NamedTextColor.RED, false));
        lore.add(getStatLine("防御力", StatType.DEFENSE, stats, NamedTextColor.BLUE, false));
        lore.add(getStatLine("魔攻力", StatType.MAGIC_DAMAGE, stats, NamedTextColor.LIGHT_PURPLE, false));
        lore.add(getStatLine("魔耐性", StatType.MAGIC_RESIST, stats, NamedTextColor.DARK_AQUA, false));
        lore.add(getStatLine("クリ率", StatType.CRIT_CHANCE, stats, NamedTextColor.GOLD, true));
        lore.add(getStatLine("クリダメ", StatType.CRIT_DAMAGE, stats, NamedTextColor.GOLD, true));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProfessionIcon(Player player) {
        ItemStack item = new ItemStack(Material.GOLDEN_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("[ 職業スキル ]", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        PlayerProfessionData profData = professionManager.getData(player);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (profData != null) {
            for (ProfessionType type : ProfessionType.values()) {
                long totalExp = profData.getExp(type);
                int profLevel = professionManager.getLevel(totalExp);
                String typeName = (type == ProfessionType.MINING) ? "採掘" : (type == ProfessionType.FISHING) ? "釣り" : type.name();
                lore.add(Component.text(" " + typeName + ": ", NamedTextColor.GRAY)
                        .append(Component.text("Lv." + profLevel, NamedTextColor.GREEN))
                        .append(Component.text(" (" + totalExp + " xp)", NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text(" データ読み込み中...", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuildQuestIcon(Player player) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("[ ギルドクエスト ]", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        PlayerQuestData qData = questManager.getPlayerData(player.getUniqueId());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (qData != null && !qData.getActiveQuests().isEmpty()) {
            Map<UUID, QuestProgress> quests = qData.getActiveQuests();
            for (QuestProgress progress : quests.values()) {
                String title = progress.getQuestDetails().getTitle();
                int cur = progress.getCurrentCount();
                int req = progress.getQuestDetails().getRequiredQuantity();
                String mob = progress.getQuestDetails().getTargetMobId();

                lore.add(Component.text(" " + title, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("  討伐: ", NamedTextColor.GRAY).append(Component.text(mob, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("  進捗: ", NamedTextColor.GRAY)
                        .append(Component.text(cur, NamedTextColor.GREEN))
                        .append(Component.text(" / ", NamedTextColor.GRAY))
                        .append(Component.text(req, NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false));
                if (progress.isComplete()) {
                    lore.add(Component.text("  [報告可能]", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                }
                lore.add(Component.empty());
            }
        } else {
            lore.add(Component.text(" 現在受注しているクエストはありません。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDailyTaskIcon(Player player) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("[ デイリータスク ]", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        DailyTaskData tData = dailyTaskManager.getTaskData(player);
        Set<String> activeTraders = dailyTaskManager.getActiveTaskTraders(player);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (!activeTraders.isEmpty()) {
            for (String traderId : activeTraders) {
                int[] progress = tData.getProgress(traderId);
                String mob = tData.getTargetMob(traderId);
                String mobName = mob.equals("bandit") ? "バンディット" : mob;

                lore.add(Component.text(" 依頼者: " + traderId, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("  討伐: " + mobName, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("  進捗: ", NamedTextColor.GRAY)
                        .append(Component.text(progress[0], NamedTextColor.GREEN))
                        .append(Component.text(" / ", NamedTextColor.GRAY))
                        .append(Component.text(progress[1], NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false));
                if (progress[0] >= progress[1]) {
                    lore.add(Component.text("  [報告可能]", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                }
                lore.add(Component.empty());
            }
        } else {
            lore.add(Component.text(" 現在受注しているタスクはありません。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavButton(Material mat, Component name, Component... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        List<Component> nonItalicLore = new ArrayList<>();
        for (Component l : loreLines) {
            nonItalicLore.add(l.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(nonItalicLore);
        item.setItemMeta(meta);
        return item;
    }

    private void fillBackground(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, glass);
            }
        }
    }

    private Component getStatLine(String name, StatType type, StatMap stats, NamedTextColor color, boolean isPercent) {
        double value = stats.getFinal(type);
        String valStr = isPercent ? String.format("%.1f%%", value) : String.format("%.0f", value);
        return Component.text("  ", NamedTextColor.GRAY)
                .append(Component.text(name + ": ", NamedTextColor.GRAY))
                .append(Component.text(valStr, color))
                .decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().title().equals(GUI_TITLE)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = e.getSlot();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        switch (slot) {
            case 22:
                player.closeInventory();
                new CompanionGui(Deepwither.getInstance().getCompanionManager()).openGui(player);
                break;
            case 38:
                player.closeInventory();
                player.performCommand("skilltree");
                break;
            case 39:
                player.closeInventory();
                player.performCommand("attributes");
                break;
            case 41:
                player.closeInventory();
                Deepwither.getInstance().getSkillAssignmentGUI().open(player);
                break;
            case 42:
                player.closeInventory();
                new ArtifactGUI().openArtifactGUI(player);
                break;
            case 49:
                player.closeInventory();
                break;
            case 50:
                player.closeInventory();
                Deepwither.getInstance().getSettingsGUI().open(player);
                break;
        }
    }
}
