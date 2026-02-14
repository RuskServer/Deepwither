package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.aethelgard.GeneratedQuest;
import com.lunar_prototype.deepwither.aethelgard.GuildQuestManager;
import com.lunar_prototype.deepwither.aethelgard.QuestLocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class QuestGUI implements InventoryHolder {

    private final QuestLocation questLocation;
    private final Inventory inventory;

    private static final String GUI_TITLE_FORMAT = "ギルドクエスト受付 - %s";
    private static final int INVENTORY_SIZE = 54;
    private static final int QUEST_SLOTS_START = 10;
    private static final int QUEST_SLOTS_END = 43;

    public QuestGUI(GuildQuestManager manager, String locationId) {
        this.questLocation = manager.getQuestLocation(locationId);

        if (this.questLocation == null) {
            this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, Component.text("エラー: ギルドが見つかりません", NamedTextColor.RED));
        } else {
            String titleStr = String.format(GUI_TITLE_FORMAT, questLocation.getLocationName());
            this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, Component.text(titleStr, NamedTextColor.DARK_AQUA));
            initializeItems();
        }
    }

    private void initializeItems() {
        if (questLocation == null) return;

        List<GeneratedQuest> quests = questLocation.getCurrentQuests();
        int questIndex = 0;

        for (int i = QUEST_SLOTS_START; i <= QUEST_SLOTS_END && questIndex < quests.size(); i++) {
            if (i % 9 == 0 || i % 9 == 8) continue;
            GeneratedQuest quest = quests.get(questIndex++);
            ItemStack item = createQuestItem(quest);
            inventory.setItem(i, item);
        }
    }

    private ItemStack createQuestItem(GeneratedQuest quest) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(quest.getTitle(), NamedTextColor.YELLOW, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("---\\u4fca-----", NamedTextColor.GRAY));

        final int MAX_LINE_WIDTH = 35;
        for (String line : wrapText(quest.getQuestText(), MAX_LINE_WIDTH)) {
            lore.add(Component.text(line, NamedTextColor.WHITE));
        }
        lore.add(Component.empty());
        lore.add(Component.text("目標: ", NamedTextColor.AQUA)
                .append(Component.text(quest.getTargetMobId() + " 討伐 x" + quest.getRequiredQuantity(), NamedTextColor.DARK_AQUA)));

        lore.add(Component.empty());

        long remainingMillis = quest.getRemainingTime();
        Component timeComp;
        if (remainingMillis <= 0) {
            timeComp = Component.text("期限切れ", NamedTextColor.RED);
        } else {
            long hours = remainingMillis / (1000 * 60 * 60);
            long minutes = (remainingMillis % (1000 * 60 * 60)) / (1000 * 60);
            NamedTextColor timeColor = (hours == 0 && minutes < 30) ? NamedTextColor.RED : NamedTextColor.GREEN;
            timeComp = Component.text(String.format("%d時間 %d分", hours, minutes), timeColor);
        }
        lore.add(Component.text("残り受付時間: ", NamedTextColor.GRAY).append(timeComp));

        lore.add(Component.empty());
        lore.add(Component.text("報酬: ", NamedTextColor.GOLD)
                .append(Component.text(quest.getRewardDetails().getLlmRewardText(), NamedTextColor.YELLOW)));
        lore.add(Component.empty());
        lore.add(Component.text(">> クリックして受注 <<", NamedTextColor.GREEN, TextDecoration.BOLD));

        lore.add(Component.text("QUEST_ID:" + quest.getQuestId().toString(), NamedTextColor.BLACK));

        meta.lore(lore);
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

    private List<String> wrapText(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        String[] sections = text.split("\\n");
        for (String section : sections) {
            int length = section.length();
            for (int i = 0; i < length; i += maxWidth) {
                int endIndex = Math.min(i + maxWidth, length);
                lines.add(section.substring(i, endIndex));
            }
        }
        return lines;
    }
}
