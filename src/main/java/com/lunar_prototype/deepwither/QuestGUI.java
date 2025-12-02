package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.quest.GeneratedQuest;
import com.lunar_prototype.deepwither.quest.GuildQuestManager;
import com.lunar_prototype.deepwither.quest.QuestLocation;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ギルドクエスト一覧を表示するためのインベントリホルダー兼GUI作成クラス。
 * QuestLocationの情報を保持するため、InventoryHolderを実装します。
 */
public class QuestGUI implements InventoryHolder {

    private final QuestLocation questLocation;
    private final Inventory inventory;

    // GUIのタイトルフォーマット
    private static final String GUI_TITLE_FORMAT = "ギルドクエスト受付 - %s";

    // GUIのサイズ (9の倍数、今回は最大54スロットまで利用可能)
    private static final int INVENTORY_SIZE = 54;
    private static final int QUEST_SLOTS_START = 10;
    private static final int QUEST_SLOTS_END = 43;

    public QuestGUI(GuildQuestManager manager, String locationId) {
        this.questLocation = manager.getQuestLocation(locationId);

        if (this.questLocation == null) {
            // エラー処理（本来はコマンド側でチェックすべき）
            this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, ChatColor.RED + "エラー: ギルドが見つかりません");
        } else {
            String title = String.format(GUI_TITLE_FORMAT, questLocation.getLocationName());
            this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, ChatColor.DARK_AQUA + title);
            initializeItems();
        }
    }

    /**
     * GUIアイテムを初期化し、クエストを配置します。
     */
    private void initializeItems() {
        if (questLocation == null) return;

        List<GeneratedQuest> quests = questLocation.getCurrentQuests();
        int questIndex = 0;

        for (int i = QUEST_SLOTS_START; i <= QUEST_SLOTS_END && questIndex < quests.size(); i++) {
            // スロットがアイテムで埋まっていないことを確認
            if (i % 9 == 0 || i % 9 == 8) continue;

            GeneratedQuest quest = quests.get(questIndex++);
            // クエストアイテムを作成
            ItemStack item = createQuestItem(quest);
            inventory.setItem(i, item);
        }
    }

    /**
     * GeneratedQuestの詳細に基づいてItemStackを作成します。
     */
    private ItemStack createQuestItem(GeneratedQuest quest) {
        ItemStack item = new ItemStack(Material.PAPER); // クエストを表すアイテム
        ItemMeta meta = item.getItemMeta();

        // 1. 表示名を設定
        meta.setDisplayName(ChatColor.YELLOW.toString() + ChatColor.BOLD + quest.getTitle());

        // 2. Lore (説明文) を設定
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "--- 依頼概要 ---");
        // ★ 依頼文の全文表示ロジック
        final int MAX_LINE_WIDTH = 35; // 1行の最大文字数 (日本語も考慮して調整)

        // 依頼文を改行してロアに追加 (ChatColor.WHITEを適用)
        for (String line : wrapText(quest.getQuestText(), MAX_LINE_WIDTH)) {
            lore.add(ChatColor.WHITE + line);
        }
        lore.add("");
        lore.add(ChatColor.AQUA + "目標: " + ChatColor.DARK_AQUA + quest.getTargetMobId() + " 討伐 x" + quest.getRequiredQuantity());
        lore.add(ChatColor.AQUA + "場所: " + ChatColor.DARK_AQUA + quest.getLocationDetails().getLlmLocationText() + "[" + "X:" + quest.getLocationDetails().getX() + "Y:" + quest.getLocationDetails().getY() + "Z:" + quest.getLocationDetails().getZ() + "]");
        lore.add("");
        lore.add(ChatColor.GOLD + "報酬: " + ChatColor.YELLOW + quest.getRewardDetails().getLlmRewardText());
        lore.add("");
        lore.add(ChatColor.GREEN.toString() + ChatColor.BOLD + ">> クリックして受注 <<");

        // 3. 識別子の埋め込み (重要!)
        // クエストIDをLoreの末尾など、プレイヤーに見えない場所に埋め込みます。
        lore.add(ChatColor.BLACK + "QUEST_ID:" + quest.getQuestId().toString());

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public QuestLocation getQuestLocation() {
        return questLocation;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 長い文字列を指定した幅で改行（分割）し、リストとして返します。
     * @param text 分割する文字列
     * @param maxWidth 1行の最大文字数 (Bukkitのロア表示に適した値)
     * @return 分割された文字列のリスト
     */
    private List<String> wrapText(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();

        // 改行コード（\n）を考慮して一度分割
        String[] sections = text.split("\n");

        for (String section : sections) {
            // 各セクションを指定された maxWidth でさらに分割
            int length = section.length();
            for (int i = 0; i < length; i += maxWidth) {
                int endIndex = Math.min(i + maxWidth, length);
                lines.add(section.substring(i, endIndex));
            }
        }
        return lines;
    }
}